package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Represents a series of points along which a minecart moves over the rails.
 * The points of this path are relative to the (0,0,0) of the rails block.
 * Paths are immutable to enable optimized performance for simple (two-point) paths.
 */
public class RailPath {
    public static final RailPath EMPTY = new RailPath(new Point[0]);
    private final Point[] points;
    private final Segment[] segments;
    private final double totalDistance;

    private RailPath(Point[] points) {
        this.points = points;
        if (points.length < 2) {
            this.segments = new Segment[0];
            this.totalDistance = 0.0;
        } else {
            double distance = 0.0;
            this.segments = new Segment[points.length - 1];
            for (int i = 0; i < this.segments.length; i++) {
                this.segments[i] = new Segment(points[i], points[i + 1]);
                distance += this.segments[i].l;
            }
            this.totalDistance = distance;
        }
    }

    /**
     * Gets the total distance of this rail path
     * 
     * @return total distance
     */
    public double getTotalDistance() {
        return this.totalDistance;
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
     * Gets all the segments of point pairs part of the path
     * 
     * @return segments
     */
    public Segment[] getSegments() {
        return this.segments;
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
     * The input absolute position and direction vector are updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * This move function allows absolute position calculations, by also specifying the rails block.
     * 
     * @param absolutePosition in world coordinates
     * @param direction of movement along this path
     * @param railsBlock relative to which is moved
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(Vector absolutePosition, Vector direction, Block railsBlock, double distance) {
        absolutePosition.setX(absolutePosition.getX() - railsBlock.getX());
        absolutePosition.setY(absolutePosition.getY() - railsBlock.getY());
        absolutePosition.setZ(absolutePosition.getZ() - railsBlock.getZ());
        double result = move(absolutePosition, direction, distance);
        absolutePosition.setX(absolutePosition.getX() + railsBlock.getX());
        absolutePosition.setY(absolutePosition.getY() + railsBlock.getY());
        absolutePosition.setZ(absolutePosition.getZ() + railsBlock.getZ());
        return result;
    }

    /**
     * Moves along this rail path for a certain distance in the direction specified.
     * The input position and direction vector are updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * 
     * @param position relative to the rails
     * @param direction of movement along this path
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(Vector position, Vector direction, double distance) {
        // If no segments are known, we can't move
        if (this.segments.length == 0) {
            return 0.0;
        }

        if (this.segments.length == 1) {
            // If only a single segment exists, take a shortcut here
            // We don't need to calculate the distance, only theta is relevant
            // All we do here is move along the one segment we have
            Segment s = this.segments[0];

            // Zero length segments allow no movement
            // All we do here is snap onto the point
            // The direction remains unchanged
            if (s.isZeroLength()) {
                s.p0.toVector(position);
                return 0.0;
            }

            double theta = s.calcTheta(position);
            s.calcPosition(position, theta);
            int order = s.calcDirection(direction, position);
            if (order == 1) {
                // p0 -> p1
                if (theta >= 1.0) {
                    return 0.0; // Nothing to move!
                } else if (theta < 0.0) {
                    theta = 0.0;
                }

                // Perform the movement
                double remainingDistance = s.l * (1.0 - theta);
                if (distance >= remainingDistance) {
                    s.p1.toVector(position);
                    return remainingDistance;
                } else {
                    s.calcPosition(position, theta + (distance / s.l));
                    return distance;
                }
            } else {
                // p1 -> p0
                if (theta <= 0.0) {
                    return 0.0; // Nothing to move!
                } else if (theta > 1.0) {
                    theta = 1.0;
                }

                // Perform the movement
                double remainingDistance = s.l * theta;
                if (distance >= remainingDistance) {
                    s.p0.toVector(position);
                    return remainingDistance;
                } else {
                    s.calcPosition(position, theta - (distance / s.l));
                    return distance;
                }
            }

        } else {
            // Find the start segment closest to the position
            double theta = 0.0;
            Segment s = null;
            int segmentIndex = -1;
            {
                double closestDistance = Double.MAX_VALUE;
                for (int i = 0; i < this.segments.length; i++) {
                    Segment tmpSegment = this.segments[i];
                    if (tmpSegment.isZeroLength()) continue;
                    double tmpTheta = tmpSegment.calcTheta(position);
                    double tmpDistSquared = tmpSegment.calcDistanceSquared(position, tmpTheta);
                    if (tmpDistSquared < closestDistance) {
                        closestDistance = tmpDistSquared;
                        theta = tmpTheta;
                        s = tmpSegment;
                        segmentIndex = i;
                    }
                }
                if (s == null) {
                    return 0.0;
                }
            }

            // If no distance to move, only snap to the rails and refresh direction
            if (distance <= 0.0) {
                s.calcPosition(position, theta);
                s.calcDirection(direction, position);
                return 0.0;
            }

            // Iterate the segments in order
            int order = s.calcDirection(direction, position);
            double moved = 0.0;
            while (distance > 0.0) {
                s.calcPosition(position, theta);
                if (!s.isZeroLength()) {
                    if (order == 1) {
                        // p0 -> p1
                        if (theta < 0.0) theta = 0.0;
                        if (theta < 1.0) {
                            // Perform the movement
                            double remainingDistance = s.l * (1.0 - theta);
                            if (distance >= remainingDistance) {
                                s.p1.toVector(position);
                                moved += remainingDistance;
                                distance -= remainingDistance;
                            } else {
                                s.calcPosition(position, theta + (distance / s.l));
                                moved += distance;
                                distance = 0.0;
                                break;
                            }
                        }
                    } else {
                        // p1 -> p0
                        if (theta > 1.0) theta = 1.0;
                        if (theta > 0.0) {
                            // Perform the movement
                            double remainingDistance = s.l * theta;
                            if (distance >= remainingDistance) {
                                s.p0.toVector(position);
                                moved += remainingDistance;
                                distance -= remainingDistance;
                            } else {
                                s.calcPosition(position, theta - (distance / s.l));
                                moved += distance;
                                distance = 0.0;
                                break;
                            }
                        }
                    }
                }

                // Move to the next segment
                segmentIndex += order;
                if (segmentIndex < 0 || segmentIndex >= this.segments.length) {
                    break;
                } else {
                    s = this.segments[segmentIndex];
                    theta = s.calcTheta(position);
                    s.calcDirection(direction, position);
                }
            }
            return moved;
        }
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
         * Gets the squared distance between this path point and a vector position
         * 
         * @param position
         * @return distance squared
         */
        public double distanceSquared(Vector position) {
            double dx = (position.getX() - this.x);
            double dy = (position.getY() - this.y);
            double dz = (position.getZ() - this.z);
            return dx * dx + dy * dy + dz * dz;
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
         * Assigns this Point to a Vector
         * 
         * @param v vector to assign to
         */
        public final void toVector(Vector v) {
            v.setX(this.x);
            v.setY(this.y);
            v.setZ(this.z);
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

    /**
     * A linear segment between two points
     */
    public static class Segment {
        public final Point p0;
        public final Point p1;
        public final Point dt;
        public final Point dt_norm;
        public final double l;
        public final double ls;

        public Segment(Point p0, Point p1) {
            this.p0 = p0;
            this.p1 = p1;
            this.dt = new Point(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
            this.ls = MathUtil.lengthSquared(this.dt.x, this.dt.y, this.dt.z);
            this.l = Math.sqrt(this.ls);
            if (this.isZeroLength()) {
                this.dt_norm = new Point(0.0, 0.0, 0.0);
            } else {
                this.dt_norm = new Point(this.dt.x / this.l, this.dt.y / this.l, this.dt.z / this.l);
            }
        }

        /**
         * Gets whether this segment is a zero-length vector
         * 
         * @return True if this segment is zero-length
         */
        public final boolean isZeroLength() {
            return this.l <= 0.00001;
        }

        /**
         * Gets a location on this segment using a known rails block and theta (0.0 ... 1.0)
         * 
         * @param railsBlock
         * @param theta
         * @return location on the segment
         */
        public final Location getLocation(Block railsBlock, double theta) {
            return new Location(
                    railsBlock.getWorld(),
                    railsBlock.getX() + p0.x + dt.x * theta,
                    railsBlock.getY() + p0.y + dt.y * theta,
                    railsBlock.getZ() + p0.z + dt.z * theta);
        }

        /**
         * Calculates the direction best matching this segment, using vector dot product.
         * Returns an integer representing the order in which segments should be iterated.
         * A value of 1 indicates p0 -> p1 (increment segments) and -1 indicates p1 -> p0
         * (decrement segments).
         * 
         * @param direction vector, is assigned the direction of this segment that best matches
         * @param position information, which acts as a fallback in case a 90-degree angle is encountered
         * @return order (-1 for decrement, 1 for increment)
         */
        public final int calcDirection(Vector direction, Vector position) {
            double dot = (direction.getX() * this.dt_norm.x) +
                         (direction.getY() * this.dt_norm.y) +
                         (direction.getZ() * this.dt_norm.z);

            // Hitting the segment at a 90-degree angle
            // This means the path direction cannot be easily assessed from the direction
            // Assume the direction goes from the point closest to the point farthest
            // TODO: South-west rule or something?
            if (dot <= 0.0000001 && dot >= -0.0000001) {
                dot = this.p1.distanceSquared(position) - this.p0.distanceSquared(position);
            }

            if (dot >= 0.0) {
                direction.setX(this.dt_norm.x);
                direction.setY(this.dt_norm.y);
                direction.setZ(this.dt_norm.z);
                return 1;
            } else {
                direction.setX(-this.dt_norm.x);
                direction.setY(-this.dt_norm.y);
                direction.setZ(-this.dt_norm.z);
                return -1;
            }
        }

        /**
         * Calculates the squared distance between an arbitrary point and this segment.
         * 
         * @param position relative to the rails
         * @return distance squared
         */
        public final double calcDistanceSquared(Vector position) {
            return calcDistanceSquared(position, calcTheta(position));
        }

        /**
         * Calculates the squared distance between an arbitrary point and this segment.
         * The theta value from {@link #calcTheta(double, double, double)} can be reused here.
         * 
         * @param position relative to the rails
         * @param theta (0.0 ... 1.0)
         * @return distance squared
         */
        public final double calcDistanceSquared(Vector position, double theta) {
            Vector segmentPosition = new Vector();
            calcPosition(segmentPosition, theta);
            segmentPosition.subtract(position);
            return segmentPosition.lengthSquared();
        }

        /**
         * Calculates the squared distance between an arbitrary point and this segment.
         * 
         * @param x - coordinate
         * @param y - coordinate
         * @param z - coordinate
         * @return distance squared
         */
        public final double calcDistanceSquared(double x, double y, double z) {
            return calcDistanceSquared(x, y, z, calcTheta(x, y, z));
        }

        /**
         * Calculates the squared distance between an arbitrary point and this segment.
         * The theta value from {@link #calcTheta(double, double, double)} can be reused here.
         * 
         * @param x - coordinate
         * @param y - coordinate
         * @param z - coordinate
         * @param theta (0.0 ... 1.0)
         * @return distance squared
         */
        public final double calcDistanceSquared(double x, double y, double z, double theta) {
            double dx, dy, dz;
            if (theta <= 0.0) {
                dx = p0.x;
                dy = p0.y;
                dz = p0.z;
            } else if (theta >= 1.0) {
                dx = p1.x;
                dy = p1.y;
                dz = p1.z;
            } else {
                dx = (p0.x + dt.x * theta);
                dy = (p0.y + dt.y * theta);
                dz = (p0.z + dt.z * theta);
            }
            dx -= x; dy -= y; dz -= z;
            dx *= dx;
            dy *= dy;
            dz *= dz;
            return dx + dy + dz;
        }

        /**
         * Assigns the x/y/z position to the vector based on theta,
         * where the theta is clamped between 0.0 and 1.0
         * 
         * @param position where the result is assigned to
         * @param theta (0.0 ... 1.0)
         */
        public void calcPosition(Vector position, double theta) {
            if (theta <= 0.0) {
                p0.toVector(position);
            } else if (theta >= 1.0) {
                p1.toVector(position);
            } else {
                position.setX(p0.x + dt.x * theta);
                position.setY(p0.y + dt.y * theta);
                position.setZ(p0.z + dt.z * theta);
            }
        }

        /**
         * Calculates the theta (0.0 ... 1.0) for the point
         * on this segment closest to a particular coordinate.
         * Theta results lower than 0 or higher than 1 indicate
         * a point that is beyond the ends of the segment.
         * 
         * @param position relative to the rails
         * @return theta
         */
        public final double calcTheta(Vector position) {
            return calcTheta(position.getX(), position.getY(), position.getZ());
        }

        /**
         * Calculates the theta (0.0 ... 1.0) for the point
         * on this segment closest to a particular coordinate.
         * Theta results lower than 0 or higher than 1 indicate
         * a point that is beyond the ends of the segment.
         * 
         * @param x - coordinate
         * @param y - coordinate
         * @param z - coordinate
         * @return theta
         */
        public final double calcTheta(double x, double y, double z) {
            return -(((p0.x - x) * dt.x + (p0.y - y) * dt.y + (p0.z - z) * dt.z) / ls);
        }
    }
}
