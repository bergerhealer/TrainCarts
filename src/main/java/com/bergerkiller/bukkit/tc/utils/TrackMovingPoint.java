package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

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
    private Set<Block> loopFilter = null;

    /**
     * Constructs a new Track Moving Point using the track position
     * and initial direction specified
     *
     * @param startBlock     of the rail to start moving from
     * @param startDirection to start moving into
     */
    public TrackMovingPoint(Block startBlock, BlockFace startDirection) {
        this.currentTrack = this.nextTrack = startBlock;
        this.currentDirection = this.nextDirection = startDirection;
        this.hasNext = false;
        if (this.nextTrack == null || this.nextDirection == null) {
            return;
        }
        RailType type = RailType.getType(this.nextTrack);
        if (type != null) {
            this.current = this.next = type.findMinecartPos(this.nextTrack);
            this.currentRail = this.nextRail = type;
            this.hasNext = true;
        }
    }

    /**
     * Sets whether a loop filter is employed, which tracks the positions already returned
     * and stops iteration when encountering a block already visited.
     * 
     * @param enabled
     */
    public void setLoopFilter(boolean enabled) {
        this.loopFilter = enabled ? new HashSet<Block>() : null;
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
        this.next = this.currentRail.getNextPos(this.currentTrack, this.currentDirection);
        if (this.next != null && this.loopFilter != null && !this.loopFilter.add(this.next)) {
            this.next = null; // Loop Detected
        }
        if (this.next == null) {
            // No next position available
            return;
        }

        // Re-calculate the direction we are moving
        if (this.current.getX() == this.next.getX() && this.current.getZ() == this.next.getZ()) {
            // Moving vertically
            this.nextDirection = FaceUtil.getVertical(this.next.getY() > this.current.getY());
        } else {
            // Moving horizontally
            this.nextDirection = FaceUtil.getDirection(this.current, this.next, false);
        }

        // Figure out what kind of rail is stored at the next Block
        RailInfo info = RailType.findRailInfo(this.next);
        if (info != null) {
            // Found a next track!
            this.nextTrack = info.railBlock;
            this.next = info.railType.findMinecartPos(this.nextTrack);
            this.nextRail = info.railType;
            this.hasNext = true;
        }
    }
}
