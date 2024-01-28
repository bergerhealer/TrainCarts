package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Iterates the full-block positions encountered when moving from
 * a start x/y/z double position into a given direction. Every block
 * encountered within the distance limit is iterated over.
 */
public class BlockIterator {
    private double dx, dy, dz;
    private double px, py, pz;
    private int bx, by, bz;
    private double remaining;
    private boolean first;

    // If ending position is at a block coordinate, these coordinates specify this
    // to skip that one.
    private int skipEndX, skipEndY, skipEndZ;

    // These are reused inside next() and do not need to be initialized
    private int bdx, bdy, bdz;
    private double min;

    /**
     * Non-initialized block iterator. Call reset() first.
     */
    public BlockIterator() {
    }

    /**
     * Initializes a new BlockIterator for iterating a full segment length.
     * The start and end positions, if they are at the block's edge, are not
     * included.
     *
     * @param rails    The rails offset for the rail path segment
     * @param segment  The rail path segment to iterate fully
     */
    public BlockIterator(IntVector3 rails, RailPath.Segment segment) {
        reset(rails, segment);
    }

    /**
     * Initializes a new BlockIterator
     * 
     * @param loc  The starting coordinates
     * @param direction  The direction vector
     * @param distance  The distance before next() returns false
     */
    public BlockIterator(Location loc, Vector direction, double distance) {
        this(loc.getX(), loc.getY(), loc.getZ(), direction.getX(), direction.getY(), direction.getZ(), distance);
    }

    /**
     * Initializes a new BlockIterator
     *
     * @param x   The starting x-coordinate
     * @param y   The starting y-coordinate
     * @param z   The starting z-coordinate
     * @param dx  The direction vector x-coordinate
     * @param dy  The direction vector y-coordinate
     * @param dz  The direction vector z-coordinate
     * @param distance  The distance before next() returns false
     */
    public BlockIterator(double x, double y, double z, double dx, double dy, double dz, double distance) {
        IntVector3 rails = IntVector3.blockOf(x, y, z);
        reset(rails, x - rails.x, y - rails.y, z - rails.z, dx, dy, dz, distance);
    }

    /**
     * Initializes a new BlockIterator
     *
     * @param rails Block coordinates relative to which the x/y/z are
     * @param x   The starting x-coordinate
     * @param y   The starting y-coordinate
     * @param z   The starting z-coordinate
     * @param dx  The direction vector x-coordinate
     * @param dy  The direction vector y-coordinate
     * @param dz  The direction vector z-coordinate
     * @param distance  The distance before next() returns false
     */
    public BlockIterator(IntVector3 rails, double x, double y, double z, double dx, double dy, double dz, double distance) {
        reset(rails, x, y, z, dx, dy, dz, distance);
    }

    /**
     * Resets this iterator to start iterating again from the starting position and direction specified.
     *
     * @param rails Block coordinates relative to which the x/y/z are
     * @param x   The starting x-coordinate
     * @param y   The starting y-coordinate
     * @param z   The starting z-coordinate
     * @param dx  The direction vector x-coordinate
     * @param dy  The direction vector y-coordinate
     * @param dz  The direction vector z-coordinate
     * @param distance  The distance before next() returns false
     */
    public void reset(IntVector3 rails, double x, double y, double z, double dx, double dy, double dz, double distance) {
        int floor_x = MathUtil.floor(x);
        int floor_y = MathUtil.floor(y);
        int floor_z = MathUtil.floor(z);

        this.bx = rails.x + floor_x;
        this.by = rails.y + floor_y;
        this.bz = rails.z + floor_z;
        this.px = x - floor_x;
        this.py = y - floor_y;
        this.pz = z - floor_z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.skipEndX = Integer.MIN_VALUE;
        this.skipEndY = Integer.MIN_VALUE;
        this.skipEndZ = Integer.MIN_VALUE;
        this.remaining = distance;
        this.first = true;
    }

    /**
     * Initializes the BlockIterator for iterating a full segment length.
     * The start and end positions, if they are at the block's edge, are not
     * included.
     *
     * @param segment  The rail path segment to iterate fully
     * @param rails    The rails offset for the rail path segment
     */
    public void reset(IntVector3 rails, RailPath.Segment segment) {
        this.reset(rails,
                   segment.p0.x, segment.p0.y, segment.p0.z,
                   segment.mot.getX(), segment.mot.getY(), segment.mot.getZ(),
                   segment.l);

        // If s_rel_bx is at exactly the same coordinate as p0, and the delta is negative,
        // skip the first block. Same for y/z.
        int s_rel_bx = bx - rails.x;
        int s_rel_by = by - rails.y;
        int s_rel_bz = bz - rails.z;
        if (
                (s_rel_bx == segment.p0.x && segment.mot.getX() < 0.0) ||
                (s_rel_by == segment.p0.y && segment.mot.getY() < 0.0) ||
                (s_rel_bz == segment.p0.z && segment.mot.getZ() < 0.0)
        ) {
            this.first = false;
        }

        // If p1 is at exactly a block coordinate, and the delta is negative,
        // skip the last block. Same for y/z.
        // Because of the unpredictability of the distance-based limit, we specify
        // the exact block to skip rather than to "skip the last value".
        // This avoids weirdness.
        int e_rel_bx = MathUtil.floor(segment.p1.x);
        int e_rel_by = MathUtil.floor(segment.p1.y);
        int e_rel_bz = MathUtil.floor(segment.p1.z);
        boolean e_beyond_x = (e_rel_bx == segment.p1.x);
        boolean e_beyond_y = (e_rel_by == segment.p1.y);
        boolean e_beyond_z = (e_rel_bz == segment.p1.z);
        if (e_beyond_x || e_beyond_y || e_beyond_z) {
            skipEndX = rails.x + e_rel_bx;
            skipEndY = rails.y + e_rel_by;
            skipEndZ = rails.z + e_rel_bz;

            if (e_beyond_x && segment.mot.getX() < 0.0) {
                skipEndX--;
            }
            if (e_beyond_y && segment.mot.getY() < 0.0) {
                skipEndY--;
            }
            if (e_beyond_z && segment.mot.getZ() < 0.0) {
                skipEndZ--;
            }
        }
    }

    /**
     * Gets the IntVector3 Block Coordinates for the block. Call {@link #next()} first.
     * 
     * @return block
     */
    public IntVector3 block() {
        return new IntVector3(bx, by, bz);
    }

    /**
     * Moves to the next block. The first time this is called, the start position
     * block is specified. When this method returns true, then a next block is available,
     * which can be retrieved using {@link #block()}. If this returns false, the end is reached.
     * 
     * @return True if a next block is available
     */
    public boolean next() {
        // Return the first block at the start position
        if (this.first) {
            this.first = false;
            return true;
        }

        this.min = Double.MAX_VALUE;

        // Check move distance till x-edge of block
        if (this.dx > 1e-10) {
            add((1.0 - this.px) / this.dx, 1, 0, 0);
        } else if (this.dx < -1e-10) {
            add(this.px / -this.dx, -1, 0, 0);
        }

        // Check move distance till y-edge of block
        if (this.dy > 1e-10) {
            add((1.0 - this.py) / this.dy, 0, 1, 0);
        } else if (this.dy < -1e-10) {
            add(this.py / -this.dy, 0, -1, 0);
        }

        // Check move distance till z-edge of block
        if (this.dz > 1e-10) {
            add((1.0 - this.pz) / this.dz, 0, 0, 1);
        } else if (this.dz < -1e-10) {
            add(this.pz / -this.dz, 0, 0, -1);
        }

        // If exceeding remaining track, abort
        if (this.min > this.remaining) {
            return false;
        }

        // Move to next block
        this.remaining -= this.min;
        this.px += this.dx * this.min;
        this.py += this.dy * this.min;
        this.pz += this.dz * this.min;
        this.bx += this.bdx;
        this.by += this.bdy;
        this.bz += this.bdz;
        this.px -= this.bdx;
        this.py -= this.bdy;
        this.pz -= this.bdz;

        // Skip if ignored end
        if (this.bx == skipEndX && this.by == skipEndY && this.bz == skipEndZ) {
            return false;
        }

        return true;
    }

    private void add(double value, int bdx, int bdy, int bdz) {
        if (value < this.min) {
            this.min = value;
            this.bdx = bdx;
            this.bdy = bdy;
            this.bdz = bdz;
        }
    }
}
