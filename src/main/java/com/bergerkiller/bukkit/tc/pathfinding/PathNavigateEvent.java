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
public interface PathNavigateEvent {

    /**
     * Constructs a new PathNavigateEvent.
     * Must call {@link #resetToInitialState(RailState, RailPath, double)} to set it up
     * before using as an event.
     */
    static PathNavigateEvent createNew() {
        return new PathNavigateEventBaseImpl();
    }

    /**
     * Resets this event, so that it can be used again for a new rail position
     *
     * @param railState Current Rail State for the event
     * @param railPath Current Rail Path for the event
     * @param currentDistance Current distance traveled so far using the track walking point
     */
    void resetToInitialState(RailState railState, RailPath railPath, double currentDistance);

    /**
     * Gets the current distance the walking point has traveled since the start
     *
     * @return Travel distance
     */
    double currentDistance();

    /**
     * Gets whether {@link #abortNavigation()} was called. Mostly internal use.
     *
     * @return True if navigation is aborted
     */
    boolean isNavigationAborted();

    /**
     * Aborts any further navigation of the track walking point. This exits navigation early,
     * causing {@link TrackWalkingPoint#moveFull()} to return false.
     */
    void abortNavigation();

    /**
     * Marks the current rail block movement as a blocked movement.
     * Path finding will abort at this point and consider the rest
     * of the track ahead unreachable.<br>
     * <br>
     * Underlying it just calls {@link #abortNavigation()} to stop walking
     * the track
     *
     * @see #abortNavigation()
     */
    default void setBlocked() {
        this.abortNavigation();
    }

    /**
     * Gets whether {@link #setBlocked()} was called for this rail
     *
     * @return True if blocked
     * @see #isNavigationAborted()
     */
    default boolean isBlocked() {
        return this.isNavigationAborted();
    }

    /**
     * Gets the exact rail block, type, position and direction
     * currently on the track.
     *
     * @return rail state details. Is never null.
     */
    RailState railState();

    /**
     * Gets the rail path currently being moved over
     *
     * @return rail path details
     */
    RailPath railPath();

    /**
     * Gets the RailPiece of the current track. This stores the rail
     * type and block, and other information about a piece of rails.
     *
     * @return rail piece
     */
    default RailPiece railPiece() {
        return railState().railPiece();
    }

    /**
     * Gets the exact rail block the path finder is currently on
     *
     * @return rail block
     */
    default Block railBlock() {
        return railState().railBlock();
    }

    /**
     * Gets the World this event is for
     *
     * @return rail world
     */
    default World railWorld() {
        return railState().railWorld();
    }

    /**
     * Gets the next rail path end-position of the current rail block
     * that it should navigate to. Is null if unset and following the
     * default path.
     *
     * @return next rail path position, null if unset
     */
    RailPath.Position getSwitchedPosition();

    /**
     * Gets whether a switched position was set
     *
     * @return True if a switched position was set
     * @see #setSwitchedPosition(RailPath.Position)
     */
    boolean hasSwitchedPosition();

    /**
     * Sets the rail path end-position of the current rail block that should be
     * navigated to.
     *
     * @param nextPosition Next rail path position
     */
    void setSwitchedPosition(RailPath.Position nextPosition);

    /**
     * Sets the rail junction of the current rail block that should be
     * navigated to.
     *
     * @param junction Junction to take and whose end-position to navigate to
     */
    default void setSwitchedJunction(RailJunction junction) {
        setSwitchedPosition(junction.position());
    }
}
