package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.cache.RailTypeCache;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

public class PathProvider extends Task {
    private static final int STEP_COUNT = 100; // Steps performed per timing check
    private static final int MAX_PROCESSING_PER_TICK = 30; // Maximum processing time in Ms per tick
    public static boolean DEBUG_MODE = false;
    private static PathProvider task;
    private Queue<BlockLocation> pendingDiscovery = new LinkedList<BlockLocation>();
    private Set<PathNode> pendingNodes = new LinkedHashSet<>();
    private Queue<PathFindOperation> pendingOperations = new LinkedList<>();

    private PathProvider(JavaPlugin plugin) {
        super(plugin);
    }

    public static void init() {
        task = new PathProvider(TrainCarts.plugin);
        task.start(1, 1);
    }

    public static void deinit() {
        if (task == null) {
            return;
        }
        task.stop();
        task = null;
    }

    /**
     * Tells this Path Provider to start calculating all neighboring paths from the node specified
     *
     * @param startNode to schedule
     */
    public static void schedule(PathNode startNode) {
        if (task != null) {
            task.pendingNodes.add(startNode);
        }
    }

    /**
     * Tells this Path Provider to schedule new destination and switcher sign discovery, starting at a particular
     * rails block. This rail location must have signs that switch or declare a destination, otherwise
     * nothing will happen.
     * 
     * @param railLocation to discover destinations and switchers at
     */
    public static void discover(BlockLocation railLocation) {
        if (task != null) {
            task.pendingDiscovery.add(railLocation);
        }
    }

    /**
     * Checks whether this Path Provider is currently busy processing path finding
     *
     * @return True if processing is being performed, False if not
     */
    public static boolean isProcessing() {
        return task != null && (!task.pendingOperations.isEmpty() || !task.pendingNodes.isEmpty());
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
        RailTypeCache.reset();
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
                SignActionEvent event = new SignActionEvent(trackedSign.signBlock, trackedSign.railBlock);
                SignAction action = SignAction.getSignAction(event);
                if (action == null) {
                    continue;
                }

                // Check for switchers and potential (new) destinations
                boolean switchable = action.isRailSwitcher(event);
                String destinationName = action.getRailDestinationName(event);
                if (!switchable && destinationName == null) {
                    continue; // Not path finding related
                }

                // Get location of the rails, and define a name to use
                String locationStr = railLocation.toString();
                if (switchable && destinationName == null) {
                    destinationName = locationStr;
                }

                // Add new path node
                PathNode to = PathNode.getOrCreate(destinationName, railLocation);

                // when switchable and name does not equal the location String, add an extra tag name as fallback
                if (switchable && !to.containsName(locationStr)) {
                    to.addName(PathNode.SWITCHER_NAME_FALLBACK);
                }
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
                        System.out.println("NODE " + node.getDisplayName() + " CONTAINS A SWITCHER");
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
                    state1.setRailBlock(startRail);
                    state1.setRailType(startType);
                    state1.position().setLocation(startType.getSpawnLocation(startRail, BlockFace.NORTH));
                    if (!RailType.loadRailInformation(state1)) {
                        continue;
                    }

                    // Snap onto rails
                    state1.loadRailLogic().getPath().snap(state1.position(), state1.railBlock());

                    // Create opposite direction state
                    RailState state2 = state1.clone();
                    state2.position().invertMotion();
                    state2.initEnterDirection();

                    // Walk both states to the end of the path
                    state1.loadRailLogic().getPath().move(state1, Double.MAX_VALUE);
                    state2.loadRailLogic().getPath().move(state2, Double.MAX_VALUE);

                    // Schedule them
                    scheduleNode(node, state1, new RailJunction("1", state1.position().clone()));
                    scheduleNode(node, state2, new RailJunction("2", state2.position().clone()));
                }
            }
            this.pendingNodes.clear();
        }
    }

    private void scheduleNode(PathNode node, RailState state, RailJunction junction) {
        if (task != null) {
            task.pendingOperations.offer(new PathFindOperation(node, state, junction));
        }
    }

    private static class PathFindOperation {
        private final TrackWalkingPoint p;
        private final PathNode startNode;
        private final String junctionName;

        public PathFindOperation(PathNode startNode, RailState state, RailJunction junction) {
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
            for (RailSignCache.TrackedSign trackedSign : RailSignCache.getSigns(p.state.railType(), p.state.railBlock())) {
                // Discover a SignAction at this sign
                SignActionEvent event = new SignActionEvent(trackedSign.signBlock, trackedSign.railBlock);
                event.setAction(SignActionType.GROUP_ENTER);
                SignAction action = SignAction.getSignAction(event);
                if (action == null) {
                    continue;
                }

                // Check for switchers and potential (new) destinations
                boolean switchable = action.isRailSwitcher(event);
                String destinationName = action.getRailDestinationName(event);
                if (switchable || destinationName != null) {
                    // Get location of the rails, and define a name to use
                    BlockLocation railLocation = new BlockLocation(nextRail);
                    String locationStr = railLocation.toString();
                    if (switchable && destinationName == null) {
                        destinationName = locationStr;
                    }

                    // Quick check that the start node not already contains this name we are trying to define
                    // This is extra protection against loops
                    if (!startNode.containsName(destinationName)) {

                        // include distance between spawn position on rail, and the current position with the walker
                        double totalDistance = p.movedTotal;
                        {
                            Location spawnPos = p.state.railType().getSpawnLocation(p.state.railBlock(), p.state.position().getMotionFace());
                            totalDistance += spawnPos.distanceSquared(p.state.positionLocation());
                        }

                        // finished, we found our first target - create connection
                        PathNode to = PathNode.getOrCreate(destinationName, railLocation);

                        // when switchable and name does not equal the location String, add an extra tag name as fallback
                        if (switchable && !to.containsName(locationStr)) {
                            to.addName(PathNode.SWITCHER_NAME_FALLBACK);
                        }

                        this.startNode.addNeighbour(to, totalDistance, this.getJunctionName());
                        hasFinished = true;
                        if (DEBUG_MODE) {
                            System.out.println("MADE CONNECTION FROM " + startNode.getDisplayName() + " TO " + destinationName);
                        }
                    }

                } else if (action.isPathFindingBlocked(event, p.state)) {
                    // If blocked, abort
                    hasFinished = true;
                    break;
                }
            }
            return hasFinished;
        }
    }

}
