package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Collections;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.mutable.LocationAbstract;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
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

    /**
     * Gets the other wheel compared to a wheel.
     * Passing back() returns front() and vice versa.
     * 
     * @param wheel
     * @return other wheel
     */
    public Wheel other(Wheel wheel) {
        return (this._front == wheel) ? this._back : this._front;
    }

    /**
     * Gets the wheel thats moving forwards at the front of the cart
     * 
     * @return forwards moving wheel
     */
    public Wheel movingForwards() {
        return this._owner.isOrientationInverted() ? this._back : this._front;
    }

    /**
     * Gets the wheel thats moving backwards at the back of the cart
     * 
     * @return backwards moving wheel
     */
    public Wheel movingBackwards() {
        return this._owner.isOrientationInverted() ? this._front : this._back;
    }

    /**
     * Whether either wheel has a wheel distance set. If none is set, then the Minecart moves
     * along a singular point.
     * 
     * @return True if wheel distance is set
     */
    public boolean hasWheelDistance() {
        return this._front.getDistance() > MIN_WHEEL_DISTANCE || this._back.getDistance() > MIN_WHEEL_DISTANCE;
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
            double diff = (back().getDistance() - front().getDistance());

            this._position = new Vector();
            this._position.add(front().getPosition());
            this._position.add(back().getPosition());
            if (diff != 0.0) {
                Vector dir = front().getPosition().clone().subtract(back().getPosition());
                double n = MathUtil.getNormalizationFactor(dir);
                if (n < 1e10) {
                    dir.multiply(n * diff);
                } else {
                    // Unusable, fallback
                    dir = getOwner().getOrientationForward().multiply(diff);
                }
                this._position.add(dir);
            }
            this._position.multiply(0.5);
            this._position.add(this._owner.getEntity().loc.vector());
        }
        return this._position;
    }

    public double getBankingRoll() {
        return this._bankingRoll;
    }

    public void startTeleport() {
        this._front._invalid = true;
        this._back._invalid = true;
        this._position = null;
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
        TrainProperties props = (this._owner.isUnloaded() ? null : this._owner.getGroup().getProperties());
        if (props != null && props.getBankingStrength() != 0.0) {
            // Get the orientation difference between the current and last rotation
            Quaternion q = Quaternion.divide(new_orientation, this.getLastOrientation());

            // Calculate the forward vector - the x (left/right) is what is interesting
            // This stores the change in direction, which allows calculation of centripetal force
            double centripetalForceStep = q.forwardVector().getX();
            if (MathUtil.isHeadingTo(_owner.getDirection(), new_orientation.forwardVector())) {
                centripetalForceStep = -centripetalForceStep;
            }

            // Higher speed = more centripetal force
            centripetalForceStep *= _owner.getRealSpeedLimited();

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
            double angle = (double) MathUtil.atan2(this._centripetalForce, 1.0 / props.getBankingStrength());
            if (props.getBankingSmoothness() == 0.0) {
                this._bankingRoll = angle;
            } else {
                this._bankingRoll += (1.0 / props.getBankingSmoothness()) * ( angle - this._bankingRoll);
            }
        } else {
            this._bankingRoll = 0.0;
        }

        // Refresh member orientation through rail logic (when not unloaded)
        if (this._owner.isUnloaded()) {
            this._owner.setOrientation(new_orientation);
        } else {
            this._owner.getRailLogic().onUpdateOrientation(this._owner, new_orientation);
        }
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
        private final Vector _displayPosition = new Vector(); // Position of the wheel when displayed (visual)
        private final Vector _position = new Vector(); // Position is relative to the minecart position
        private final Vector _forward = new Vector();  // The forward direction vector of this wheel
        private final Vector _up = new Vector();       // Up vector, used for angling the Minecart around the wheels
        private boolean _invalid = true;   // Position/forward/up vectors are invalid, an update() is required.
        private boolean _displayInvalid = true; // displayPosition is invalid and must be recalculated
        private boolean _oriented;       // Last-known state whether we are moving in the same direction as orientation or not
        private final RailPath.Position _railPosition = new RailPath.Position(); // Buffered and re-used

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
                this._invalid = true;
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
         * Gets the distance between this wheel and the nearest edge of the Minecart
         * 
         * @return edge distance
         */
        public double getEdgeDistance() {
            double edgeDistance = 0.5 * this.member.getEntity().getWidth();
            edgeDistance -= this._distance;
            return edgeDistance;
        }

        /**
         * Gets the center-relative position of this wheel.
         * The center is the exact coordinates of the Minecart itself.
         * 
         * @return center-relative position
         */
        public Vector getPosition() {
            if (this._invalid) {
                this.update(); // Required
            }
            return this._position;
        }

        /**
         * Gets the position of this wheel in world coordinates.
         * 
         * @return absolute position
         */
        public Vector getAbsolutePosition() {
            return this.getPosition().clone().add(this.member.getEntity().loc.vector());
        }

        /**
         * Gets the world coordinates of where the wheel is displayed
         * 
         * @return display position
         */
        public Vector getDisplayPosition() {
            if (this._displayInvalid) {
                if (this._invalid) {
                    this.update(); // Required
                }

                // Below code 'correct' the distance between the wheels
                // The problem with this correction is that wheels derail slightly in curves
                // Its fixing one problem, causing another. Going with accurate rail tracking for now.
                // The true fix would be in the calculation of the position itself. Sometimes you need
                // to walk more distance of track for the same (rotated) orientations...
                /*
                Vector fwd = this.member.getOrientationForward();
                if (this._front) {
                    fwd.multiply(this._distance);
                } else {
                    fwd.multiply(-this._distance);
                }
                Util.setVector(this._displayPosition, this.member.getWheels().getPosition().clone().add(fwd));
                this._displayInvalid = false;
                */

                LocationAbstract loc = this.member.getEntity().loc;
                this._displayPosition.setX(loc.getX() + this._position.getX());
                this._displayPosition.setY(loc.getY() + this._position.getY());
                this._displayPosition.setZ(loc.getZ() + this._position.getZ());
                this._displayInvalid = false;
            }
            return this._displayPosition;
        }

        /**
         * Gets the absolute transformation of this wheel, containing
         * the position translation and the orientation calculations.
         * 
         * @return absolute transformation
         */
        public Matrix4x4 getAbsoluteTransform() {
            Matrix4x4 result = new Matrix4x4();
            getAbsoluteTransform(result);
            return result;
        }

        /**
         * Gets the absolute transformation of this wheel, containing
         * the position translation and the orientation calculations.
         * 
         * @param target to write this information to
         */
        public void getAbsoluteTransform(Matrix4x4 target) {
            target.setIdentity();
            target.translate(this.getDisplayPosition());
            target.rotate(Quaternion.fromLookDirection(this._forward.clone(), this._up.clone()));
        }

        /**
         * Gets the up unit vector. This is the vector upwards, which controls
         * the angle of the Minecart around the set of wheels. This is particularly
         * important for rails like upside-down rails and vertical rails.
         * 
         * @return up vector
         */
        public Vector getUp() {
            if (this._invalid) {
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
            if (this._invalid) {
                this.update(); // Required
            }
            return this._forward;
        }

        /**
         * Recalculates the position of this wheel
         */
        public void update() {
            // Reset invalid
            this._invalid = false;

            // Reset display vector
            this._displayInvalid = true;

            // Find the index of the rails for this member
            List<TrackedRail> rails;
            if (this.member.isUnloaded()) {
                rails = Collections.emptyList();
            } else {
                rails = this.member.getGroup().getRailTracker().getRailInformation();
            }

            int railIndex = -1;
            if (!this.member.isDerailed()) {
                for (int i = 0; i < rails.size(); i++) {
                    TrackedRail rail = rails.get(i);
                    if (rail == this.member.getRailTracker().getRail()) {
                        railIndex = i;
                        break;
                    }
                }
            }

            // If this Minecart is derailed, set the wheels to point into the direction of the orientation
            if (railIndex == -1) {
                Quaternion orientation = member.getOrientation();
                Util.setVector(this._up, orientation.upVector());
                Util.setVector(this._forward, orientation.forwardVector());
                Util.setVector(this._position, this._forward);
                this._position.multiply(this._distance);
                if (!this._front) {
                    this._position.multiply(-1.0);
                }
                return;
            }

            // Start by doing a movement of 0 distance to correctly calculate the movement direction
            // This initializes the running Position object correctly
            RailPath.Position position = this._railPosition;
            position.setLocation(this.member.getEntity().loc);
            position.setMotion(member.getRailTracker().getMotionVector());
            {
                TrackedRail rail = rails.get(railIndex);
                rail.getPath().move(position, rail.state.railBlock(), 0.0);
            }

            // Below code attempts to correct the error between the position and the actual minecart location
            // It does so by picking a different rail that has less error with the current position of the member
            // This fix 'works', but further testing showed it was only needed when more than one track is at
            // a single rails block. By fixing that, this fix was no longer needed.
            // Evidently, this correction is only needed when 'jumping' occurs due to multiple track paths per rail block
            double initial_position_error = position.distanceSquared(this.member.getEntity().loc);
            if (initial_position_error > 1e-5) {
                // Too large an error. Check whether railIndex-1 or railIndex+1 are better suited
                int original_rail_index = railIndex;
                if (original_rail_index > 0) {
                    RailPath.Position prev_position = new RailPath.Position();
                    prev_position.setLocation(this.member.getEntity().loc);
                    prev_position.setMotion(member.getRailTracker().getMotionVector());
                    {
                        TrackedRail prev_rail = rails.get(original_rail_index-1);
                        prev_rail.getPath().move(prev_position, prev_rail.state.railBlock(), 0.0);
                    }

                    double prev_initial_error = prev_position.distanceSquared(this.member.getEntity().loc);
                    if (prev_initial_error < initial_position_error) {
                        prev_position.copyTo(position);
                        initial_position_error = prev_initial_error;
                        railIndex = original_rail_index-1;
                    }
                }
                if (original_rail_index < (rails.size()-1)) {
                    RailPath.Position next_position = new RailPath.Position();
                    next_position.setLocation(this.member.getEntity().loc);
                    next_position.setMotion(member.getRailTracker().getMotionVector());
                    {
                        TrackedRail next_rail = rails.get(original_rail_index+1);
                        next_rail.getPath().move(next_position, next_rail.state.railBlock(), 0.0);
                    }

                    double next_initial_error = next_position.distanceSquared(this.member.getEntity().loc);
                    if (next_initial_error < initial_position_error) {
                        next_position.copyTo(position);
                        initial_position_error = next_initial_error;
                        railIndex = original_rail_index+1;
                    }
                }
            }

            // Flip the direction when the orientation vs front differs
            // When dot is 0.0, we hit an odd 90-degree incline
            // 'Remember' the orientation from previous round in that case
            int order = -1;
            double dot = position.motDot(member.getOrientationForward());
            boolean oriented = (dot > 0.0);
            if (dot >= -0.0001 && dot <= 0.0001) {
                oriented = _oriented;
            }
            _oriented = oriented;
            if (oriented ^ this._front) {
                position.motX = -position.motX;
                position.motY = -position.motY;
                position.motZ = -position.motZ;
                position.reverse = true;
                order = 1;
            }

            if (this._distance > MIN_WHEEL_DISTANCE) {
                // Distance is set: walk the path
                double remainingDistance = this._distance;
                for (int index = railIndex; index >= 0 && index < rails.size() && remainingDistance >= 0.0001; index += order) {
                    TrackedRail rail = rails.get(index);
                    RailPath path = rail.getPath();
                    remainingDistance -= path.move(position, rail.state.railBlock(), remainingDistance);
                }

                // Any remaining distance, simply 'assume' from the last-known direction information
                position.posX += position.motX * remainingDistance;
                position.posY += position.motY * remainingDistance;
                position.posZ += position.motZ * remainingDistance;
            }

            // Refresh vectors
            this._position.setX(position.posX - this.member.getEntity().loc.getX());
            this._position.setY(position.posY - this.member.getEntity().loc.getY());
            this._position.setZ(position.posZ - this.member.getEntity().loc.getZ());

            // Empty paths produce null orientation
            if (position.orientation == null) {
                position.orientation = this.member.getOrientation();
            }

            //TODO: Do we really have to split this into 'forward' and 'up'?
            // Could just keep it a Quaternion storing both. 
            Util.setVector(this._up, position.orientation.upVector());
            Util.setVector(this._forward, position.orientation.forwardVector());
            if (position.motDot(this._forward) < 0.0) {
                this._forward.multiply(-1.0);
            }
            if (!this._front) {
                this._forward.multiply(-1.0);
            }

            // Debug
            if (TCConfig.wheelTrackerDebugEnabled) {
                Util.spawnBubble(position.toLocation(this.member.getEntity().getWorld()));
            }
        }

    }
}
