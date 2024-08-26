package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import org.bukkit.World;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

/**
 * A handler invoked for every Rail Block point on the path
 * encountered, where destinations/switchers are detected.
 * Custom handlers can be implemented to add pathfinding sign
 * behavior without actual physical signs.
 */
public interface PathRoutingHandler {

    /**
     * Main process function of the routing handler. Every rail block
     * visited by the path finding algorithm is passed by this method
     * of the handler.<br>
     * <br>
     * It is here new path nodes can be initialized using
     * {@link PathRouteEvent#createNode()}. Properties of this node,
     * such as whether it's a switcher or has certain destination names
     * associated, can then be setup. A graph for all these nodes
     * is automatically created.
     *
     * @param event
     */
    void process(PathRouteEvent event);

    /**
     * Called when a train is navigating track ahead to predict what
     * direction to send the train to on a switcher, and to predict
     * a speed limit for the train to use.<br>
     * <br>
     * For path nodes visited that are switched, implementers of that
     * switcher can let the train know where it will go. This will help
     * prevent deadlocks by accurately navigating the track, rather than
     * the current state the track happens to have.<br>
     * <br>
     * For speed traps and blocking movement in one direction, a speed
     * limit can be imposed so that the train will gradually slow down
     * to meet that speed, rather than abruptly stop.
     *
     * @param event
     */
    default void predict(PathPredictEvent event) {}

    /**
     * Event passed to {@link PathRoutingHandler#process(PathRouteEvent)}
     * to process path finding. The methods {@link #createNode()}
     * and {@link #setBlocked()} can be used to provide feedback
     * about what to do with the current rail.
     */
    class PathRouteEvent extends PathNavigateEventBaseImpl {
        private final PathProvider provider;
        private final PathWorld world;
        private PathNode nodeAtRail;

        public PathRouteEvent(PathProvider provider, World world) {
            this.provider = provider;
            this.world = provider.getWorld(world);
        }

        @Override
        public void resetToInitialState(RailState railState, RailPath railPath, double currentDistance) {
            super.resetToInitialState(railState, railPath, currentDistance);
            this.nodeAtRail = null;
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
         * Initializes a new node to assign to this rail block. The path finder
         * will re-search in all directions from this node to find other
         * routes.
         *
         * @return The existing or created PathNode for this rail block
         */
        public PathNode createNode() {
            if (this.nodeAtRail == null) {
                this.nodeAtRail = this.world.getOrCreateAtRail(new BlockLocation(railBlock()));
            }
            return this.nodeAtRail;
        }

        /**
         * Gets the last node set using {@link #createNode()}
         *
         * @return last created node
         */
        public PathNode getLastSetNode() {
            return this.nodeAtRail;
        }

        /**
         * Reads the stored information of this event and summarizes it with the
         * PathRailInfo state enum
         *
         * @return PathRailInfo
         */
        public PathRailInfo getRailInfo() {
            if (isBlocked()) {
                return PathRailInfo.BLOCKED;
            } else if (getLastSetNode() != null) {
                return PathRailInfo.NODE;
            } else {
                return PathRailInfo.NONE;
            }
        }
    }
}
