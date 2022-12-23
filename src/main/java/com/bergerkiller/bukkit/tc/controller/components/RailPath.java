package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.mutable.LocationAbstract;
import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Represents a series of points along which a minecart moves over the rails.
 * The points of this path are relative to the (0,0,0) of the rails block.
 * Paths are immutable to enable optimized performance for simple (two-point) paths.
 */
public class RailPath {
    private static final double SMALL_ADVANCE_MIN_MOT = 1e-6;
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
            for (int i = 0; i < this.segments.length; i++) {
                this.segments[i].postinit();
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
     * Gets the start position of this rail path. This is the final position
     * at the start of the first segment of this path.
     * 
     * @return start position
     */
    public Position getStartPosition() {
        Segment firstSegment = this.segments[0];
        Position p = new Position();
        p.relative = true;
        p.posX = firstSegment.p0.x;
        p.posY = firstSegment.p0.y;
        p.posZ = firstSegment.p0.z;
        p.wheelSegment = firstSegment;
        p.wheelTheta = 0.0;
        p.motX = -firstSegment.mot.getX();
        p.motY = -firstSegment.mot.getY();
        p.motZ = -firstSegment.mot.getZ();
        return p;
    }

    /**
     * Gets the end position of this rail path. This is the final position
     * at the end of the last segment of this path.
     * 
     * @return end position
     */
    public Position getEndPosition() {
        Segment lastSegment = this.segments[this.segments.length - 1];
        Position p = new Position();
        p.relative = true;
        p.posX = lastSegment.p1.x;
        p.posY = lastSegment.p1.y;
        p.posZ = lastSegment.p1.z;
        p.wheelSegment = lastSegment;
        p.wheelTheta = 1.0;
        p.motX = lastSegment.mot.getX();
        p.motY = lastSegment.mot.getY();
        p.motZ = lastSegment.mot.getZ();
        return p;
    }

    /**
     * Gets the end position on this rail path if traveled infinitely forwards
     * from the input position specified. Can only be used if the input position
     * was previously moved/snapped on this path.
     *
     * @param railBlock Rail Block this path is for
     * @param position Absolute position (on this path)
     * @return Absolute position of the end of the path
     */
    public Position getEndOfPath(Block railBlock, RailPath.Position position) {
        RailPath.Segment s = position.wheelSegment;
        if (s == null) {
            throw new IllegalArgumentException("Input position was never moved or snapped to a path!");
        }
        Position end = (position.motDot(s.p_offset) > 0.0) ? getEndPosition() : getStartPosition();
        end.makeAbsolute(railBlock);
        return end;
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
     * Computes proximity information which describes how close to the path a particular position is.
     * This is an extension of {@link #distanceSquared(Vector)} to describe additional information
     * required for nearest-sorting. Use {@link ProximityInfo#compareTo(ProximityInfo)} to compare
     * different positions.
     * 
     * @param position      The rail-relative position on this path
     * @param motionVector  The movement vector while on this path
     * @return Proximity information
     */
    public ProximityInfo getProximityInfo(Vector position, Vector motionVector) {
        ProximityInfo info = new ProximityInfo();
        for (int i = 0; i < this.segments.length; i++) {
            Segment tmpSegment = this.segments[i];
            if (tmpSegment.isZeroLength()) continue;
            double tmpTheta = tmpSegment.calcTheta(position);
            double tmpDistSquared = tmpSegment.calcDistanceSquared(position, tmpTheta);
            if (tmpDistSquared < info.distanceSquared) {
                info.distanceSquared = tmpDistSquared;
                if (tmpTheta < tmpSegment.end_theta_threshold && i == 0) {
                    info.canMoveForward = tmpSegment.mot.dot(motionVector) >= 0.0;
                } else if ((1.0 - tmpTheta) < tmpSegment.end_theta_threshold && i == (this.segments.length-1)) {
                    info.canMoveForward = tmpSegment.mot.dot(motionVector) <= 0.0;
                } else {
                    info.canMoveForward = true;
                }
            }
        }
        return info;
    }

    /**
     * Finds the distance squared between a rail-relative position and this rail path.
     * Returns {@link Double#MAX_VALUE} if this path has no segments.
     * 
     * @param position (relative)
     * @return distance squared between the position and this rail path
     */
    public double distanceSquared(Vector position) {
        double closestDistance = Double.MAX_VALUE;
        for (int i = 0; i < this.segments.length; i++) {
            Segment tmpSegment = this.segments[i];
            if (tmpSegment.isZeroLength()) continue;
            double tmpTheta = tmpSegment.calcTheta(position);
            double tmpDistSquared = tmpSegment.calcDistanceSquared(position, tmpTheta);
            if (tmpDistSquared < closestDistance) {
                closestDistance = tmpDistSquared;
            }
        }
        return closestDistance;
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
     * Snaps onto this rail path.
     * The input position information is updated to be on this path.
     * This snap function allows absolute position calculations, by also specifying the rails block.
     * 
     * @param position in relative or absolute world coordinates
     * @param railsBlock relative to which this path is
     */
    public void snap(Position position, Block railsBlock) {
        this.move(position, railsBlock, 0.0);
    }

    /**
     * Moves along this rail path for a certain distance.
     * The input rail state information is updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * 
     * @param state with rail position, direction and block information
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(RailState state, double distance) {
        return this.move(state.position(), state.railBlock(), distance);
    }

    /**
     * Moves along this rail path for a certain distance.
     * The input position information is updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * This move function allows absolute position calculations, by also specifying the rails block.
     * 
     * @param position in absolute world coordinates
     * @param railBlock relative to which is moved
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double move(Position position, Block railBlock, double distance) {
        position.assertAbsolute();
        position.makeRelative(railBlock);;
        double result = moveRelative(position, distance);
        position.makeAbsolute(railBlock);
        return result;
    }

    @Deprecated
    public double move(Vector position, Vector direction, Block railsBlock, double distance) {
        position.setX(position.getX() - railsBlock.getX());
        position.setY(position.getY() - railsBlock.getY());
        position.setZ(position.getZ() - railsBlock.getZ());
        double result = moveRelative(position, direction, distance);
        position.setX(position.getX() + railsBlock.getX());
        position.setY(position.getY() + railsBlock.getY());
        position.setZ(position.getZ() + railsBlock.getZ());
        return result;
    }

    @Deprecated
    public double moveRelative(Vector position, Vector direction, double distance) {
        Position tmp = new Position();
        tmp.relative = true;
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

    /**
     * Moves along this rail path for a certain distance.
     * The input position information is updated with the movement.
     * A movement distance of 0 can be used to snap the minecart onto the path.
     * A return distance of 0.0 indicates that the end of the path was reached.
     * This move function allows relative position calculations; the positions are relative to the rails.
     * 
     * @param position in rail-relative coordinates
     * @param distance to move
     * @return actual distance moved. Can be less than distance if the end of the path is reached.
     */
    public double moveRelative(Position position, double distance) {
        // Check
        position.assertRelative();

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
                    s.calcPosition(position, theta + (distance * s.linv));
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
                    s.calcPosition(position, theta - (distance * s.linv));
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
                                s.calcPosition(position, theta + (distance * s.linv));
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
                                s.calcPosition(position, theta - (distance * s.linv));
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
                    Vector mot = s.mot;
                    if (order > 0) {
                        position.motX = mot.getX();
                        position.motY = mot.getY();
                        position.motZ = mot.getZ();
                    } else {
                        position.motX = -mot.getX();
                        position.motY = -mot.getY();
                        position.motZ = -mot.getZ();
                    }
                }
            }
            return moved;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof RailPath) {
            RailPath other = (RailPath) o;
            if (other.points.length != this.points.length) {
                return false;
            }
            for (int i = 0; i < this.points.length; i++) {
                Point p1 = this.points[i];
                Point p2 = other.points[i];
                if (p1.x != p2.x || p1.y != p2.y || p1.z != p2.z) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("RailPath[npoints=").append(this.points.length + "]:");
        for (RailPath.Point p : this.points) {
            str.append("\n  - ").append(p.toString());
        }
        return str.toString();
    }

    /**
     * Stringifies the start and end positions of this rail path.
     * The input railBlock is used to turn relative coordinates into
     * absolute coordinates, if required.
     *
     * @param railBlock Rail block used to translate relative
     *        to absolute coordinates
     * @return stringified readout of the start and end coordinates
     */
    public String stringifyEndPoints(Block railBlock) {
        if (this.isEmpty()) {
            return "RailPath{EMPTY}";
        } else {
            Position a = this.getStartPosition();
            Position b = this.getEndPosition();
            a.makeAbsolute(railBlock);
            b.makeAbsolute(railBlock);
            return "RailPath{[ " + a.posX + " / "  + a.posY + " / " + a.posZ + " ]"
                    + " => [ " + b.posX + " / " + b.posY + " / " + b.posZ + " ]}";
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
     * Stores state for proximity comparison
     */
    public static class ProximityInfo implements Comparable<ProximityInfo> {
        /**
         * Trains are allowed to jump this size of gap between rail paths, if it
         * means jumping from the end of a rail section to continue movement forwards.
         * If sections don't 100% line up, this prevents the train getting stuck.
         */
        private static final double DIST_DIFF_SQ_THRESHOLD = (1e-3 * 1e-3);

        public double distanceSquared = Double.MAX_VALUE;
        public boolean canMoveForward = false;

        @Override
        public int compareTo(ProximityInfo o) {
            if (o.canMoveForward == this.canMoveForward) {
                return Double.compare(this.distanceSquared, o.distanceSquared);
            }

            double diffDistSq = this.distanceSquared - o.distanceSquared;
            if (diffDistSq > DIST_DIFF_SQ_THRESHOLD) {
                return 1;
            } else if (diffDistSq < -DIST_DIFF_SQ_THRESHOLD) {
                return -1;
            } else {
                return Boolean.compare(o.canMoveForward, this.canMoveForward);
            }
        }
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
         * Orientation of a wheel on the path. Is computed once used.
         */
        private Segment wheelSegment;
        private double wheelTheta;
        /**
         * Whether we are walking the path in reverse. This is important
         * when applying the south-east rule when encountering an orthogonal
         * rail path intersection.
         */
        public boolean reverse = false;

        /**
         * Whether this position is relative to the rails (true), or absolute
         * world coordinates (false). Property is automatically switched when
         * {@link #makeRelative(Block)} or {@link #makeAbsolute(Block)} are used.
         */
        public boolean relative = true;

        /**
         * Makes the position relative to the block, if not already {@link #relative}.
         * 
         * @param railBlock
         */
        public void makeRelative(Block railBlock) {
            if (!this.relative) {
                this.relative = true;
                this.posX -= railBlock.getX();
                this.posY -= railBlock.getY();
                this.posZ -= railBlock.getZ();
            }
        }

        /**
         * Makes the position absolute world coordinates, if not already NOT {@link #relative}.
         * 
         * @param railBlock
         */
        public void makeAbsolute(Block railBlock) {
            if (this.relative) {
                this.relative = false;
                this.posX += railBlock.getX();
                this.posY += railBlock.getY();
                this.posZ += railBlock.getZ();
            }
        }

        /**
         * Verifies that this Position is using relative position coordinates.
         * Throws an {@link IllegalStateException} if this is not the case.
         */
        public final void assertRelative() {
            if (!this.relative) {
                throw new IllegalStateException("Rail Position must be in relative coordinates");
            }
        }

        /**
         * Verifies that this Position is using absolute position coordinates.
         * Throws an {@link IllegalStateException} if this is not the case.
         */
        public final void assertAbsolute() {
            if (this.relative) {
                throw new IllegalStateException("Rail Position must be in absolute world coordinates");
            }
        }

        /**
         * Moves the position forwards by the most minimal amount possible.
         * When at block boundaries, this will push the position beyond towards the next
         * block. The new position will be guaranteed different to the previous
         * position.<br>
         * <br>
         * When the absolute position has very high coordinates, the step moved
         * is larger than when the absolute position is near 0.
         */
        public void smallAdvance() {
            if (motX > SMALL_ADVANCE_MIN_MOT) {
                posX = Math.nextUp(posX);
            } else if (motX < -SMALL_ADVANCE_MIN_MOT) {
                posX = Math.nextDown(posX);
            }
            if (motY > SMALL_ADVANCE_MIN_MOT) {
                posY = Math.nextUp(posY);
            } else if (motY < -SMALL_ADVANCE_MIN_MOT) {
                posY = Math.nextDown(posY);
            }
            if (motZ > SMALL_ADVANCE_MIN_MOT) {
                posZ = Math.nextUp(posZ);
            } else if (motZ < -SMALL_ADVANCE_MIN_MOT) {
                posZ = Math.nextDown(posZ);
            }
        }

        /**
         * Gets the orientation of wheels at this rail position
         *
         * @return wheel orientation at this rail position
         */
        public Quaternion getWheelOrientation() {
            Segment s = this.wheelSegment;
            if (s == null) {
                // Fallback. Should never be used, really.
                return Quaternion.fromLookDirection(this.getMotion(), new Vector(0, 1, 0));
            } else {
                return s.calcWheelOrientation(this.wheelTheta);
            }
        }

        /**
         * Moves a distance forwards along the same current movement
         * vector.
         *
         * @param distance Distance to move, negative to move in reverse
         */
        public void move(double distance) {
            posX += distance * motX;
            posY += distance * motY;
            posZ += distance * motZ;
        }

        public double distance(Position position) {
            if (this.relative)
                position.assertRelative();
            else
                position.assertAbsolute();

            return MathUtil.distance(posX, posY, posZ,
                    position.posX, position.posY, position.posZ);
        }

        public double distance(Location location) {
            this.assertAbsolute();
            return MathUtil.distance(posX, posY, posZ,
                    location.getX(), location.getY(), location.getZ());
        }

        public double distanceSquared(Location location) {
            this.assertAbsolute();
            return MathUtil.distanceSquared(posX, posY, posZ,
                    location.getX(), location.getY(), location.getZ());
        }

        public double distance(LocationAbstract location) {
            this.assertAbsolute();
            return MathUtil.distance(posX, posY, posZ,
                    location.getX(), location.getY(), location.getZ());
        }

        /**
         * Performs a distance squared check, allowing for absolute and relative positions to be
         * compared. It is assumed both positions are about the same rail block.
         * 
         * @param railBlock
         * @param pos
         * @return distance squared between this and the position
         */
        public double distanceSquaredAtRail(Block railBlock, RailPath.Position pos) {
            if (pos.relative == this.relative) {
                return MathUtil.distanceSquared(this.posX, this.posY, this.posZ,
                                                pos.posX, pos.posY, pos.posZ);
            } else if (this.relative) {
                return MathUtil.distanceSquared(this.posX, this.posY, this.posZ,
                                                pos.posX - railBlock.getX(),
                                                pos.posY - railBlock.getY(),
                                                pos.posZ - railBlock.getZ());
            } else {
                return MathUtil.distanceSquared(this.posX - railBlock.getX(),
                                                this.posY - railBlock.getY(),
                                                this.posZ - railBlock.getZ(),
                                                pos.posX, pos.posY, pos.posZ);
            }
        }

        public double distanceSquared(RailPath.Position pos) {
            if (pos.relative != this.relative) {
                throw new IllegalStateException("Self and pos must both be relative or both be absolute");
            }
            return MathUtil.distanceSquared(this.posX, this.posY, this.posZ,
                                            pos.posX, pos.posY, pos.posZ);
        }

        public double distanceSquared(LocationAbstract pos) {
            if (this.relative) {
                throw new IllegalStateException("Self position must be absolute");
            }
            return pos.distanceSquared(this.posX, this.posY, this.posZ);
        }

        public Location toLocation(World world) {
            this.assertAbsolute();
            return new Location(world, posX, posY, posZ);
        }

        public Location toLocation(Block railsBlock) {
            if (this.relative) {
                return new Location(railsBlock.getWorld(),
                        railsBlock.getX()+posX, railsBlock.getY()+posY, railsBlock.getZ()+posZ);
            } else {
                return new Location(railsBlock.getWorld(), posX, posY, posZ);
            }
        }

        public void getLocation(Location location) {
            this.assertAbsolute();
            location.setX(this.posX);
            location.setY(this.posY);
            location.setZ(this.posZ);
        }

        public void setLocation(Location location) {
            this.relative = false;
            this.posX = location.getX();
            this.posY = location.getY();
            this.posZ = location.getZ();
        }

        public void setLocation(LocationAbstract location) {
            this.relative = false;
            this.posX = location.getX();
            this.posY = location.getY();
            this.posZ = location.getZ();
        }

        public void setLocationMidOf(Block block) {
            this.relative = false;
            this.posX = block.getX() + 0.5;
            this.posY = block.getY() + 0.5;
            this.posZ = block.getZ() + 0.5;
        }

        public BlockFace getMotionFace() {
            return Util.vecToFace(motX, motY, motZ, false);
        }

        public BlockFace getMotionFaceWithSubCardinal() {
            return Util.vecToFace(motX, motY, motZ, true);
        }

        public double motDot(Position pos) {
            return (motX * pos.motX) + (motY * pos.motY) + (motZ * pos.motZ);
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

        public double motLength() {
            return Math.sqrt(motLengthSquared());
        }

        public double motLengthSquared() {
            return (motX * motX) + (motY * motY) + (motZ * motZ);
        }

        public Vector getMotion() {
            return new Vector(this.motX, this.motY, this.motZ);
        }

        public Vector getMotion(Vector v) {
            v.setX(this.motX);
            v.setY(this.motY);
            v.setZ(this.motZ);
            return v;
        }

        public void setMotion(VectorAbstract movement) {
            this.motX = movement.getX();
            this.motY = movement.getY();
            this.motZ = movement.getZ();
        }

        public void setMotion(Vector movement) {
            if (Double.isNaN(movement.getX())) {
                throw new IllegalArgumentException("Motion vector is NaN");
            }
            this.motX = movement.getX();
            this.motY = movement.getY();
            this.motZ = movement.getZ();
        }

        public void setMotion(BlockFace movement) {
            this.motX = movement.getModX();
            this.motY = movement.getModY();
            this.motZ = movement.getModZ();
        }

        public void invertMotion() {
            this.motX = -this.motX;
            this.motY = -this.motY;
            this.motZ = -this.motZ;
        }

        public void normalizeMotion() {
            double n = MathUtil.getNormalizationFactor(this.motX, this.motY, this.motZ);
            if (Double.isInfinite(n)) {
                this.motX = 0.0;
                this.motY = -1.0;
                this.motZ = 0.0;
            } else {
                this.motX *= n;
                this.motY *= n;
                this.motZ *= n;
            }
        }

        public void copyTo(Position p) {
            p.posX = this.posX;
            p.posY = this.posY;
            p.posZ = this.posZ;
            p.motX = this.motX;
            p.motY = this.motY;
            p.motZ = this.motZ;
            p.wheelSegment = this.wheelSegment;
            p.wheelTheta = this.wheelTheta;
            p.reverse = this.reverse;
            p.relative = this.relative;
        }

        @Override
        public Position clone() {
            Position p = new Position();
            this.copyTo(p);;
            return p;
        }

        @Override
        public boolean equals(Object o) {
            // Note: not very practical, but useful for test cases...
            if (o == this) {
                return true;
            } else if (o instanceof Position) {
                Position other = (Position) o;
                return this.posX == other.posX &&
                       this.posY == other.posY &&
                       this.posZ == other.posZ &&
                       this.motX == other.motX &&
                       this.motY == other.motY &&
                       this.motZ == other.motZ &&
                       this.relative == other.relative &&
                       this.reverse == other.reverse;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return (this.relative ? "{r_pos={" : "{a_pos={") +
                    MathUtil.round(posX, 4) + "/" +
                    MathUtil.round(posY, 4) + "/" +
                    MathUtil.round(posZ, 4) + "},mot={" +
                    MathUtil.round(motX, 4) + "/" +
                    MathUtil.round(motY, 4) + "/" +
                    MathUtil.round(motZ, 4) + "},f=" +
                    this.getMotionFace().name() + "}";
        }

        /**
         * Creates a Position from an absolute position on the rails and a movement direction vector
         * 
         * @param position in absolute world coordinates
         * @param direction vector
         * @return absolute Position
         */
        public static Position fromPosDir(Vector position, Vector direction) {
            Position p = new Position();
            p.relative = false;
            p.posX = position.getX();
            p.posY = position.getY();
            p.posZ = position.getZ();
            p.motX = direction.getX();
            p.motY = direction.getY();
            p.motZ = direction.getZ();
            return p;
        }

        /**
         * Creates a Position from an absolute from and to position in world coordinates
         * 
         * @param from
         * @param to
         * @return absolute Position
         */
        public static Position fromTo(Location from, Location to) {
            Position p = new Position();
            p.relative = false;
            p.posX = from.getX();
            p.posY = from.getY();
            p.posZ = from.getZ();
            p.motX = to.getX() - p.posX;
            p.motY = to.getY() - p.posY;
            p.motZ = to.getZ() - p.posZ;
            return p;
        }

        /**
         * Turns a Location, and its yaw/pitch information, into a Position
         * 
         * @param positionWithDirection
         * @return Position, motion is set to the Location's direction vector (yaw/pitch)
         */
        public static Position fromLocation(Location positionWithDirection) {
            Position p = new Position();
            p.relative = false;
            p.posX = positionWithDirection.getX();
            p.posY = positionWithDirection.getY();
            p.posZ = positionWithDirection.getZ();
            Vector dir = positionWithDirection.getDirection();
            p.motX = dir.getX();
            p.motY = dir.getY();
            p.motZ = dir.getZ();
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
         * Computes the dot product of this point's x/y/z values and a vector
         * 
         * @param vector
         * @return dot product
         */
        public double dot(Vector vector) {
            return this.x * vector.getX() + this.y * vector.getY() + this.z * vector.getZ();
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
        /** The total offset between p0 and p1 (p1 - p0) */
        public final Vector p_offset;
        /** The normalized motion direction vector along this segment */
        public final Vector mot;
        /** p_offset / ls */
        private final Vector mot_dt;
        public final Quaternion p0_orientation;
        public final Quaternion p1_orientation;
        /** The up orientation vector changes at all during the movement along this segment */
        public final boolean has_changing_up_orientation;
        /** The delta Y position changes significantly during the movement along this segment */
        public final boolean has_vertical_slope;
        /** Length-based theta threshold value beyond which a position is considered at the end of this segment */
        private final double end_theta_threshold;
        /** Segment length */
        public final double l;
        /** Segment length squared */
        public final double ls; // l*l
        /** 1 / segment length */
        public final double linv; // 1.0 / l

        private Segment prev, next;

        /** @deprecated Use {@link #p_offset} instead */
        @Deprecated
        public final Point dt;
        /** @deprecated use {@link #mot} instead */
        @Deprecated
        public final Point dt_norm;

        public Segment(Point p0, Point p1) {
            p_offset = new Vector(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
            ls = p_offset.lengthSquared();
            l = Math.sqrt(ls);
            if (l <= 1e-20) {
                linv = 0.0;
                mot = new Vector();
                mot_dt = new Vector();
            } else {
                linv = 1.0 / l;
                mot = p_offset.clone().multiply(linv);
                mot_dt = p_offset.clone().multiply(1.0 / ls);
            }
            has_vertical_slope = mot.getY() < -1e-10 || mot.getY() > 1e-10;
            end_theta_threshold = Math.min(1e-3, 1e-4 * linv);

            // Legacy stuff encoded this as Point which was just...weird
            dt = new Point(p_offset);
            dt_norm = new Point(mot);

            // Uses the roll values of the points and the segment slope
            // to calculate the normal 'up' vector. This is the direction
            // upwards that the Minecart faces. This is especially important
            // for positions like upside-down, vertical, and twisted
            Vector up0 = mot.clone().crossProduct(p0.up()).crossProduct(mot).normalize();
            Vector up1 = mot.clone().crossProduct(p1.up()).crossProduct(mot).normalize();
            this.p0 = new Point(p0.x, p0.y, p0.z, up0.getX(), up0.getY(), up0.getZ());
            this.p1 = new Point(p1.x, p1.y, p1.z, up1.getX(), up1.getY(), up1.getZ());
            this.has_changing_up_orientation = (up0.distanceSquared(up1) > 1e-6);
            this.p0_orientation = new Quaternion();
            this.p1_orientation = new Quaternion();
        }

        /**
         * Called after the prev/next segments have been set
         */
        public void postinit() {
            // Orientation of a wheel when at p0
            Quaternion mid_up0 = Quaternion.fromLookDirection(this.mot, this.p0.up());
            if (this.prev == null || this.prev.isZeroLength()) {
                this.p0_orientation.setTo(mid_up0);
            } else {
                Quaternion prev_up = Quaternion.fromLookDirection(this.prev.mot, this.prev.p1.up());
                this.p0_orientation.setTo(Quaternion.slerp(prev_up, mid_up0, 0.5));
            }

            // Orientation of a wheel when at p1
            Quaternion mid_up1 = Quaternion.fromLookDirection(this.mot, this.p1.up());
            if (this.next == null || this.next.isZeroLength()) {
                this.p1_orientation.setTo(mid_up1);
            } else {
                Quaternion next_up = Quaternion.fromLookDirection(this.next.mot, this.next.p0.up());
                this.p1_orientation.setTo(Quaternion.slerp(next_up, mid_up1, 0.5));
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
                    railsBlock.getX() + p0.x + p_offset.getX() * theta,
                    railsBlock.getY() + p0.y + p_offset.getY() * theta,
                    railsBlock.getZ() + p0.z + p_offset.getZ() * theta);
        }

        private final int isHeadingToPrev(Position position) {
            if (this.prev != null) {
                double dot = position.motDot(this.prev.mot);
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
                double dot = position.motDot(this.next.mot);
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
            Vector mot = this.mot;
            double dot = position.motDot(mot);

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
                position.motX = mot.getX();
                position.motY = mot.getY();
                position.motZ = mot.getZ();
                return 1;
            } else {
                position.motX = -mot.getX();
                position.motY = -mot.getY();
                position.motZ = -mot.getZ();
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
                dx = (p0.x + p_offset.getX() * theta);
                dy = (p0.y + p_offset.getY() * theta);
                dz = (p0.z + p_offset.getZ() * theta);
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
                position.setX(p0.x + p_offset.getX() * theta);
                position.setY(p0.y + p_offset.getY() * theta);
                position.setZ(p0.z + p_offset.getZ() * theta);
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
            position.wheelSegment = this;
            position.wheelTheta = theta;
            if (theta <= 0.0) {
                position.posX = p0.x;
                position.posY = p0.y;
                position.posZ = p0.z;
            } else if (theta >= 1.0) {
                position.posX = p1.x;
                position.posY = p1.y;
                position.posZ = p1.z;
            } else {
                position.posX = p0.x + p_offset.getX() * theta;
                position.posY = p0.y + p_offset.getY() * theta;
                position.posZ = p0.z + p_offset.getZ() * theta;
            }
        }

        /**
         * Calculates the Quaternion orientation wheels have somewhere along this rail path segment
         *
         * @param theta
         * @return wheel orientation
         */
        public Quaternion calcWheelOrientation(double theta) {
            if (theta <= 0.0) {
                return p0_orientation;
            } else if (theta >= 1.0) {
                return p1_orientation;
            } else {
                return Quaternion.slerp(p0_orientation, p1_orientation, theta);
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
            Point p0 = this.p0;
            Vector mot = this.mot_dt;
            return -((p0.x - x) * mot.getX() + (p0.y - y) * mot.getY() + (p0.z - z) * mot.getZ());
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
