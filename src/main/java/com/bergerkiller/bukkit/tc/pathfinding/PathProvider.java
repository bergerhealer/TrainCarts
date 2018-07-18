package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
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
            BlockLocation newNodeLocation;
            String newNodeName;
            boolean hasFinished = false;
            for (Block signblock : Util.getSignsFromRails(nextRail)) {
                SignActionEvent event = new SignActionEvent(signblock);
                if (event.getMode() != SignActionMode.NONE) {
                    if (event.isType("tag", "switcher")) {
                        newNodeLocation = new BlockLocation(nextRail);
                        newNodeName = newNodeLocation.toString();
                    } else if (event.isType("destination")) {
                        newNodeLocation = new BlockLocation(nextRail);
                        newNodeName = event.getLine(2);
                    } else if (event.isType("blocker") && event.isWatchedDirection(p.state.enterDirection()) && event.isPowerAlwaysOn()) {
                        hasFinished = true;
                        break;
                    } else {
                        continue;
                    }
                    if (!newNodeName.isEmpty() && !startNode.containsName(newNodeName)) {
                        // include distance between spawn position on rail, and the current position with the walker
                        Location spawnPos = p.state.railType().getSpawnLocation(p.state.railBlock(), p.state.position().getMotionFace());
                        double totalDistance = p.movedTotal + spawnPos.distanceSquared(p.state.positionLocation());

                        // finished, we found our first target - create connection
                        PathNode to = PathNode.getOrCreate(newNodeName, newNodeLocation);
                        this.startNode.addNeighbour(to, totalDistance, this.getJunctionName());
                        hasFinished = true;
                        if (DEBUG_MODE) {
                            System.out.println("MADE CONNECTION FROM " + startNode.getDisplayName() + " TO " + newNodeName);
                        }
                    }
                }
            }
            return hasFinished;
        }
    }
}
