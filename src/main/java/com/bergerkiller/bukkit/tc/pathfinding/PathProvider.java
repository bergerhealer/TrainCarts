package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PathProvider extends Task implements TrainCarts.Provider {
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
    private final Queue<BlockLocation> pendingDiscovery = new LinkedList<BlockLocation>();
    /**
     * Nodes in the pathfinding graph that need to perform re-discovery. This means checking
     * all possible directions from which the node can be traveled and seeing what is on the
     * other end. Regularly these nodes are turned into PathFindOperation instances to perform
     * processing.
     */
    private final Set<PathNode> pendingNodes = new LinkedHashSet<>();
    /**
     * Pending operations from a path node into a given direction, where the route to other
     * nodes is computed. Each pending node can turn into one or more pending operations.
     */
    private final Queue<PathFindOperation> pendingOperations = new LinkedList<>();
    /**
     * All nodes that had re-routing scheduled since the re-routing algorithm fell idle.
     * This is used to decide whether a node needs further calculations done when a route
     * to another node is added to it or changed. It avoids re-calculating the same routes
     * over and over again.<br>
     * <br>
     * Once no more operations are being done, then this set is cleared.
     */
    private final Set<PathNode> scheduledNodesSinceIdle = new HashSet<>();
    /**
     * Tracks the path nodes from which a reroute was initiated. When the rerouting finishes,
     * the plugin checks all these nodes to see if they still exist afterwards. If not, a
     * message is sent to the player informing of its removal.
     */
    private final Set<PathNodeSnapshot> pathNodesBeforeDiscovery = new HashSet<>();
    /**
     * People to notify when the routes have finished calculating
     */
    private final Set<CommandSender> sendersToNotifyOfCompletion = new HashSet<>();
    private boolean hasChanges = false;
    private int maxProcessingPerTick = DEFAULT_MAX_PROCESSING_PER_TICK;

    public PathProvider(TrainCarts plugin) {
        super(plugin);

        // Default TrainCarts routing handler - for signs
        registerRoutingHandler(new PathRoutingHandler() {
            @Override
            public void process(PathRouteEvent event) {
                boolean switchable = false;
                List<String> destinationNames = Collections.emptyList();
                for (RailLookup.TrackedSign trackedSign : event.railPiece().signs()) {
                    // Check there is a SignAction at this sign
                    SignAction action = trackedSign.getAction();
                    if (trackedSign.isRemoved() || action == null) {
                        continue;
                    }

                    // Check for blocker signs
                    SignRoutingEvent signEvent = new SignRoutingEvent(trackedSign);
                    signEvent.resetToInitialState(event.railState(), event.railPath(), event.currentDistance());
                    signEvent.overrideCartEnterState(event.railState());
                    action.route(signEvent);

                    // If blocked, abort. Don't even create / attach a node here.
                    // The order of the blocker sign doesn't matter - we know the train can't move on
                    if (signEvent.isBlocked()) {
                        event.setBlocked();
                        return;
                    }

                    // If a destination name or switchable is set, create a node here
                    // Resume navigation from this node onwards
                    if (signEvent.isRouteSwitchable() || !signEvent.getDestinationNames().isEmpty()) {
                        // Remember state for later
                        switchable |= signEvent.isRouteSwitchable();
                        if (!signEvent.getDestinationNames().isEmpty()) {
                            if (destinationNames.isEmpty()) {
                                destinationNames = new ArrayList<>();
                            }
                            destinationNames.addAll(signEvent.getDestinationNames());
                        }

                        // Clear any sort of path switched position, as it'll be overridden by this later sign
                        event.setSwitchedPosition(null);
                        continue;
                    }

                    // If a next position on the track was predicted, pass is along to the walker
                    if (signEvent.hasSwitchedPosition()) {
                        event.setSwitchedPosition(signEvent.getSwitchedPosition());
                    }
                }

                if (switchable || !destinationNames.isEmpty()) {
                    // Update the node we found with the information of the current sign
                    PathNode newFoundNode = event.createNode();
                    if (switchable) {
                        newFoundNode.addSwitcher();
                    }
                    destinationNames.forEach(newFoundNode::addName);
                }
            }

            @Override
            public void predict(PathPredictEvent event) {
                for (RailLookup.TrackedSign trackedSign : event.railPiece().signs()) {
                    // Check there is a SignAction at this sign
                    SignAction action = trackedSign.getAction();
                    if (trackedSign.isRemoved() || action == null || !action.hasPathFindingPrediction()) {
                        continue;
                    }

                    // Execute logic to probe for speed limits / switchers / blockers
                    SignActionEvent signEvent = trackedSign.createEvent(SignActionType.GROUP_ENTER);
                    signEvent.setMember(event.member());
                    signEvent.overrideCartEnterState(event.railState());
                    action.predictPathFinding(signEvent, event);
                }
            }
        });
    }

    @Override
    public TrainCarts getTrainCarts() {
        return (TrainCarts) super.getPlugin();
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
     * Gets whether a particular destination name exists on any world
     *
     * @param name Destination (node) name
     * @return True if it exists
     */
    public boolean nodeExistsOnAnyWorld(String name) {
        for (PathWorld world : getWorlds()) {
            if (world.getNodeByName(name) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to find a path node at the old location or the same name as
     * another node. The specified node is allowed to have been removed.
     *
     * @param snapshot PathNodeSnapshot of a previous node
     * @return Found PathNode, or null if the node has been removed
     */
    public PathNode tryFindNodeAgain(PathNodeSnapshot snapshot) {
        PathWorld world = getWorld(snapshot.getWorldName());
        if (world != null) {
            return world.tryFindNodeAgain(snapshot);
        } else {
            return null;
        }
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
     * Starts rerouting the paths from the destination names specified,
     * and from there all other changes we detect.
     *
     * @param destinationNames Destination names
     */
    public void rerouteFrom(List<String> destinationNames) {
        for (PathWorld world : getWorlds()) {
            world.rerouteFrom(destinationNames);
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
     * Tells this Path Provider to start calculating all neighboring paths from the node specified
     *
     * @param startNode to schedule
     * @deprecated Use {@link #scheduleNode(PathNode)} instead on PathProvider instance in TrainCarts plugin
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
     * nothing will happen.
     *
     * @param railLocation to discover destinations and switchers at
     * @deprecated Use {@link #discoverFromRail(BlockLocation)} instead
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
        PathWorld world = getWorld(railLocation.world);
        if (world != null) {
            PathNode atRail = world.getNodeAtRail(railLocation);
            if (atRail != null) {
                pathNodesBeforeDiscovery.add(atRail.getSnapshot());
            }
        }

        pendingDiscovery.add(railLocation);
    }

    /**
     * Tells this Path Provider to schedule new destination and switcher sign discovery, starting at a particular
     * path node. The node's rail location must have signs that switch or declare a destination, otherwise
     * nothing will happen.<br>
     * <br>
     * Will tell players after rerouting finishes when this node no longer exists after the reroute completed.
     *
     * @param node PathNode to discover destinations and switchers at
     */
    public void discoverFromNode(PathNode node) {
        pathNodesBeforeDiscovery.add(node.getSnapshot());
        discoverFromRail(node.location);
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
            getTrainCarts().log(Level.INFO, "Performing " + this.pendingOperations.size() + " pending path finding operations (can take a while)...");
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
            if (!sendersToNotifyOfCompletion.isEmpty()) {
                // Collect extra information about path nodes that existed before the reroute started
                // This properly logs information about path nodes passed with --from, or all of them
                // if a complete reroute was initiated.
                // Sort these nodes so that the ones with destination names are at the top.
                final int maxUpdateMessages = 10;
                List<MessageBuilder> updateMessages = pathNodesBeforeDiscovery.stream()
                        .sorted()
                        .map(snapshot -> snapshot.getUpdateMessage(tryFindNodeAgain(snapshot)))
                        .filter(Objects::nonNull)
                        .limit(maxUpdateMessages + 1)
                        .collect(Collectors.toList());
                final boolean hasMore = (updateMessages.size() > maxUpdateMessages);
                if (hasMore) {
                    updateMessages = updateMessages.subList(0, maxUpdateMessages);
                }

                // Clear for next reroute
                pathNodesBeforeDiscovery.clear();

                List<CommandSender> senders = new ArrayList<>(this.sendersToNotifyOfCompletion);
                this.sendersToNotifyOfCompletion.clear();
                for (CommandSender sender : senders) {
                    if (!(sender instanceof ConsoleCommandSender || sender instanceof Player)) {
                        continue; // don't send to command blocks, that's weird
                    }
                    if (sender instanceof Player && !((Player) sender).isValid()) {
                        continue; // don't send to offline players
                    }
                    for (MessageBuilder message : updateMessages) {
                        message.send(sender);
                    }
                    if (hasMore) {
                        sender.sendMessage(ChatColor.YELLOW + "...and more changes");
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
                getTrainCarts().log(Level.INFO, "DISCOVERING EVERYTHING FROM " + operation.startNode.getDisplayName() +
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
            PathRoutingHandler.PathRouteEvent routeEvent = new PathRoutingHandler.PathRouteEvent(this, initialState.railWorld());
            routeEvent.resetToInitialState(initialState, initialState.loadRailLogic().getPath(), 0.0);
            for (PathRoutingHandler handler : this.handlers) {
                handler.process(routeEvent);
            }
        } while ((System.currentTimeMillis() - startTime) <= this.maxProcessingPerTick);
    }

    private void addPendingNodes() {
        if (!this.pendingNodes.isEmpty()) {
            Set<PathNode> newPending = new LinkedHashSet<>(this.pendingNodes);
            for (PathNode node : newPending) {
                Block startRail = node.location.getBlock();
                RailType startType = RailType.getType(startRail);
                if (startType == RailType.NONE) {
                    // Track type can not be identified
                    continue;
                }
                if (node.containsSwitcher()) {
                    if (DEBUG_MODE) {
                        getTrainCarts().log(Level.INFO, "NODE " + node.getDisplayName() + " CONTAINS A SWITCHER, BRANCHING OFF");
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
            this.pendingNodes.removeAll(newPending);
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
        try {
            pendingOperations.offer(new PathFindOperation(this, node, state, junction));
        } catch (Throwable t) {
            getTrainCarts().getLogger().log(Level.SEVERE, "Failed to schedule path finding operation for node at " + node.location, t);
        }
    }

    private static class PathFindOperation {
        private final PathProvider provider;
        private final World world;
        private final TrackWalkingPoint p;
        private final PathNode startNode;
        private final String junctionName;

        public PathFindOperation(PathProvider provider, PathNode startNode, RailState state, RailJunction junction) {
            this.provider = provider;
            this.world = state.railWorld();
            this.junctionName = junction.name();
            this.startNode = startNode;

            this.p = new TrackWalkingPoint(state);
            this.p.setNavigator(new TrackWalkingPoint.Navigator<PathRoutingHandler.PathRouteEvent>() {
                @Override
                public void navigate(PathRoutingHandler.PathRouteEvent event) {
                    // Handle event
                    for (PathRoutingHandler handler : event.provider().handlers) {
                        handler.process(event);
                    }

                    // Process results
                    PathNode foundNode = event.getLastSetNode();
                    if (foundNode != null && !startNode.location.equals(foundNode.location)) {
                        // Calculate distance from the start node to this new node
                        // Include distance between spawn position on rail, and the current position with the walker
                        double totalDistance = p.movedTotal;
                        {
                            Location spawnPos = p.state.railType().getSpawnLocation(p.state.railBlock(), p.state.position().getMotionFace());
                            totalDistance += spawnPos.distance(p.state.positionLocation());
                        }

                        // Add neighbour
                        startNode.addNeighbour(foundNode, totalDistance, getJunctionName());
                        if (DEBUG_MODE) {
                            event.provider().getTrainCarts().log(Level.INFO, "MADE CONNECTION FROM " +
                                    startNode.getDisplayName() + " TO " + foundNode.getDisplayName());
                        }

                        // Finished
                        event.abortNavigation();
                        return;
                    }
                }

                @Override
                public PathRoutingHandler.PathRouteEvent createNewEvent() {
                    return new PathRoutingHandler.PathRouteEvent(provider, world);
                }
            });
            this.p.setLoopFilter(true);

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
            // All processing is done in the Navigator
            return !this.p.moveFull();
        }
    }

    /**
     * Queries all registered routing handlers while navigating over the track
     *
     * @param routeEvent Route event to notify to all handlers
     */
    public void handleRouting(PathRoutingHandler.PathRouteEvent routeEvent) {
        for (PathRoutingHandler handler : this.handlers) {
            handler.process(routeEvent);
        }
    }

    /**
     * Queries all registered routing handlers while navigating over the track
     * 
     * @param railState Current rail state position information
     * @param railPath Current rail path navigated over
     * @param currentDistance Current distance moved from start
     * @return PathRouteEvent storing the results of routing
     */
    public PathRoutingHandler.PathRouteEvent handleRouting(RailState railState, RailPath railPath, double currentDistance) {
        PathRoutingHandler.PathRouteEvent routeEvent = new PathRoutingHandler.PathRouteEvent(this, railState.railWorld());
        routeEvent.resetToInitialState(railState, railPath, currentDistance);
        handleRouting(routeEvent);
        return routeEvent;
    }
}
