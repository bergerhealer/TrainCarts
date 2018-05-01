package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.tc.rails.type.RailType;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.NoSuchElementException;

/**
 * Represents a Minecart moving from Block to Block.
 * The current Minecart Block position and track position is maintained.
 */
public class TrackMovingPoint {
    public Block current, next;
    public Block currentTrack, nextTrack;
    public BlockFace currentDirection, nextDirection;
    public RailType currentRail, nextRail;
    private boolean hasNext;
    private final TrackWalkingPoint walkingPoint;

    /**
     * Constructs a new Track Moving Point using the initial Minecart position
     * and initial motion vector direction specified
     * 
     * @param startPos of the Minecart
     * @param motionVector to start moving into
     */
    public TrackMovingPoint(Location startPos, Vector motionVector) {
        this(new TrackWalkingPoint(startPos, motionVector));
    }

    /**
     * Constructs a new Track Moving Point using the track position
     * and initial direction specified
     *
     * @param startBlock     of the rail to start moving from
     * @param startDirection to start moving into
     */
    @Deprecated
    public TrackMovingPoint(Block startBlock, BlockFace startDirection) {
        this(new TrackWalkingPoint(startBlock, startDirection));
    }

    private TrackMovingPoint(TrackWalkingPoint walkingPoint) {
        this.walkingPoint = walkingPoint;
        if (this.walkingPoint.state.railType() != RailType.NONE) {
            this.currentTrack = this.nextTrack = this.walkingPoint.state.railBlock();
            this.currentDirection = this.nextDirection = this.walkingPoint.state.enterFace();
            this.current = this.next = this.walkingPoint.state.positionBlock();
            this.currentRail = this.nextRail = this.walkingPoint.state.railType();
            this.hasNext = true;
        } else {
            this.currentTrack = this.nextTrack = null;
            this.currentDirection = this.nextDirection = BlockFace.SELF;
            this.current = this.next = null;
            this.currentRail = this.nextRail = RailType.NONE;
            this.hasNext = false;
        }
    }

    /**
     * Sets whether a loop filter is employed, which tracks the positions already returned
     * and stops iteration when encountering a block already visited.
     * 
     * @param enabled
     */
    public void setLoopFilter(boolean enabled) {
        this.walkingPoint.setLoopFilter(enabled);
    }

    /**
     * Whether next track information is available
     *
     * @return True if available, False if not
     */
    public boolean hasNext() {
        return hasNext;
    }

    /**
     * Clears the next track information. After calling this method,
     * {@link #hasNext()} will return False and {@link #next()} will
     * no longer succeed.
     */
    public void clearNext() {
        this.hasNext = false;
    }

    /**
     * Loads in the next track information. If no next information
     * is available, this method throws an exception. Use
     * {@link #hasNext()} to check whether next track information is available.
     */
    public void next() {
        next(true);
    }

    /**
     * Loads in the next track information. If no next information
     * is available, this method throws an exception. Use
     * {@link #hasNext()} to check whether next track information is available.
     *
     * @param allowNext - True to allow future next() calls, False to make this one the last
     */
    public void next(boolean allowNext) {
        if (!hasNext()) {
            throw new NoSuchElementException("No next element is available");
        }

        // Store the currently returned next track information
        this.current = this.next;
        this.currentTrack = this.nextTrack;
        this.currentDirection = this.nextDirection;
        this.currentRail = this.nextRail;
        this.hasNext = false;

        // If requested, do not attempt to generate another next one
        if (!allowNext) {
            return;
        }

        // Use the current rail to obtain the next Block to go to
        if (!this.walkingPoint.moveFull()) {
            // No next position available
            return;
        }

        this.next = this.walkingPoint.state.positionBlock();
        this.nextTrack = this.walkingPoint.state.railBlock();
        this.nextRail = this.walkingPoint.state.railType();
        this.nextDirection = this.walkingPoint.state.enterFace();
        this.hasNext = true;
    }

}
