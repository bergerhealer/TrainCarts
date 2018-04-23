package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
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
            for (int i = 0; i < this.segments.length - 1; i++) {
                this.segments[i].next = this.segments[i + 1];
                this.segments[i + 1].prev = this.segments[i];
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
     * This is the case when the number of segments is 0.
     * 
     * @return True if empty
     */
    public boolean isEmpty() {
        return this.segments.length == 0;
    }

    /**
     * Finds the segment of this rail path that is used at the relative position specified
     * 
     * @param position (relative to rails block)
     * @return segment, null if this rail path has no segments
     */
    public Segment findSegment(Vector position) {
        if (this.segments.length == 0) {
            return null;
        } else if (this.segments.length == 1) {
            return this.segments[0];
        } else {
            // Find the start segment closest to the position
            Segment s = null;
            {
                double closestDistance = Double.MAX_VALUE;
                for (int i = 0; i < this.segments.length; i++) {
                    Segment tmpSegment = this.segments[i];
                    if (tmpSegment.isZeroLength()) continue;
                    double tmpTheta = tmpSegment.calcTheta(position);
                    double tmpDistSquared = tmpSegment.calcDistanceSquared(position, tmpTheta);
                    if (tmpDistSquared < closestDistance) {
                        closestDistance = tmpDistSquared;
                        s = tmpSegment;
                    }
                }
            }
            return s;
        }
    }

    /**
     * Finds the segment of this rail path that is used at the absolute position specified
     * 
     * @param position (absolute)
     * @param rails (relative to which this path exists)
     * @return segment, null if this rail path has no segments
     */
    public Segment findSegment(Vector position, Block rails) {
        if (this.segments.length == 0) {
            return null;
        } else if (this.segments.length == 1) {
            return this.segments[0];
        } else {
            Vector relPos = position.clone();
            relPos.setX(relPos.getX() - rails.getX());
            relPos.setY(relPos.getY() - rails.getY());
            relPos.setZ(relPos.getZ() - rails.getZ());
            return findSegment(relPos);
        }
    }

    /**
     * Moves along this rail path for a certain distance.
     * The input position information is updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * This move function allows absolute position calculations, by also specifying the rails block.
     * 
     * @param absolutePosition in world coordinates
     * @param orientation of the Minecart while moving along this path
     * @param railsBlock relative to which is moved
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(Position position, Block railsBlock, double distance) {
        position.posX -= railsBlock.getX();
        position.posY -= railsBlock.getY();
        position.posZ -= railsBlock.getZ();
        double result = moveRelative(position, distance);
        position.posX += railsBlock.getX();
        position.posY += railsBlock.getY();
        position.posZ += railsBlock.getZ();
        return result;
    }

    @Deprecated
    public double move(Vector position, Vector direction, Block railsBlock, double distance) {
        position.setX(position.getX() - railsBlock.getX());
        position.setY(position.getY() - railsBlock.getY());
        position.setZ(position.getZ() - railsBlock.getZ());
        double result = move(position, direction, distance);
        position.setX(position.getX() + railsBlock.getX());
        position.setY(position.getY() + railsBlock.getY());
        position.setZ(position.getZ() + railsBlock.getZ());
        return result;
    }

    @Deprecated
    public double move(Vector position, Vector direction, double distance) {
        Position tmp = new Position();
        tmp.posX = position.getX();
        tmp.posY = position.getY();
        tmp.posZ = position.getZ();
        tmp.motX = direction.getX();
        tmp.motY = direction.getY();
        tmp.motZ = direction.getZ();
        double result = this.moveRelative(tmp, distance);
        position.setX(tmp.posX);
        position.setY(tmp.posY);
        position.setZ(tmp.posZ);
        direction.setX(tmp.motX);
        direction.setY(tmp.motY);
        direction.setZ(tmp.motZ);
        return result;
    }

    private double moveRelative(Position position, double distance) {
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
                s.calcPosition(position, 0.0);
                return 0.0;
            }

            double theta = s.calcTheta(position);
            s.calcPosition(position, theta);
            int order = s.calcDirection(position);
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
                    s.calcPosition(position, 1.0);
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
                    s.calcPosition(position, 0.0);
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
                s.calcDirection(position);
                return 0.0;
            }

            // Iterate the segments in order
            int order = s.calcDirection(position);
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
                                s.calcPosition(position, 1.0);
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
                                s.calcPosition(position, 0.0);
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
                    s.calcDirection(position);
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
     * Takes a rail path and translates its position based on a vector offset.
     * The up vectors of the original path are left unaffected.
     * 
     * @param original_path to offset
     * @param position_offset to offset
     * @return original rail path offset by the position offset
     */
    public static RailPath offset(RailPath original_path, Vector position_offset) {
        if (original_path.isEmpty()) {
            return EMPTY;
        }
        RailPath.Point[] originalPoints = original_path.getPoints();
        RailPath.Point[] points_offset = new RailPath.Point[originalPoints.length];
        for (int i = 0; i < originalPoints.length; i++) {
            RailPath.Point original = originalPoints[i];
            points_offset[i] = new RailPath.Point(
                    original.x + position_offset.getX(),
                    original.y + position_offset.getY(),
                    original.z + position_offset.getZ(),
                    original.up_x, original.up_y, original.up_z
            );
        }
        return create(points_offset);
    }

    /**
     * Stores all the transformation information for a single position on the path.
     * This includes the absolute world-coordinates position, the direction
     * vector in which is moved, and the orientation 'normal' vector.
     */
    public static final class Position {
        /**
         * The position of the object in world-coordinates.
         */
        public double posX, posY, posZ;
        /**
         * The motion unit vector. This is the direction
         * in which the object moves forwards over the path.
         */
        public double motX, motY, motZ;
        /**
         * The orientation 'up' unit vector. This is the direction
         * the object's top-side faces while traveling over the rails.
         * It is interpolated and calculated from the rail path roll values.
         */
        public double upX, upY, upZ;
        /**
         * Whether we are walking the path in reverse. This is important
         * when applying the south-east rule when encountering an orthogonal
         * rail path intersection.
         */
        public boolean reverse = false;

        public Location toLocation(World world) {
            return new Location(world, posX, posY, posZ);
        }

        public BlockFace getMotionFace() {
            if (motX < -0.001 || motX > 0.001 || motZ < -0.001 || motZ > 0.001) {
                return FaceUtil.getDirection(motX, motZ, false);
            } else {
                return FaceUtil.getVertical(motY);
            }
        }

        public double motDot(Vector v) {
            return (motX * v.getX()) + (motY * v.getY()) + (motZ * v.getZ());
        }

        public double motDot(BlockFace face) {
            return (motX * face.getModX()) + (motY * face.getModY()) + (motZ * face.getModZ());
        }

        public double motDot(Point point) {
            return (motX * point.x) + (motY * point.y) + (motZ * point.z);
        }

        public double motDot(double dx, double dy, double dz) {
            return (motX * dx) + (motY * dy) + (motZ * dz);
        }

        public void setMotion(BlockFace movement) {
            this.motX = movement.getModX();
            this.motY = movement.getModY();
            this.motZ = movement.getModZ();
        }

        public static Position fromPosDir(Vector position, Vector direction) {
            Position p = new Position();
            p.posX = position.getX();
            p.posY = position.getY();
            p.posZ = position.getZ();
            p.motX = direction.getX();
            p.motY = direction.getY();
            p.motZ = direction.getZ();
            return p;
        }

        public static Position fromTo(Location from, Location to) {
            Position p = new Position();
            p.posX = from.getX();
            p.posY = from.getY();
            p.posZ = from.getZ();
            p.motX = to.getX() - p.posX;
            p.motY = to.getY() - p.posY;
            p.motZ = to.getZ() - p.posZ;
            return p;
        }
    }

    /**
     * A single point on the path
     */
    public static class Point {
        public final double x, y, z;
        /**
         * The up-vector for the Minecart at this point.
         * This is allowed to be an approximate, since it is automatically
         * calculated to be orthogonal to the direction vector. The up vector
         * also does not have to be a unit vector (length = 1).
         */
        public final double up_x, up_y, up_z;

        public Point(Vector v) {
            this(v.getX(), v.getY(), v.getZ());
        }

        public Point(Vector v, Vector up) {
            this(v.getX(), v.getY(), v.getZ(), up.getX(), up.getY(), up.getZ());
        }

        public Point(Vector v, double up_x, double up_y, double up_z) {
            this(v.getX(), v.getY(), v.getZ(), up_x, up_y, up_z);
        }

        public Point(Vector v, BlockFace face) {
            this(v.getX(), v.getY(), v.getZ(), face);
        }

        public Point(double x, double y, double z) {
            this(x, y, z, BlockFace.UP);
        }

        public Point(double x, double y, double z, BlockFace face) {
            this(x, y, z, face.getModX(), face.getModY(), face.getModZ());
        }

        public Point(double x, double y, double z, double up_x, double up_y, double up_z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.up_x = up_x;
            this.up_y = up_y;
            this.up_z = up_z;
        }

        /**
         * Gets whether this point is vertical, i.e. the x/z values are very close to 0
         * 
         * @return True if this point is vertical, where only the y-coordinate has meaning
         */
        public boolean isVertical() {
            final double EP = 0.00001;
            return this.x >= -EP && this.x <= EP && this.z >= -EP && this.z <= EP;
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
         * Gets the squared distance between this path point and a position
         * 
         * @param position
         * @return distance squared
         */
        public double distanceSquared(Position position) {
            double dx = (position.posX - this.x);
            double dy = (position.posY - this.y);
            double dz = (position.posZ - this.z);
            return dx * dx + dy * dy + dz * dz;
        }

        /**
         * Obtains the up-vector as a vector
         * 
         * @return Up Vector
         */
        public final Vector up() {
            return new Vector(this.up_x, this.up_y, this.up_z);
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

        @Override
        public String toString() {
            return "[v={" + this.x + "/" + this.y + "/" + this.z + "} " +
                   "up={" + this.up_x + "/" + this.up_y + "/" + this.up_z + "}]";
                       
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
        public final Point up0;
        public final Point up1;
        public final Point up_dt;
        public final double l;
        public final double ls;
        private Segment prev, next;

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

            // Uses the roll values of the points and the segment slope
            // to calculate the normal 'up' vector. This is the direction
            // upwards that the Minecart faces. This is especially important
            // for positions like upside-down, vertical, and twisted
            Vector dir = this.dt_norm.toVector();
            Vector up0 = dir.clone().crossProduct(this.p0.up()).crossProduct(dir).normalize();
            Vector up1 = dir.clone().crossProduct(this.p1.up()).crossProduct(dir).normalize();
            this.up0 = new Point(up0);
            this.up1 = new Point(up1);
            this.up_dt = new Point(this.up1.x - this.up0.x, this.up1.y - this.up0.y, this.up1.z - this.up0.z);
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

        private final int isHeadingToPrev(Position position) {
            if (this.prev != null) {
                double dot = position.motDot(this.prev.dt_norm);
                if (dot < -0.0000001) {
                    return 1;
                } else if (dot > 0.0000001) {
                    return -1;
                } else {
                    return this.prev.isHeadingToPrev(position);
                }
            } else {
                return 0;
            }
        }

        private final int isHeadingToNext(Position position) {
            if (this.next != null) {
                double dot = position.motDot(this.next.dt_norm);
                if (dot > 0.0000001) {
                    return 1;
                } else if (dot < -0.0000001) {
                    return -1;
                } else {
                    return this.next.isHeadingToNext(position);
                }
            } else {
                return 0;
            }
        }

        /**
         * Calculates the direction best matching this segment, using vector dot product.
         * Returns an integer representing the order in which segments should be iterated.
         * A value of 1 indicates p0 -> p1 (increment segments) and -1 indicates p1 -> p0
         * (decrement segments).
         * 
         * @param position, is assigned the direction and roll of this segment that best matches
         * @return order (-1 for decrement, 1 for increment)
         */
        public final int calcDirection(Position position) {
            double dot = position.motDot(this.dt_norm);

            // Hitting the segment at a 90-degree angle
            // This means the path direction cannot be easily assessed from the direction
            // Assume the direction goes from the point closest to the point farthest
            if (dot <= 1e-8 && dot >= -1e-8) {
                // Head-on: see if a previous or next segment knows our direction
                int order = this.isHeadingToPrev(position) - this.isHeadingToNext(position);
                if (order > 0) {
                    // Feedback from surrounding segments says go -1
                    dot = -1.0;
                } else if (order < 0) {
                    // Feedback from surrounding segments says go +1
                    dot = 1.0;
                } else {
                    dot = this.p1.distanceSquared(position) - this.p0.distanceSquared(position);
                    if (dot <= 1e-8 && dot >= -1e-8) {
                        //TODO: South-east rule
                    }

                    if (position.reverse) {
                        dot = -dot;
                    }
                }
            }

            if (dot >= 0.0) {
                position.motX = this.dt_norm.x;
                position.motY = this.dt_norm.y;
                position.motZ = this.dt_norm.z;
                return 1;
            } else {
                position.motX = -this.dt_norm.x;
                position.motY = -this.dt_norm.y;
                position.motZ = -this.dt_norm.z;
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
         * The theta value from {@link #calcTheta(double, double, double)} can be reused here.
         * 
         * @param position relative to the rails
         * @param theta (0.0 ... 1.0)
         * @return distance squared
         */
        public final double calcDistanceSquared(Position position, double theta) {
            Vector segmentPosition = new Vector();
            calcPosition(segmentPosition, theta);
            segmentPosition.setX(segmentPosition.getX() - position.posX);
            segmentPosition.setY(segmentPosition.getY() - position.posY);
            segmentPosition.setZ(segmentPosition.getZ() - position.posZ);
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
         * Assigns the x/y/z position and orientation normal vector based on theta,
         * where the theta is clamped between 0.0 and 1.0
         * 
         * @param position where the result is assigned to
         * @param theta (0.0 ... 1.0)
         */
        public void calcPosition(Position position, double theta) {
            if (theta <= 0.0) {
                position.posX = p0.x;
                position.posY = p0.y;
                position.posZ = p0.z;
                position.upX = up0.x;
                position.upY = up0.y;
                position.upZ = up0.z;
            } else if (theta >= 1.0) {
                position.posX = p1.x;
                position.posY = p1.y;
                position.posZ = p1.z;
                position.upX = up1.x;
                position.upY = up1.y;
                position.upZ = up1.z;
            } else {
                position.posX = p0.x + dt.x * theta;
                position.posY = p0.y + dt.y * theta;
                position.posZ = p0.z + dt.z * theta;
                position.upX = up0.x + up_dt.x * theta;
                position.upY = up0.y + up_dt.y * theta;
                position.upZ = up0.z + up_dt.z * theta;
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
         * @param position relative to the rails
         * @return theta
         */
        public final double calcTheta(Position position) {
            return calcTheta(position.posX, position.posY, position.posZ);
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

    /**
     * Builder for paths, which makes it easier to compose paths
     * consisting of multiple points.
     */
    public static class Builder {
        private List<Point> points = new ArrayList<Point>(3);
        private double default_up_x = 0.0;
        private double default_up_y = 1.0;
        private double default_up_z = 0.0;

        public Builder up(BlockFace up) {
            return this.up(up.getModX(), up.getModY(), up.getModZ());
        }

        public Builder up(double up_x, double up_y, double up_z) {
            this.default_up_x = up_x;
            this.default_up_y = up_y;
            this.default_up_z = up_z;
            return this;
        }

        public Builder add(double x, double y, double z) {
            return add(new Point(x, y, z, default_up_x, default_up_y, default_up_z));
        }

        public Builder add(double x, double y, double z, double up_x, double up_y, double up_z) {
            return add(new Point(x, y, z, up_x, up_y, up_z));
        }

        public Builder add(double x, double y, double z, BlockFace face) {
            return add(new Point(x, y, z, face));
        }

        public Builder add(Vector point) {
            return add(new Point(point, default_up_x, default_up_y, default_up_z));
        }

        public Builder add(Vector point, double up_x, double up_y, double up_z) {
            return add(new Point(point, up_x, up_y, up_z));
        }

        public Builder add(Vector point, BlockFace face) {
            return add(new Point(point, face));
        }

        public Builder add(Point point) {
            this.points.add(point);
            return this;
        }

        public RailPath build() {
            return RailPath.create(points.toArray(new Point[points.size()]));
        }
    }
}
