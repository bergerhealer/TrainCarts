package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.cache.RailPieceCache;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

public class PathProvider extends Task {
    private static final String SWITCHER_NAME_FALLBACK = "::traincarts::switchable::";
    private static final int STEP_COUNT = 100; // Steps performed per timing check
    private static final int MAX_PROCESSING_PER_TICK = 30; // Maximum processing time in Ms per tick
    public static boolean DEBUG_MODE = false;
    private final Map<String, PathWorld> worlds = new HashMap<String, PathWorld>();
    private Queue<BlockLocation> pendingDiscovery = new LinkedList<BlockLocation>();
    private Set<PathNode> pendingNodes = new LinkedHashSet<>();
    private Queue<PathFindOperation> pendingOperations = new LinkedList<>();
    private boolean hasChanges = false;

    public PathProvider(JavaPlugin plugin) {
        super(plugin);
    }

    public void enable(String filename) {
        this.start(1, 1);

        new CompressedDataReader(filename) {
            public void read(DataInputStream stream) throws IOException {
                // clear all previous data by clearing the worlds mapping
                worlds.clear();

                // Initializing the nodes
                int count = stream.readInt();
                PathNode[] parr = new PathNode[count];
                for (int i = 0; i < count; i++) {
                    String name = stream.readUTF();
                    BlockLocation loc = new BlockLocation(stream.readUTF(), stream.readInt(), stream.readInt(), stream.readInt());
                    parr[i] = getWorld(loc.world).addNode(loc);
                    if (name.isEmpty()) {
                        // No name, is a switcher
                        parr[i].addSwitcher();
                    } else {
                        // Go by all newline-separated names
                        for (String name_part : name.split("\n")) {
                            if (name_part.equals(SWITCHER_NAME_FALLBACK)) {
                                parr[i].addSwitcher();
                            } else {
                                parr[i].addName(name_part);
                            }
                        }
                    }
                }
                // Generating connections
                for (PathNode node : parr) {
                    int ncount = stream.readInt();
                    for (int i = 0; i < ncount; i++) {
                        node.addNeighbourFast(new PathConnection(parr[stream.readInt()], stream));
                    }
                }
            }
        }.read();

        hasChanges = false;
    }

    public void disable() {
        this.stop();

        for (PathWorld world : this.getWorlds()) {
            world.clearAll();
        }
    }

    public void save(boolean autosave, String filename) {
        if (autosave && !hasChanges) {
            return;
        }
        new CompressedDataWriter(filename) {
            public void write(DataOutputStream stream) throws IOException {
                // Compute and write total amount of nodes
                int totalNodeCount = 0;
                for (PathWorld world : getWorlds()) {
                    totalNodeCount += world.getNodes().size();
                }
                stream.writeInt(totalNodeCount);

                // Generate indices
                int i = 0;
                for (PathWorld world : getWorlds()) {
                    for (PathNode node : world.getNodes()) {
                        node.index = i;
                        if (node.containsSwitcher()) {
                            if (node.getNames().isEmpty()) {
                                // Only switcher sign, write an empty String
                                stream.writeUTF("");
                            } else {
                                // Switcher and destination sign are both at the same block
                                // To indicate that, write the switcher name fallback in addition to the names
                                stream.writeUTF(SWITCHER_NAME_FALLBACK + "\n" + StringUtil.join("\n", node.getNames()));
                            }
                        } else {
                            // Only destination sign(s), write names
                            stream.writeUTF(StringUtil.join("\n", node.getNames()));
                        }
                        stream.writeUTF(node.location.world);
                        stream.writeInt(node.location.x);
                        stream.writeInt(node.location.y);
                        stream.writeInt(node.location.z);
                        i++;
                    }
                }

                // Write out connections
                for (PathWorld world : getWorlds()) {
                    for (PathNode node : world.getNodes()) {
                        stream.writeInt(node.getNeighbours().size());
                        for (PathConnection conn : node.getNeighbours()) {
                            conn.writeTo(stream);
                        }
                    }
                }
            }
        }.write();
        hasChanges = false;
    }
    
    /**
     * Gets a collection of worlds on which path data is stored
     * 
     * @return worlds
     */
    public Collection<PathWorld> getWorlds() {
        return this.worlds.values();
    }

    /**
     * Gets the path node information stored for a world
     * 
     * @param worldName
     * @return PathWorld instance for the world with worldName
     */
    public PathWorld getWorld(String worldName) {
        return this.worlds.computeIfAbsent(worldName, name -> new PathWorld(this, name));
    }

    /**
     * Gets the path node information stored for a world
     * 
     * @param world
     * @return PathWorld instance for the world
     */
    public PathWorld getWorld(World world) {
        return getWorld(world.getName());
    }

    /**
     * Clears all node information on all worlds
     */
    public void clearAll() {
        for (PathWorld world : getWorlds()) {
            world.clearAll();
        }
    }

    /**
     * Starts rerouting all nodes on all worlds
     */
    public void reroute() {
        for (PathWorld world : getWorlds()) {
            world.rerouteAll();
        }
    }

    /**
     * Tells this provider that node information has changed and needs to be saved again to file
     */
    protected void markChanged() {
        this.hasChanges = true;
    }

    /**
     * Tells this Path Provider to start calculating all neighboring paths from the node specified<br>
     * <b>Deprecated: use {@link #scheduleNode(PathNode)} instead on PathProvider instance in TrainCarts plugin</b>
     *
     * @param startNode to schedule
     */
    @Deprecated
    public static void schedule(PathNode startNode) {
        TrainCarts.plugin.getPathProvider().scheduleNode(startNode);
    }

    /**
     * Schedules a node to start calculating all neighboring paths
     * 
     * @param startNode
     */
    public void scheduleNode(PathNode startNode) {
        pendingNodes.add(startNode);
    }

    /**
     * Tells this Path Provider to schedule new destination and switcher sign discovery, starting at a particular
     * rails block. This rail location must have signs that switch or declare a destination, otherwise
     * nothing will happen.<br>
     * <b>Deprecated: use {@link #discoverFromRail(BlockLocation)} instead</b>
     * 
     * @param railLocation to discover destinations and switchers at
     */
    @Deprecated
    public static void discover(BlockLocation railLocation) {
        TrainCarts.plugin.getPathProvider().discoverFromRail(railLocation);
    }

    /**
     * Tells this Path Provider to schedule new destination and switcher sign discovery, starting at a particular
     * rails block. This rail location must have signs that switch or declare a destination, otherwise
     * nothing will happen.
     * 
     * @param railLocation to discover destinations and switchers at
     */
    public void discoverFromRail(BlockLocation railLocation) {
        pendingDiscovery.add(railLocation);
    }

    /**
     * Checks whether this Path Provider is currently busy processing path finding
     *
     * @return True if processing is being performed, False if not
     */
    public boolean isProcessing() {
        return !pendingDiscovery.isEmpty() || !pendingOperations.isEmpty() || !pendingNodes.isEmpty();
    }

    @Override
    public Task stop() {
        addPendingNodes();
        if (!this.pendingOperations.isEmpty()) {
            TrainCarts.plugin.log(Level.INFO, "Performing " + this.pendingOperations.size() + " pending path finding operations (can take a while)...");
            while (!this.pendingOperations.isEmpty()) {
                PathFindOperation operation = this.pendingOperations.poll();
                while (operation.next()) ;
            }
        }
        return super.stop();
    }

    @Override
    public void run() {
        if (this.pendingOperations.isEmpty() && !this.pendingDiscovery.isEmpty()) {
            addNewlyDiscovered();
        }
        if (this.pendingOperations.isEmpty()) {
            addPendingNodes();
        }
        if (this.pendingOperations.isEmpty()) {
            return;
        }
        int i;
        boolean done;
        final long startTime = System.currentTimeMillis();
        while (!this.pendingOperations.isEmpty()) {
            PathFindOperation operation = this.pendingOperations.peek();
            done = false;
            if (DEBUG_MODE) {
                System.out.println("DISCOVERING EVERYTHING FROM " + operation.startNode.getDisplayName() +
                        " INTO " + operation.getJunctionName());
            }
            // Perform the operations in steps
            // Not per step, because System.currentTimeMillis is not entirely cheap!
            do {
                for (i = 0; i < STEP_COUNT && !done; i++) {
                    done = operation.next();
                }
            } while (!done && (System.currentTimeMillis() - startTime) <= MAX_PROCESSING_PER_TICK);
            if (done) {
                this.pendingOperations.poll();
            } else {
                break; // Ran out of time
            }
        }

        // Important: wipe any rail and sign caches we have polluted with temporary block data
        // This will momentarily cause the plugin to run slower, but we must do this or risk out of memory!
        RailSignCache.reset();
        RailPieceCache.reset();
    }

    // Discovers new switchers and destination signs. Stops upon the first new node found.
    private void addNewlyDiscovered() {
        BlockLocation railLocation;
        while ((railLocation = this.pendingDiscovery.poll()) != null) {
            // Check this rail location was not already visited by path finding before
            if (PathNode.get(railLocation) != null) {
                continue;
            }

            // Discover rail block
            Block railBlock = railLocation.getBlock();
            if (railBlock == null) {
                continue;
            }

            // Discover rails
            RailType railType = RailType.getType(railBlock);
            if (railType == RailType.NONE) {
                continue;
            }

            // Discover signs, and process each
            for (RailSignCache.TrackedSign trackedSign : RailSignCache.getSigns(railType, railBlock)) {
                SignActionEvent event = new SignActionEvent(trackedSign);
                SignAction action = SignAction.getSignAction(event);
                if (action == null) {
                    continue;
                }

                initNode(railBlock, event, action);
            }
        }
    }

    private void addPendingNodes() {
        if (!this.pendingNodes.isEmpty()) {
            for (PathNode node : this.pendingNodes) {
                Block startRail = node.location.getBlock();
                RailType startType = RailType.getType(startRail);
                if (startType == RailType.NONE) {
                    // Track type can not be identified
                    continue;
                }
                if (node.containsSwitcher()) {
                    if (DEBUG_MODE) {
                        System.out.println("NODE " + node.getDisplayName() + " CONTAINS A SWITCHER, BRANCHING OFF");
                    }

                    // Check north-east-south-west for possible routes
                    // Skip PAST the switcher sign rails, to avoid problems
                    for (RailJunction junc : startType.getJunctions(startRail)) {
                        RailState state = startType.takeJunction(startRail, junc);
                        if (state != null) {
                            scheduleNode(node, state, junc);
                        }
                    }
                } else {
                    // Only check available routes
                    RailState state1 = new RailState();
                    state1.setRailPiece(RailPiece.create(startType, startRail));
                    state1.position().setLocation(startType.getSpawnLocation(startRail, BlockFace.NORTH));
                    if (!RailType.loadRailInformation(state1)) {
                        continue;
                    }

                    // Snap onto rails
                    state1.loadRailLogic().getPath().snap(state1.position(), state1.railBlock());

                    // Find junctions
                    Block railBlock = state1.railBlock();
                    List<RailJunction> junctions = state1.railPiece().getJunctions();
                    if (!junctions.isEmpty()) {
                        // Create opposite direction state
                        RailState state2 = state1.clone();
                        state2.position().invertMotion();
                        state2.initEnterDirection();

                        // Walk both states to the end of the path
                        state1.loadRailLogic().getPath().move(state1, Double.MAX_VALUE);
                        state2.loadRailLogic().getPath().move(state2, Double.MAX_VALUE);

                        // Schedule the junctions of the rails matching these positions
                        scheduleNode(node, state1, findBestJunction(junctions, railBlock, state1.position()));
                        scheduleNode(node, state2, findBestJunction(junctions, railBlock, state2.position()));
                    }
                }
            }
            this.pendingNodes.clear();
        }
    }

    private static RailJunction findBestJunction(List<RailJunction> junctions, Block railBlock, RailPath.Position position) {
        if (junctions.isEmpty()) {
            throw new IllegalArgumentException("Junctions list is empty");
        }
        RailJunction best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (RailJunction junction : junctions) {
            double dist_sq = junction.position().distanceSquaredAtRail(railBlock, position);
            if (dist_sq < bestDistanceSq) {
                bestDistanceSq = dist_sq;
                best = junction;
            }
        }
        return best;
    }

    private void scheduleNode(PathNode node, RailState state, RailJunction junction) {
        pendingOperations.offer(new PathFindOperation(this, node, state, junction));
    }

    private static class PathFindOperation {
        private final PathProvider provider;
        private final TrackWalkingPoint p;
        private final PathNode startNode;
        private final String junctionName;

        public PathFindOperation(PathProvider provider, PathNode startNode, RailState state, RailJunction junction) {
            this.provider = provider;
            this.p = new TrackWalkingPoint(state);
            this.p.setLoopFilter(true);
            this.junctionName = junction.name();
            this.startNode = startNode;

            // Include distance from spawn position of rails, to the junction start
            Location spawnPos = state.railType().getSpawnLocation(state.railBlock(), state.position().getMotionFace());
            this.p.movedTotal += state.positionLocation().distance(spawnPos);
        }

        public String getJunctionName() {
            return this.junctionName;
        }

        /**
         * Performs the next finding run
         *
         * @return True if this task is finished, False if not
         */
        public boolean next() {
            if (!this.p.moveFull()) {
                return true;
            }
            Block nextRail = p.state.railBlock();
            boolean hasFinished = false;
            PathNode foundNode = null;
            for (RailSignCache.TrackedSign trackedSign : p.state.railSigns()) {
                // Discover a SignAction at this sign
                SignActionEvent event = new SignActionEvent(trackedSign);
                event.setAction(SignActionType.GROUP_ENTER);
                SignAction action = SignAction.getSignAction(event);
                if (action == null) {
                    continue;
                }

                // Check for blocker signs
                if (action.isPathFindingBlocked(event, p.state)) {
                    // If blocked, abort
                    hasFinished = true;
                    break;
                }

                // Update the node we found with the information of the current sign
                PathNode newFoundNode = provider.initNode(nextRail, event, action);
                if (newFoundNode == null) {
                    continue;
                }

                // Take first
                // Ignore signs at the start position (same node)
                // We do refresh the switchers and destination names at the start node
                if (foundNode == null && !this.startNode.location.equals(newFoundNode.location)) {
                    foundNode = newFoundNode;

                    // At a switcher/destination sign, stop now, continue looking from points we add
                    hasFinished = true;
                }
            }
            if (foundNode != null) {
                // Calculate distance from the start node to this new node
                // Include distance between spawn position on rail, and the current position with the walker
                double totalDistance = p.movedTotal;
                {
                    Location spawnPos = p.state.railType().getSpawnLocation(p.state.railBlock(), p.state.position().getMotionFace());
                    totalDistance += spawnPos.distanceSquared(p.state.positionLocation());
                }

                // Add neighbour
                this.startNode.addNeighbour(foundNode, totalDistance, this.getJunctionName());
                if (DEBUG_MODE) {
                    System.out.println("MADE CONNECTION FROM " + startNode.getDisplayName() + " TO " + foundNode.getDisplayName());
                }
            }
            return hasFinished;
        }
    }

    /**
     * Finds out rail information at a particular rail position.
     * If new nodes are discovered, they are scheduled.
     * 
     * @param state
     * @return info
     */
    public PathRailInfo getRailInfo(RailState state) {
        PathRailInfo result = PathRailInfo.NONE;
        for (RailSignCache.TrackedSign trackedSign : state.railSigns()) {
            // Discover a SignAction at this sign
            SignActionEvent event = new SignActionEvent(trackedSign);
            event.setAction(SignActionType.GROUP_ENTER);
            SignAction action = SignAction.getSignAction(event);
            if (action == null) {
                continue;
            }

            // Check for blocker signs
            if (action.isPathFindingBlocked(event, state)) {
                return PathRailInfo.BLOCKED;
            }

            // Update the node we found with the information of the current sign
            if (initNode(state.railBlock(), event, action) != null) {
                result = PathRailInfo.NODE;
            }
        }
        return result;
    }

    private PathNode initNode(Block railBlock, SignActionEvent event, SignAction action) {
        // Check for switchers and potential (new) destinations
        boolean switchable = action.isRailSwitcher(event);
        String destinationName = action.getRailDestinationName(event);
        if (!switchable && destinationName == null) {
            return null; // no pathfinding relevant signs
        }

        // Update the node we found with the information of the current sign
        PathNode node = getWorld(railBlock.getWorld()).getOrCreateAtRail(new BlockLocation(railBlock));
        if (switchable) {
            node.addSwitcher();
        }
        if (destinationName != null) {
            node.addName(destinationName);
        }

        return node;
    }
}
