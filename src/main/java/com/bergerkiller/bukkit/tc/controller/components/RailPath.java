package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Represents a series of points along which a minecart moves over the rails.
 * The points of this path are relative to the (0,0,0) of the rails block.
 * Paths are immutable to enable optimized performance for simple (two-point) paths.
 */
public class RailPath {
    public static final RailPath EMPTY = new RailPath(new Point[0]);
    private final Point[] points;

    private RailPath(Point[] points) {
        this.points = points;
    }

    /**
     * Gets all the sequential points part of the path
     * 
     * @return path points
     */
    public Point[] getPoints() {
        return this.points;
    }

    /**
     * Gets whether this Rail Path is empty. An empty rail path offers no
     * point information, essentially allowing free movement through the space.
     * 
     * @return True if empty
     */
    public boolean isEmpty() {
        return this.points.length == 0;
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

    public static RailPath create(Vector... pointVectors) {
        Point[] points = new Point[pointVectors.length];
        for (int i = 0; i < pointVectors.length; i++) {
            Vector v = pointVectors[i];
            points[i] = new Point(v.getX(), v.getY(), v.getZ());
        }
        return create(points);
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
        public final double x, y, z;

        public Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * Turns this immutable Point into a Vector
         * 
         * @return Vector
         */
        public final Vector toVector() {
            return new Vector(this.x, this.y, this.z);
        }

        /**
         * Gets the world location of this point for a rails block
         * 
         * @param railsBlock
         * @return location
         */
        public final Location getLocation(Block railsBlock) {
            return new Location(
                    railsBlock.getWorld(),
                    railsBlock.getX() + this.x,
                    railsBlock.getY() + this.y,
                    railsBlock.getZ() + this.z);
        }
    }
}
