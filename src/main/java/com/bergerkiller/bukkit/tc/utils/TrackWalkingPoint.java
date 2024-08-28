package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.pathfinding.PathNavigateEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * A Moving point implementation that allows one to 'walk' along rails without
 * restricting to full-block movement, allowing for accurate distance calculations
 * and accurate Minecart positioning information for spawning on rails.
 */
public class TrackWalkingPoint {
    /**
     * Stores the current state of walking
     */
    public final RailState state;
    /**
     * Rail logic of {@link #state}
     */
    public RailLogic currentRailLogic;
    /**
     * Rail path of {@link #currentRailLogic}
     */
    public RailPath currentRailPath;
    /**
     * The actual distance moved during the last {@link #move(double)} call
     */
    public double moved = 0.0;
    /**
     * The total distance moved since instantiating this walking point
     */
    public double movedTotal = 0.0;
    /**
     * The reason a previous {@link #move(double)} call failed and returned false
     */
    public FailReason failReason = FailReason.NONE;
    /**
     * Is used to make sure rails are only crossed once, if enabled
     */
    private Set<Block> loopFilter = null;
    /**
     * Detects a recurring loop when stuck moving on the same location (end of rail?)
     */
    private Vector lastLocation = null;
    /**
     * Counter to track repeated positions, indicating getting stuck
     */
    private int _stuckCtr = 0;

    private boolean first = true;
    private boolean isAtEnd = false;
    private NavigatorWithEvent<?> navigator = null;

    public TrackWalkingPoint(RailState state) {
        state.position().assertAbsolute();
        this.state = state.clone();
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
        if (this.isDerailed()) {
            this.failReason = FailReason.NO_RAIL;
        }
    }

    public TrackWalkingPoint(Location startPos, Vector motionVector) {
        this.state = new RailState();
        this.state.setRailPiece(RailPiece.createWorldPlaceholder(startPos.getWorld()));
        this.state.position().setMotion(motionVector);
        this.state.position().setLocation(startPos);
        RailType.loadRailInformation(this.state);
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
        if (this.isDerailed()) {
            this.failReason = FailReason.NO_RAIL;
        }
    }

    public TrackWalkingPoint(Block startRail, BlockFace motionFace) {
        this.state = new RailState();
        this.state.position().relative = false;
        if (startRail != null) {
            this.state.setRailPiece(RailPiece.create(RailType.getType(startRail), startRail));
            this.state.position().setMotion(motionFace);
            this.state.position().setLocation(this.state.railType().getSpawnLocation(startRail, motionFace));
            this.state.initEnterDirection(); // Not loadRailInformation because we go type -> spawn location
            this.currentRailLogic = this.state.loadRailLogic();
            this.currentRailPath = this.currentRailLogic.getPath();
            if (this.isDerailed()) {
                this.failReason = FailReason.NO_RAIL;
            }
        } else {
            this.failReason = FailReason.NO_RAIL;
        }
    }

    /**
     * Sets a navigator on this track walking point. The navigator is informed of visited rails,
     * and can alter the path followed (change junctions). Incompatible with {@link #setFollowPredictedPath(MinecartMember)}
     *
     * @param navigator Navigator to use
     */
    public void setNavigator(Navigator<?> navigator) {
        if (navigator == null) {
            this.navigator = null; // Reset
            return;
        }

        NavigatorWithEvent<?> currentNav = this.navigator;
        if (currentNav == null || currentNav.navigator != navigator) {
            this.navigator = currentNav = new NavigatorWithEvent<>(navigator);

            // Initialize the navigator event
            if (!this.isDerailed()) {
                currentNav.navigate(this.state, this.currentRailPath, this.movedTotal);
            }

            if (currentNav.event.isNavigationAborted()) {
                this.failReason = FailReason.NAVIGATION_ABORTED;
                // Iteration is halted later
            }
        }
    }

    /**
     * Configures this walking point to follow a predicted path of a particular Minecart Member.
     * Signs and other routing nodes along the way that can perform track switching will be asked
     * where this member should go, and that path is followed accordingly.
     *
     * @param member MinecartMember to track following the predicted path. Null to disable.
     */
    public void setFollowPredictedPath(MinecartMember<?> member) {
        setNavigator((member == null) ? null : new Predictor(member));
    }

    /**
     * Gets a speed limit imposed for the current rail block.
     * {@link #setFollowPredictedPath(MinecartMember)} must be called first before
     * this method can be used. Otherwise, it will always return {@link Double#MAX_VALUE}.
     *
     * @return predicted speed limit, {@link Double#MAX_VALUE} if there is none.
     */
    public double getPredictedSpeedLimit() {
        NavigatorWithEvent<?> navigator = this.navigator;
        return (navigator != null && navigator.event instanceof PathPredictEvent)
                ? ((PathPredictEvent) navigator.event).getSpeedLimit()
                : Double.MAX_VALUE;
    }

    /**
     * If a block was encountered that must be tracked, returns the remaining amount
     * of distance until we get beyond this current block.
     *
     * @return predicted remaining block distance
     */
    public double getPredictedRemainingBlockDistance() {
        NavigatorWithEvent<?> navigator = this.navigator;
        return (navigator != null && navigator.navigator instanceof Predictor)
                ? Math.max(0.0, ((Predictor) navigator.navigator).maxPredictorEndDistance - this.movedTotal)
                : 0.0;
    }

    /**
     * Tells the walker to skip the very first starting point when running
     * {@link #move(double)} for the first time. This means the first position
     * returned after a move is a distance from the origin.
     */
    public void skipFirst() {
        this.first = false;
    }

    /**
     * Moves a full step towards the next rails block, limiting the steps taken by the limit parameter.
     * This can be used to discover the rail blocks that cover a particular stretch of track.
     * The {@link #moved} and {@link #movedTotal} parameters are updated with this call.<br>
     * <br>
     * After each iteration, the state on the rails is positioned at the very end of the current
     * rail logic, until the limit is reached, at which the position is exactly at this limit.
     * To check whether the step failed because of reaching the limit, check that {@link #failReason}
     * is set to {@link FailReason#LIMIT_REACHED}.
     *
     * @param limit distance to move
     * @return True if the step was successful
     */
    public boolean moveStep(double limit) {
        if (isMovementAborted()) {
            return false;
        }

        // If not already at the end of the current logic, move as much as possible
        // to the end. In normal iteration, this only occurs once.
        if (this.isAtEnd) {
            this.moved = 0.0;
        } else {
            double movedOnPath = this.currentRailPath.move(this.state, limit);
            this.state.initEnterDirection();

            // Set initial moved value and reduce this from the limit
            // This is so that during the next move below, the moved distance here
            // is taken into account.
            this.moved = movedOnPath;
            this.movedTotal += movedOnPath;
            limit -= movedOnPath;

            // When moved closely equals the limit, we've reached the end of track.
            if (limit > -1e-10 && limit < 1e-10) {
                this.failReason = FailReason.LIMIT_REACHED;
                return false;
            }

            // If not, then the end is reached
            this.isAtEnd = true;
        }

        // Attempt loading the next rail information. Return false if no more rails exist.
        if (!this.loadNextRail()) {
            return false;
        }

        // Try to move as much distance forward as possible
        // This is during normal iteration and will position it at the very end
        double movedOnPath = this.currentRailPath.move(this.state, limit);
        this.state.initEnterDirection();

        // Update moved distances
        this.moved += movedOnPath;
        this.movedTotal += movedOnPath;
        limit -= movedOnPath;

        // When moved closely equals the limit, we've reached the end of track.
        if (limit > -1e-10 && limit < 1e-10) {
            this.failReason = FailReason.LIMIT_REACHED;
            return false;
        }

        // All good, found the end of the next rail!
        this.isAtEnd = true;
        return true;
    }

    /**
     * Moves the full distance past the current rail's path to the next rail.
     * The {@link #state} position and movement direction are updated.
     * Can be used in a loop to iterate by all the rail blocks.
     *
     * @return True if movement was successful, False if not
     */
    public boolean moveFull() {
        if (isMovementAborted()) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Move the full length of the path, to the end of the path'
        this.moved = this.currentRailPath.move(this.state, Double.MAX_VALUE);
        this.movedTotal += this.moved;
        this.state.initEnterDirection();
        this.isAtEnd = true;

        // Attempt moving to next rails block
        if (!loadNextRail()) {
            return false;
        }

        // Snap onto the path before returning
        this.currentRailPath.snap(this.state.position(), this.state.railBlock());
        return true;
    }

    /**
     * Moves the distance specified over the current rails, moving past multiple rails if needed.
     * The {@link #state} position and movement direction are updated.
     *
     * @param distance to move
     * @return True if movement was successful, False if not
     */
    public boolean move(final double distance) {
        if (isMovementAborted()) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Walk as much distance as we can along the current rails
        double remainingDistance = distance;
        int infCycleCtr = 0;
        while (true) {
            // Move along the path
            double moved;
            if (((moved = this.currentRailPath.move(this.state, remainingDistance)) != 0.0) || (remainingDistance <= 0.0001)) {
                infCycleCtr = 0;
                remainingDistance -= moved;
                if (remainingDistance <= 0.00001) {
                    // Moved the full distance
                    this.moved = distance;
                    this.movedTotal += this.moved;
                    this.state.initEnterDirection();
                    return true;
                }
            } else if (++infCycleCtr > 100) {
                // Infinite loop detected. Stop here.
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Infinite rails loop detected at " + this.state.railBlock());
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Rail Logic at rail is " + this.currentRailLogic);
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Rail Type at rail is " + this.state.railType());
                this.moved = (distance - remainingDistance);
                this.movedTotal += this.moved;
                this.failReason = FailReason.CYCLIC_PATH;
                this.state.initEnterDirection();
                return false;
            }

            // Attempt moving to next rails block
            this.isAtEnd = true;
            if (!loadNextRail()) {
                this.moved = (distance - remainingDistance);
                this.movedTotal += this.moved;
                this.state.initEnterDirection();
                return false;
            }
        }
    }

    private boolean loadNextRail() {
        RailPath.Position position = this.state.position();
        NavigatorWithEvent<?> navigator = this.navigator;

        // If position is already the same then we ran into a nasty loop that is no good!
        // Break out of it when detected to avoid freezing the server
        if (this.lastLocation == null) {
            this.lastLocation = new Vector(position.posX, position.posY, position.posZ);
            this._stuckCtr = 0;
        } else if (this.lastLocation.getX() == position.posX &&
                   this.lastLocation.getY() == position.posY &&
                   this.lastLocation.getZ() == position.posZ)
        {
            this.failReason = FailReason.CYCLIC_PATH;

            if (++this._stuckCtr > 20) {
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Stuck on rails block " + this.state.railBlock());
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Rail Logic at rail is " + this.currentRailLogic);
                TrainCarts.plugin.log(Level.SEVERE, "[TrackWalkingPoint] Rail Type at rail is " + this.state.railType());
            }

            return false;
        } else {
            this.lastLocation.setX(position.posX);
            this.lastLocation.setY(position.posY);
            this.lastLocation.setZ(position.posZ);
            this._stuckCtr = 0;
        }

        // If navigating and the navigator altered the path, move to
        // this new position and advance a small amount beyond the rail.
        if (navigator != null && navigator.event.hasSwitchedPosition()) {
            navigator.event.getSwitchedPosition().copyTo(position);
            position.makeAbsolute(navigator.event.railBlock());
        }

        // Load next rails information
        // Move the path an infinitesmall amount to beyond the current rail
        position.smallAdvance();

        // Rail Type lookup + loop filter logic
        Block prevRailBlock = this.state.railBlock();
        if (RailType.loadRailInformation(this.state) &&
            this.loopFilter != null &&
            !BlockUtil.equals(this.state.railBlock(), prevRailBlock) &&
            !this.loopFilter.add(this.state.railBlock()))
        {
            this.state.setRailPiece(this.state.railPiece().asNoneType());
            this.failReason = FailReason.LOOP_DETECTED;
        }

        // No next rail available. This is it.
        if (this.state.railType() == RailType.NONE) {
            this.failReason = FailReason.NO_RAIL;
            return false;
        }

        // Refresh rail logic for the new position and state
        // Rail logic could return an empty path if stuff is outdated
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
        if (this.currentRailPath.isEmpty()) {
            this.failReason = FailReason.NO_RAIL;
            return false;
        }

        this.isAtEnd = true;

        // Update predictor so the speed limit / switched position is updated
        if (navigator != null) {
            navigator.navigate(this.state, this.currentRailPath, this.movedTotal);

            // Handle navigation aborting
            if (navigator.event.isNavigationAborted()) {
                this.failReason = FailReason.NAVIGATION_ABORTED;
                return false;
            }
        }

        return true;
    }

    private boolean isDerailed() {
        return this.state.railType() == RailType.NONE || this.currentRailPath.isEmpty();
    }

    private boolean isMovementAborted() {
        if (isDerailed()) {
            return true;
        }

        // If navigator is installed and halted it, fail
        NavigatorWithEvent<?> navigator = this.navigator;
        if (navigator != null && navigator.event.isNavigationAborted()) {
            return true;
        }

        return false;
    }

    /**
     * Sets whether a loop filter is employed, which tracks the positions already returned
     * and stops iteration when encountering a block already visited.
     * 
     * @param enabled
     */
    public void setLoopFilter(boolean enabled) {
        this.loopFilter = enabled ? new HashSet<Block>() : null;
        if (enabled && !isDerailed()) {
            this.loopFilter.add(this.state.railBlock());
        }
    }

    /**
     * Moves full or smaller steps until a particular rails block is reached.
     * The Spawn Location of the rails block is moved towards.
     * 
     * @param railsBlock to find
     * @param maxDistance when to stop looking (and return False)
     * @return True when the rails was found, False if not.
     */
    public boolean moveFindRail(Block railsBlock, double maxDistance) {
        // Move full rail distances until the rails block is found. if not starting out on the rail
        this.movedTotal = 0.0;
        boolean startedOnRail = BlockUtil.equals(this.state.railBlock(), railsBlock);
        if (!startedOnRail) {
            do {
                // Out of tracks or distance exceeded
                if (!this.moveFull()) {
                    return false;
                }
                if (this.movedTotal > maxDistance) {
                    this.failReason = FailReason.LIMIT_REACHED;
                    return false;
                }
            } while (!BlockUtil.equals(this.state.railBlock(), railsBlock));
        }

        // Found our rails Block! Move a tiny step further onto it.
        // Query the desired spawn location that we should move towards.
        Location spawnLocation = this.state.railType().getSpawnLocation(railsBlock, this.state.enterFace());
        for (int i = 0; i < 10; i++) {
            double distance = this.state.position().distance(spawnLocation);
            if (distance < 1e-4) {
                // Reached spawn location
                break;
            }
            double moved = this.currentRailPath.move(this.state, distance);
            this.movedTotal += moved;
            if (moved < 1e-4) {
                // When we start out on the rail, we could be walking into the wrong direction
                // In that case, fail the walker, as we are really moving <off> the current rail,
                // never reaching the intended center position.
                if (startedOnRail) {
                    this.failReason = FailReason.LIMIT_REACHED;
                    return false;
                }

                // End of path
                break;
            }
        }
        this.moved = this.movedTotal;
        return this.movedTotal <= maxDistance;
    }

    /**
     * A navigator can be installed on a track walking point to alter the path followed
     * on the track mid-iteration. This can be used for path prediction with switchers
     * and to iterate encountered rail pieces.<br>
     * <br>
     * The navigator is called for every (new) track piece encountered, but not for every
     * small movement step.
     *
     * @param <E> - Type of PathNavigateEvent to construct and use in {@link #navigate(PathNavigateEvent)}
     */
    public interface Navigator<E extends PathNavigateEvent> {
        /**
         * Called for every rail piece encountered by the track walking point
         *
         * @param event PathNavigateEvent containing track information. Also has actions
         *              that can be performed, such as changing the current position
         *              navigated on.
         */
        void navigate(E event);

        /**
         * Overridable method that returns the event class type to create that the
         * {@link #navigate(PathNavigateEvent)} method receives.
         *
         * @return PathNavigateEvent or extended type
         */
        E createNewEvent();
    }

    private static class NavigatorWithEvent<E extends PathNavigateEvent> {
        public final Navigator<E> navigator;
        public final E event;

        public NavigatorWithEvent(Navigator<E> navigator) {
            this.navigator = navigator;
            this.event = navigator.createNewEvent();
        }

        public void navigate(RailState railState, RailPath railPath, double currentPosition) {
            event.resetToInitialState(railState, railPath, currentPosition);
            navigator.navigate(event);
        }
    }

    private static class Predictor implements Navigator<PathPredictEvent> {
        private final MinecartMember<?> member;
        private List<BlockPredictor> activeBlockPredictors = Collections.emptyList();
        private Set<Object> usedBlockPredictorTokens = Collections.emptySet();
        private double maxPredictorEndDistance = 0.0;

        public Predictor(MinecartMember<?> member) {
            this.member = member;
        }

        @Override
        public PathPredictEvent createNewEvent() {
            return new PathPredictEvent(member.getTrainCarts().getPathProvider(), member);
        }

        @Override
        public void navigate(PathPredictEvent event) {
            double currentDistance = event.currentDistance();

            event.provider().predictRoutingHandler(event);

            // Process active block predictors. Remove when they return false, or distance is beyond the max.
            boolean predictorsRemoved = false;
            for (Iterator<BlockPredictor> iter = activeBlockPredictors.iterator(); iter.hasNext();) {
                BlockPredictor blockPredictor = iter.next();
                if (!blockPredictor.handler.update(event, currentDistance - blockPredictor.startDistance)
                        || currentDistance >= blockPredictor.endDistance) {
                    iter.remove();
                    predictorsRemoved = true;
                }
            }
            if (predictorsRemoved) {
                maxPredictorEndDistance = 0.0;
                for (BlockPredictor blockPredictor : activeBlockPredictors) {
                    maxPredictorEndDistance = Math.max(maxPredictorEndDistance, blockPredictor.endDistance);
                }
            }

            // Register newly added trackers
            if (event.hasNewBlockTrackers()) {
                if (activeBlockPredictors.isEmpty()) {
                    activeBlockPredictors = new ArrayList<>();
                }
                if (usedBlockPredictorTokens.isEmpty()) {
                    usedBlockPredictorTokens = new HashSet<>();
                }
                for (PathPredictEvent.ActiveBlockHandler activeHandler : event.getNewBlockTrackers()) {
                    if (!usedBlockPredictorTokens.add(activeHandler.token)) {
                        continue;
                    }
                    BlockPredictor blockPredictor = new BlockPredictor(currentDistance, activeHandler);
                    activeBlockPredictors.add(blockPredictor);
                    maxPredictorEndDistance = Math.max(maxPredictorEndDistance, blockPredictor.endDistance);
                }
            }
        }
    }

    private static class BlockPredictor {
        public final PathPredictEvent.BlockHandler handler;
        public final double startDistance;
        public final double endDistance;

        public BlockPredictor(double currentDistance, PathPredictEvent.ActiveBlockHandler activeHandler) {
            this.handler = activeHandler.handler;
            this.startDistance = currentDistance;
            this.endDistance = startDistance + activeHandler.maxDistance;
        }
    }

    /**
     * A reason for functions like move() to return false.
     * Diagnostic information, helpful for debugging.
     */
    public static enum FailReason {
        /** No failure has occurred yet */
        NONE,
        /** No rails were found beyond the current position */
        NO_RAIL,
        /** The rail path does not advance, an error inside {@link RailType#getLogic(RailState)} */
        CYCLIC_PATH,
        /** A loop on the track was detected */
        LOOP_DETECTED,
        /** An imposed movement limit was reached */
        LIMIT_REACHED,
        /** A navigator was installed and {@link PathNavigateEvent#abortNavigation()} was called */
        NAVIGATION_ABORTED
    }
}
