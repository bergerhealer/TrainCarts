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
    private int bdx, bdy, bdz;
    private double remaining, min;
    private boolean first;

    /**
     * Initializes a new BlockIterator for iterating a full segment length
     * 
     * @param segment  The rail path segment to iterate fully
     * @param rails    The rails offset for the rail path segment
     */
    public BlockIterator(RailPath.Segment segment, IntVector3 rails) {
        this(rails.x + segment.p0.x,
             rails.y + segment.p0.y,
             rails.z + segment.p0.z,
             segment.mot.getX(),
             segment.mot.getY(),
             segment.mot.getZ(),
             segment.l);
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
        this.bx = MathUtil.floor(x);
        this.by = MathUtil.floor(y);
        this.bz = MathUtil.floor(z);
        this.px = x - this.bx;
        this.py = y - this.by;
        this.pz = z - this.bz;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.remaining = distance;
        this.min = Double.MAX_VALUE;
        this.first = true;
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
