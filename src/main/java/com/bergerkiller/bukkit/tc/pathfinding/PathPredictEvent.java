package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
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
public class PathPredictEvent extends PathNavigateEvent {
    private final PathProvider provider;
    private final MinecartMember<?> member;
    private double speedLimit;
    private List<ActiveBlockHandler> newBlockHandlers = Collections.emptyList();

    public PathPredictEvent(PathProvider provider, MinecartMember<?> member) {
        this.provider = provider;
        this.member = member;
        this.speedLimit = Double.MAX_VALUE;
    }

    @Override
    public void resetToInitialState(RailState railState, RailPath railPath, double currentDistance) {
        super.resetToInitialState(railState, railPath, currentDistance);
        this.setSpeedLimit(Double.MAX_VALUE);
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
        RailState state = this.railState();
        return this.provider.getWorld(state.railWorld())
                            .getNodeAtRail(state.railBlock());
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
     * @param token Unique token for this handler. This ensures that if the same object
     *              is encountered twice, no infinite loop can occur
     * @param maxDistance Maximum distance the block may contain
     */
    public void trackBlock(BlockHandler handler, Object token, double maxDistance) {
        if (newBlockHandlers.isEmpty()) {
            newBlockHandlers = new ArrayList<>();
        }
        newBlockHandlers.add(new ActiveBlockHandler(handler, token, maxDistance));
    }

    public boolean hasNewBlockTrackers() {
        return !newBlockHandlers.isEmpty();
    }

    public List<ActiveBlockHandler> getNewBlockTrackers() {
        return newBlockHandlers;
    }

    public static class ActiveBlockHandler {
        public final BlockHandler handler;
        public final Object token;
        public final double maxDistance;

        public ActiveBlockHandler(BlockHandler handler, Object token, double maxDistance) {
            this.handler = handler;
            this.token = token;
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
