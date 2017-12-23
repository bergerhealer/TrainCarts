package com.bergerkiller.bukkit.tc.controller.components;

import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Tracks the position and direction of two wheels of a Minecart, using that information
 * to calculate the orienation of the Minecart. It is here that smooth turns, banking and other
 * aesthetic effects to the Minecart position are calculated.
 */
public class WheelTrackerMember {
    public static final double MIN_WHEEL_DISTANCE = 1E-5;
    private final MinecartMember<?> _owner;
    private final Wheel _front;
    private final Wheel _back;
    private Quaternion _orientation_last = null;
    private Vector _position = null;
    private double _centripetalForce = 0.0;
    private double _bankingRoll = 0.0;

    public WheelTrackerMember(MinecartMember<?> owner) {
        this._owner = owner;
        this._front = new Wheel(owner, true);
        this._back = new Wheel(owner, false);
    }

    public MinecartMember<?> getOwner() {
        return this._owner;
    }

    /**
     * Gets the front wheel
     * 
     * @return front wheel
     */
    public Wheel front() {
        return this._front;
    }

    /**
     * Gets the back wheel
     * 
     * @return back wheel
     */
    public Wheel back() {
        return this._back;
    }

    public Quaternion getLastOrientation() {
        if (this._orientation_last == null) {
            this._orientation_last = this._owner.getOrientation();
        }
        return this._orientation_last;
    }

    /**
     * Obtains the center position of the minecart
     * 
     * @return center position
     */
    public Vector getPosition() {
        if (this._position == null) {
            // Take average of the two wheel positions for where the Minecart should be
            // TODO: Is this actually correct?
            double diff = back().getDistance() - front().getDistance();

            this._position = new Vector();
            this._position.add(front().getPosition());
            this._position.add(back().getPosition());
            if (diff != 0.0) {
                Vector dir = front().getPosition().clone().subtract(back().getPosition());
                this._position.add(dir.multiply(diff));
            }
            this._position.multiply(0.5);
            this._position.add(this._owner.getEntity().loc.vector());
        }
        return this._position;
    }

    public double getBankingRoll() {
        return this._bankingRoll;
    }

    public void update() {
        this._orientation_last = this._owner.getOrientation();
        this._position = null; // Reset
        this._front.update();
        this._back.update();

        // Calculate new orientation
        Quaternion new_orientation;
        {
            Vector dir = front().getPosition().clone().subtract(back().getPosition());
            if (dir.lengthSquared() < 0.0001) {
                Vector fwd_a = front().getForward();
                Vector fwd_b = back().getForward();

                // Only allow this when the wheels have somewhat the same direction
                // If they don't, then there is probably a glitch!
                // Glitches often happen when a path crosses another path at a 90-degree angle
                // There is no way to 'track back' correctly, so forward and backward direction are equal
                // Because one wheel is 'backwards', it multiplies it with -1
                // In those cases, the forward direction (in which we move) is most reliable
                // This slightly suppresses it, but does not fix it...
                if (fwd_a.dot(fwd_b) > 0.0) {
                    dir = fwd_a.clone().add(fwd_b);
                } else {
                    Vector a = this._owner.getOrientationForward();
                    if (a.dot(FaceUtil.faceToVector(this._owner.getDirection())) >= 0.0) {
                        dir = fwd_a;
                    } else {
                        dir = fwd_b;
                    }
                }
            }
            Vector up = front().getUp().clone().add(back().getUp());
            if (up.lengthSquared() < 0.0001) {
                up = this._owner.getOrientation().upVector();
            }
            new_orientation = Quaternion.fromLookDirection(dir, up);
        }

        // Calculate banking effects
        TrainProperties props = this._owner.getGroup().getProperties();
        if (props.getBankingStrength() != 0.0) {
            // Get the orientation difference between the current and last rotation
            Quaternion q = Quaternion.divide(new_orientation, this.getLastOrientation());

            // Calculate the forward vector - the x (left/right) is what is interesting
            // This stores the change in direction, which allows calculation of centripetal force
            double centripetalForceStep = q.forwardVector().getX();
            if (MathUtil.isHeadingTo(_owner.getDirection(), new_orientation.forwardVector())) {
                centripetalForceStep = -centripetalForceStep;
            }

            // Higher speed = more centripetal force
            centripetalForceStep *= Math.min(_owner.getForce(), _owner.getEntity().getMaxSpeed()) / _owner.getGroup().getUpdateSpeedFactor();

            // Track an aggregate of the centripetal force thus far encountered
            // Based on smoothness, slowly reduce this force again
            if (props.getBankingSmoothness() == 0.0) {
                this._centripetalForce = centripetalForceStep;
            } else {
                this._centripetalForce += centripetalForceStep;
                this._centripetalForce *= (1.0 - (1.0 / props.getBankingSmoothness()));
            }

            // Turn the centripetal force into a banking angle
            // Also apply smoothening to the angle, to make it smoother
            double angle = Math.toDegrees(Math.atan2(this._centripetalForce, 1.0 / props.getBankingStrength()));
            if (props.getBankingSmoothness() == 0.0) {
                this._bankingRoll = angle;
            } else {
                this._bankingRoll += (1.0 / props.getBankingSmoothness()) * ( angle - this._bankingRoll);
            }
        } else {
            this._bankingRoll = 0.0;
        }

        // Refresh member orientation through rail logic
        this._owner.getRailLogic().onUpdateOrientation(this._owner, new_orientation);
    }

    /**
     * Tracks the position of a wheel by looking at past positions, or alternatively,
     * walking the tracks to satisfy the wheel-to-center distance.
     */
    public static class Wheel {
        private final MinecartMember<?> member;
        private final boolean _front;
        private double _distance = 0.0; // Distance from the center this wheel is at
                                        // This will eventually be modified based on the model that is applied
        private Vector _position = null; // Position is relative to the minecart position
        private Vector _forward = null;  // The forward direction vector of this wheel
        private Vector _up = null;       // Up vector, used for angling the Minecart around the wheels

        public Wheel(MinecartMember<?> member, boolean front) {
            this.member = member;
            this._front = front;
        }

        /**
         * Sets the distance from the center of the Minecart for this wheel
         * 
         * @param distance to set to
         */
        public void setDistance(double distance) {
            if (this._distance != distance) {
                this._distance = distance;
                this._position = null;
            }
        }

        /**
         * Gets the distance from the center of the Minecart for this wheel
         * 
         * @return wheel distance
         */
        public double getDistance() {
            return this._distance;
        }

        /**
         * Gets the center-relative position of this wheel.
         * The center is the exact coordinates of the Minecart itself.
         * 
         * @return center-relative position
         */
        public Vector getPosition() {
            if (this._position == null) {
                this.update(); // Required
            }
            return this._position;
        }

        /**
         * Gets the up unit vector. This is the vector upwards, which controls
         * the angle of the Minecart around the set of wheels. This is particularly
         * important for rails like upside-down rails and vertical rails.
         * 
         * @return up vector
         */
        public Vector getUp() {
            if (this._up == null) {
                this.update(); // Required
            }
            return this._up;
        }

        /**
         * Gets the forward unit vector. This is the direction the wheel is moving on the rails,
         * not the orientation of the wheel. For calculating the orientation, it must be calculated
         * whether the Minecart is moving into the orientation or opposite of it.
         * 
         * @return forward vector
         */
        public Vector getForward() {
            if (this._forward == null) {
                this.update(); // Required
            }
            return this._forward;
        }

        /**
         * Recalculates the position of this wheel
         */
        public void update() {
            // Find the index of the rails for this member
            List<TrackedRail> rails = this.member.getGroup().getRailTracker().getRailInformation();

            int railIndex = -1;
            if (!this.member.isDerailed()) {
                for (int i = 0; i < rails.size(); i++) {
                    TrackedRail rail = rails.get(i);
                    if (rail.member == this.member && rail.block.equals(this.member.getBlock())) {
                        railIndex = i;
                        break;
                    }
                }
            }

            // If this Minecart is derailed, set the wheels to point into the direction of the orientation
            if (railIndex == -1) {
                Quaternion orientation = member.getOrientation();
                this._up = orientation.upVector();
                this._forward = orientation.forwardVector();
                this._position = this._forward.clone().multiply(-this._distance);
                if (!this._front) {
                    this._position.multiply(-1.0);
                }
                return;
            }

            TrackedRail rail = rails.get(railIndex);

            // Calculate the approximate direction of this Minecart based on yaw/pitch
            // This is used to decide whether we move +/- by the members for front/back
            Vector direction = FaceUtil.faceToVector(this.member.getDirection());
            boolean isFrontFacing = MathUtil.isHeadingTo(this.member.getDirection(), this.member.getOrientationForward());
            if (!isFrontFacing) {
                direction.multiply(-1.0);
            }
            if (!this._front) {
                direction.multiply(-1.0);
            }

            // Walk along the paths we know
            BlockFace memberDir = member.getDirection();
            RailPath.Position position = new RailPath.Position();
            position.posX = this.member.getEntity().loc.getX();
            position.posY = this.member.getEntity().loc.getY();
            position.posZ = this.member.getEntity().loc.getZ();
            position.reverse = this._front ^ isFrontFacing;
            if (this._distance > MIN_WHEEL_DISTANCE) {
                // Distance is set: walk the path
                position.motX = memberDir.getModX();
                position.motY = memberDir.getModY();
                position.motZ = memberDir.getModZ();
                int order = 0;
                double remainingDistance = this._distance;
                for (int index = railIndex; index >= 0 && index < rails.size() && remainingDistance >= 0.0001; index += order) {
                    rail = rails.get(index);
                    RailPath path = rail.getPath();
                    if (order == 0)  {
                        // Calculate the current movement direction of this Minecart
                        // This is done by taking the getDirection() blockface and feeding it into the path
                        // By using length 0, no actual movement is done, and only direction is retrieved
                        // Use this information to figure out whether we iterate +1 or -1
                        path.move(position, rail.block, 0.0);
                        double dot = (position.motX * direction.getX()) +
                                     (position.motY * direction.getY()) +
                                     (position.motZ * direction.getZ());

                        if (dot >= 0.0) {
                            order = -1;
                        } else {
                            order = 1;
                        }

                        // Restore position motion for actual movement (re-use Position)
                        position.motX = direction.getX();
                        position.motY = direction.getY();
                        position.motZ = direction.getZ();
                    }
                    remainingDistance -= path.move(position, rail.block, remainingDistance);
                }

                // Any remaining distance, simply 'assume' from the last-known direction information
                position.posX += position.motX * remainingDistance;
                position.posY += position.motY * remainingDistance;
                position.posZ += position.motZ * remainingDistance;
            } else {
                // No distance is set: refresh position on the current rail without moving
                rail = rails.get(railIndex);
                RailPath path = rail.getPath();

                position.motX = direction.getX();
                position.motY = direction.getY();
                position.motZ = direction.getZ();
                path.move(position, rail.block, 0.0);
            }

            // Refresh vectors
            this._position = new Vector(
                    position.posX - this.member.getEntity().loc.getX(),
                    position.posY - this.member.getEntity().loc.getY(),
                    position.posZ - this.member.getEntity().loc.getZ());
            this._up = new Vector(position.upX, position.upY, position.upZ);
            this._forward = new Vector(position.motX, position.motY, position.motZ);
            if (!this._front) {
                this._forward.multiply(-1.0);
            }
        }

    }
}
