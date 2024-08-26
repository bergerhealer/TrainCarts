package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Base event for navigating the rails visited using a {@link TrackWalkingPoint}.
 * Provides information about the current rail visited and a way to override the next
 * position to navigate to.
 */
public class PathNavigateEvent {
    private final RailState railState;
    private RailPath railPath;
    private RailPath.Position nextPosition;
    private double currentDistance;
    private boolean abortNavigation;

    /**
     * Constructs a new PathNavigateEvent with the (mutable!) rail state specified.
     * Must call {@link #resetToInitialState(RailPath, double)} to set it up
     * before using as an event.
     *
     * @param railState Mutable RailState
     */
    public PathNavigateEvent(RailState railState) {
        this.railState = railState;
    }

    /**
     * Resets this event, so that it can be used again for a new rail position
     *
     * @param railPath Current Rail Path for the event
     * @param currentDistance Current distance traveled so far using the track walking point
     */
    public void resetToInitialState(RailPath railPath, double currentDistance) {
        this.railPath = railPath;
        this.nextPosition = null;
        this.currentDistance = currentDistance;
        this.abortNavigation = false;
    }

    /**
     * Gets the current distance the walking point has traveled since the start
     *
     * @return Travel distance
     */
    public double currentDistance() {
        return currentDistance;
    }

    /**
     * Gets whether {@link #abortNavigation()} was called. Mostly internal use.
     *
     * @return True if navigation is aborted
     */
    public boolean isNavigationAborted() {
        return abortNavigation;
    }

    /**
     * Aborts any further navigation of the track walking point. This exits navigation early,
     * causing {@link TrackWalkingPoint#moveFull()} to return false.
     */
    public void abortNavigation() {
        abortNavigation = true;
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
     * that it should navigate to. Is null if unset and following the
     * default path.
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
     * Sets the rail path end-position of the current rail block that should be
     * navigated to.
     *
     * @param nextPosition Next rail path position
     */
    public void setSwitchedPosition(RailPath.Position nextPosition) {
        this.nextPosition = nextPosition;
    }

    /**
     * Sets the rail junction of the current rail block that should be
     * navigated to.
     *
     * @param junction Junction to take and whose end-position to navigate to
     */
    public void setSwitchedJunction(RailJunction junction) {
        this.nextPosition = junction.position();
    }
}
