package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

public class PathProvider extends Task {
    private static final String SWITCHER_NAME_FALLBACK = "::traincarts::switchable::";
    public static final int DEFAULT_MAX_PROCESSING_PER_TICK = 30; // Maximum processing time in Ms per tick
    public static boolean DEBUG_MODE = false;
    private final Map<String, PathWorld> worlds = new HashMap<String, PathWorld>();
    private final List<PathRoutingHandler> handlers = new ArrayList<PathRoutingHandler>();
    /**
     * Block locations where discovery needs to be done to see if there is a pathfinding
     * node there. If there is, then a node is created and re-routing from that node
     * is performed.
     */
    private Queue<BlockLocation> pendingDiscovery = new LinkedList<BlockLocation>();
    /**
     * Nodes in the pathfinding graph that need to perform re-discovery. This means checking
     * all possible directions from which the node can be traveled and seeing what is on the
     * other end. Regularly these nodes are turned into PathFindOperation instances to perform
     * processing.
     */
    private Set<PathNode> pendingNodes = new LinkedHashSet<>();
    /**
     * Pending operations from a path node into a given direction, where the route to other
     * nodes is computed. Each pending node can turn into one or more pending operations.
     */
    private Queue<PathFindOperation> pendingOperations = new LinkedList<>();
    /**
     * All nodes that had re-routing scheduled since the re-routing algorithm fell idle.
     * This is used to decide whether a node needs further calculations done when a route
     * to another node is added to it or changed. It avoids re-calculating the same routes
     * over and over again.<br>
     * <br>
     * Once no more operations are being done, then this set is cleared.
     */
    private Set<PathNode> scheduledNodesSinceIdle = new HashSet<>();
    /**
     * People to notify when the routes have finished calculating
     */
    private Set<CommandSender> sendersToNotifyOfCompletion = new HashSet<>();
    private boolean hasChanges = false;
    private int maxProcessingPerTick = DEFAULT_MAX_PROCESSING_PER_TICK;

    public PathProvider(JavaPlugin plugin) {
        super(plugin);

        // Default TrainCarts routing handler - for signs
        registerRoutingHandler(new PathRoutingHandler() {
            @Override
            public void process(PathRouteEvent event) {
                for (RailLookup.TrackedSign trackedSign : event.railPiece().signs()) {
                    // Check there is a SignAction at this sign
                    SignAction action = trackedSign.action;
                    if (trackedSign.isRemoved() || action == null) {
                        continue;
                    }

                    // Check for blocker signs
                    SignActionEvent signEvent = trackedSign.createEvent(SignActionType.GROUP_ENTER);
                    if (action.isPathFindingBlocked(signEvent, event.railState())) {
                        // If blocked, abort
                        event.setBlocked();
                        return;
                    }

                    // Check for switchers and potential (new) destinations
                    boolean switchable = action.isRailSwitcher(signEvent);
                    String destinationName = action.getRailDestinationName(signEvent);
                    if (!switchable && destinationName == null) {
                        continue; // no pathfinding relevant signs
                    }

                    // Update the node we found with the information of the current sign
                    PathNode newFoundNode = event.createNode();
                    if (switchable) {
                        newFoundNode.addSwitcher();
                    }
                    if (destinationName != null) {
                        newFoundNode.addName(destinationName);
                    }
                }
            }

            @Override
            public void predict(PathPredictEvent event) {
                for (RailLookup.TrackedSign trackedSign : event.railPiece().signs()) {
                    // Check there is a SignAction at this sign
                    SignAction action = trackedSign.action;
                    if (trackedSign.isRemoved() || action == null || !action.hasPathFindingPrediction()) {
                        continue;
                    }

                    // Execute logic to probe for speed limits / switchers / blockers
                    SignActionEvent signEvent = trackedSign.createEvent(SignActionType.GROUP_ENTER);
                    signEvent.setMember(event.member());
                    signEvent.overrideCartEnterDirection(event.railState().enterDirection(),
                                                         event.railState().enterFace());
                    action.predictPathFinding(signEvent, event);
                }
            }
        });
    }

    /**
     * Registers a new routing handler
     *
     * @param handler The handler to register
     */
    public void registerRoutingHandler(PathRoutingHandler handler) {
        this.handlers.add(handler);
    }

    /**
     * Un-registers a previously registered routing handler
     *
     * @param handler The handler to un-register
     */
    public void unregisterRoutingHandler(PathRoutingHandler handler) {
        this.handlers.remove(handler);
    }

    /**
     * Invokes {@link PathRoutingHandler#predict(PathPredictEvent)} on all
     * registered routing handlers. This is used to provide a prediction
     * for how trains will navigate on track.
     *
     * @param event
     * @see PathPredictEvent
     */
    public void predictRoutingHandler(PathPredictEvent event) {
        this.handlers.forEach(handler -> handler.predict(event));
    }

    /**
     * Sets the maximum amount of time in milliseconds the path finding algorithm will spend
     * doing routing calculations.
     *
     * @param durationMillis
     */
    public void setMaxProcessingPerTick(int durationMillis) {
        this.maxProcessingPerTick = durationMillis;
    }

    public int getNumPendingNodes() {
        return this.pendingDiscovery.size() + this.pendingNodes.size();
    }

    public int getNumPendingOperations() {
        return this.pendingOperations.size();
    }

    public void notifyOfCompletion(CommandSender sender) {
        this.sendersToNotifyOfCompletion.add(sender);
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

                // Cancel all scheduled (pending) nodes
                pendingNodes.clear();
                scheduledNodesSinceIdle.clear();
            }
        }.read();

        hasChanges = false;

        if (TCConfig.rerouteOnStartup) {
            reroute();
        }
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
     * Stops all currently scheduled and pending path finding operations.
     * Existing routing information is kept.
     */
    public void stopRouting() {
        this.pendingDiscovery.clear();
        this.pendingNodes.clear();
        this.pendingOperations.clear();
        this.scheduledNodesSinceIdle.clear();
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
        scheduledNodesSinceIdle.add(startNode);
    }

    /**
     * Schedules a node to start calculating all neighboring paths, if this node was not recently re-routed
     * already.
     *
     * @param startNode
     */
    public void scheduleNodeIfNotRecentlyRouted(PathNode startNode) {
        if (scheduledNodesSinceIdle.add(startNode)) {
            pendingNodes.add(startNode);
        }
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
            this.scheduledNodesSinceIdle.clear();
            {
                List<CommandSender> senders = new ArrayList<>(this.sendersToNotifyOfCompletion);
                this.sendersToNotifyOfCompletion.clear();
                for (CommandSender sender : senders) {
                    if (!(sender instanceof ConsoleCommandSender || sender instanceof Player)) {
                        continue; // don't send to command blocks, that's weird
                    }
                    if (sender instanceof Player && !((Player) sender).isOnline()) {
                        continue; // don't send to offline players
                    }
                    sender.sendMessage(ChatColor.GREEN + "Train rerouting completed!");
                }
            }
            return;
        }
        boolean done;
        final long startTime = System.currentTimeMillis();
        while (!this.pendingOperations.isEmpty()) {
            PathFindOperation operation = this.pendingOperations.peek();
            done = false;
            if (DEBUG_MODE) {
                TrainCarts.plugin.log(Level.INFO, "DISCOVERING EVERYTHING FROM " + operation.startNode.getDisplayName() +
                        " INTO " + operation.getJunctionName());
            }
            // Perform the operations in steps
            // Not per step, because System.currentTimeMillis is not entirely cheap!
            do {
                done = operation.next();
            } while (!done && (System.currentTimeMillis() - startTime) <= this.maxProcessingPerTick);
            if (done) {
                this.pendingOperations.poll();
            } else {
                break; // Ran out of time
            }
        }

        // Important: wipe any rail and sign caches we have polluted with temporary block data
        // This will momentarily cause the plugin to run slower, but we must do this or risk out of memory!
        RailLookup.forceRecalculation();
    }

    // Discovers new switchers and destination signs. Stops upon the first new node found.
    private void addNewlyDiscovered() {
        final long startTime = System.currentTimeMillis();
        do {
            BlockLocation railLocation = this.pendingDiscovery.poll();
            if (railLocation == null) {
                break;
            }

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

            // Process this location
            RailState initialState = RailState.getSpawnState(RailPiece.create(railType, railBlock));
            PathRoutingHandler.PathRouteEvent routeEvent = new PathRoutingHandler.PathRouteEvent(this, initialState);
            for (PathRoutingHandler handler : this.handlers) {
                handler.process(routeEvent);
            }
        } while ((System.currentTimeMillis() - startTime) <= this.maxProcessingPerTick);
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
                        TrainCarts.plugin.log(Level.INFO, "NODE " + node.getDisplayName() + " CONTAINS A SWITCHER, BRANCHING OFF");
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
        if (!state.railPiece().offlineWorld().isLoaded()) {
            return;
        }
        pendingOperations.offer(new PathFindOperation(this, node, state, junction));
    }

    private static class PathFindOperation {
        private final TrackWalkingPoint p;
        private final PathNode startNode;
        private final String junctionName;
        private final PathRoutingHandler.PathRouteEvent routeEvent; // re-used

        public PathFindOperation(PathProvider provider, PathNode startNode, RailState state, RailJunction junction) {
            this.p = new TrackWalkingPoint(state);
            this.p.setLoopFilter(true);
            this.junctionName = junction.name();
            this.startNode = startNode;
            this.routeEvent = new PathRoutingHandler.PathRouteEvent(provider, state.railWorld());

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
            if (!this.p.state.railLookup().isValid()) {
                return true; // Abort. World not available.
            }
            if (!this.p.moveFull()) {
                return true;
            }

            // Handle event
            routeEvent.reset(this.p.state);
            for (PathRoutingHandler handler : routeEvent.provider().handlers) {
                handler.process(routeEvent);
            }

            // Process results
            PathNode foundNode = routeEvent.getLastSetNode();
            if (foundNode != null && !this.startNode.location.equals(foundNode.location)) {
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
                    TrainCarts.plugin.log(Level.INFO, "MADE CONNECTION FROM " + startNode.getDisplayName() + " TO " + foundNode.getDisplayName());
                }
                return true; // Finished
            }

            // If route blocked, finish routing here
            if (routeEvent.isBlocked()) {
                return true;
            }

            return false;
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
        PathRoutingHandler.PathRouteEvent routeEvent = new PathRoutingHandler.PathRouteEvent(this, state);
        for (PathRoutingHandler handler : this.handlers) {
            handler.process(routeEvent);
        }

        if (routeEvent.isBlocked()) {
            return PathRailInfo.BLOCKED;
        } else if (routeEvent.getLastSetNode() != null) {
            return PathRailInfo.NODE;
        } else {
            return  PathRailInfo.NONE;
        }
    }
}
