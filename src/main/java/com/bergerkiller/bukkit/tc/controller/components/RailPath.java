package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Represents a series of points along which a minecart moves over the rails.
 * The points of this path are relative to the middle of the rails block. (x/y/z + 0.5).
 * Paths are immutable to enable optimized performance for simple (two-point) paths.
 */
public class RailPath {
    private final Point[] points;

    private RailPath(Point[] points) {
        this.points = points;
    }

    /**
     * Moves along this rail path for a certain distance in the direction specified.
     * The input position and direction vector are updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * 
     * @param railsPosition
     * @param position
     * @param direction
     * @param distance
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(IntVector3 railsPosition, Vector position, Vector direction, double distance) {
        return 0.0;
    }

    public static RailPath create(Point... points) {
        if (points.length < 2) {
            throw new IllegalArgumentException("Paths must have at least 2 points");
        }
        return new RailPath(points);
    }
    
    /**
     * A single point on the path
     */
    public static class Point {
        public final double dx, dy, dz;
        
        public Point(double dx, double dy, double dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
