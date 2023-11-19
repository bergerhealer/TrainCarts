package com.bergerkiller.bukkit.tc.pathfinding;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event passed to {@link PathRoutingHandler#predict(PathPredictEvent)} to let a handler predict
 * where a train will go upon landing on this routing node. Track switchers should configure
 * a switched {@link RailPath.Position} based on the properties of the train/member entering it.
 * Speed traps and blockers should configure a speed limit to use on the piece of track.
 */
public class PathPredictEvent {
    private final PathProvider provider;
    private final RailState railState;
    private RailPath railPath;
    private final MinecartMember<?> member;
    private RailPath.Position nextPosition;
    private double speedLimit;
    private List<ActiveBlockHandler> newBlockHandlers = Collections.emptyList();

    public PathPredictEvent(PathProvider provider, RailState railState, MinecartMember<?> member) {
        this.provider = provider;
        this.railState = railState;
        this.member = member;
        this.nextPosition = null;
        this.speedLimit = Double.MAX_VALUE;
    }

    /**
     * Resets this prediction event, so that it can be used again for a new rail position
     *
     * @param railPath Current Rail Path for the event
     */
    public void resetToInitialState(RailPath railPath) {
        this.railPath = railPath;
        this.setSpeedLimit(Double.MAX_VALUE);
        this.setSwitchedPosition(null);
        this.newBlockHandlers = Collections.emptyList();
    }

    /**
     * Gets the Minecart Group, which is the train being routed along the rails.
     * Conditional switching rules should use this member for the decision.
     *
     * @return group
     */
    public MinecartGroup group() {
        return this.member.getGroup();
    }

    /**
     * Gets the Minecart Member that is at the front of the train, currently being routed along
     * the rails.
     * Conditional switching rules should use this member for the decision.
     *
     * @return member
     */
    public MinecartMember<?> member() {
        return this.member;
    }

    /**
     * Gets the rail path provider singleton instance which
     * is currently doing the path-finding. To initialize or
     * modify nodes this provider is used.
     *
     * @return path provider
     */
    public PathProvider provider() {
        return this.provider;
    }

    /**
     * Gets the Path Node that is at the current rail. Can be null if
     * there are no rails here.
     *
     * @return path node, Null if there is no switched/destination node here
     */
    public PathNode pathNode() {
        return this.provider.getWorld(this.railState.railWorld())
                            .getNodeAtRail(this.railState.railBlock());
    }

    /**
     * Gets the exact rail block, type, position and direction
     * currently on the track.
     *
     * @return rail state details
     */
    public RailState railState() {
        return this.railState;
    }

    /**
     * Gets the rail path currently being moved over
     *
     * @return rail path details
     */
    public RailPath railPath() {
        return this.railPath;
    }

    /**
     * Gets the RailPiece of the current track. This stores the rail
     * type and block, and other information about a piece of rails.
     *
     * @return rail piece
     */
    public RailPiece railPiece() {
        return this.railState.railPiece();
    }

    /**
     * Gets the exact rail block the path finder is currently on
     *
     * @return rail block
     */
    public Block railBlock() {
        return this.railState.railBlock();
    }

    /**
     * Gets the World this event is for
     *
     * @return rail world
     */
    public World railWorld() {
        return this.railState.railWorld();
    }

    /**
     * Gets the next rail path end-position of the current rail block
     * that the {@link #member()} should move to.
     *
     * @return next rail path position, null if unset
     */
    public RailPath.Position getSwitchedPosition() {
        return this.nextPosition;
    }

    /**
     * Gets whether a switched position was set
     *
     * @return True if a switched position was set
     * @see #setSwitchedPosition(RailPath.Position)
     */
    public boolean hasSwitchedPosition() {
        return this.nextPosition != null;
    }

    /**
     * Sets the rail path end-position of the current rail block that the
     * {@link #member()} should move to.
     *
     * @param nextPosition Next rail path position
     */
    public void setSwitchedPosition(RailPath.Position nextPosition) {
        this.nextPosition = nextPosition;
    }

    /**
     * Sets the rail junction of the current rail block that the
     * {@link #member()} should move to.
     *
     * @param junction Junction to take and whose end-position to navigate to
     */
    public void setSwitchedJunction(RailJunction junction) {
        this.nextPosition = junction.position();
    }

    /**
     * Gets a speed limit. If not set, returns {@link Double#MAX_VALUE}
     *
     * @return speed limit
     */
    public double getSpeedLimit() {
        return this.speedLimit;
    }

    /**
     * Gets whether a speed limit was set
     *
     * @return True if a speed limit was set
     */
    public boolean hasSpeedLimit() {
        return this.speedLimit != Double.MAX_VALUE;
    }

    /**
     * Sets a speed limit the train should maintain as it approaches this
     * rail block. The train will slow down well in advance if there is a wait
     * deceleration set to meet this speed.
     *
     * @param speedLimit
     */
    public void setSpeedLimit(double speedLimit) {
        this.speedLimit = speedLimit;
    }

    /**
     * Same as {@link #setSpeedLimit(double)}, but if the speed limit
     * that is already set is lower than the one requested, keeps the original
     * speed limit.
     *
     * @param speedLimit
     */
    public void addSpeedLimit(double speedLimit) {
        if (speedLimit < this.speedLimit) {
            this.speedLimit = speedLimit;
        }
    }

    /**
     * Tracks all the rail blocks encountered from the current state position, up to the
     * maximum distance specified or when the handler callback returns false.
     *
     * @param handler BlockHandler receiving updated
     * @param maxDistance Maximum distance the block may contain
     */
    public void trackBlock(BlockHandler handler, double maxDistance) {
        if (newBlockHandlers.isEmpty()) {
            newBlockHandlers = new ArrayList<>();
        }
        newBlockHandlers.add(new ActiveBlockHandler(handler, maxDistance));
    }

    public boolean hasNewBlockTrackers() {
        return !newBlockHandlers.isEmpty();
    }

    public List<ActiveBlockHandler> getNewBlockTrackers() {
        return newBlockHandlers;
    }

    public static class ActiveBlockHandler {
        public final BlockHandler handler;
        public final double maxDistance;

        public ActiveBlockHandler(BlockHandler handler, double maxDistance) {
            this.handler = handler;
            this.maxDistance = maxDistance;
        }
    }

    /**
     * Handles a block section of track ahead of the current track position being predicted.
     * The callback of this handler is called for every track piece encountered.
     */
    public interface BlockHandler {
        /**
         * Navigates additional rails. Should return false to stop navigating the current
         * block section of track.
         *
         * @param event Event with current track state information
         * @param distance Distance navigated since the start of the block
         * @return True to continue navigating the current block, False to abort
         */
        boolean update(PathPredictEvent event, double distance);
    }
}
