package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class WheelTrackerMember {
    private final MinecartMember<?> _owner;
    private final WheelTracker _front;
    private final WheelTracker _back;
    private Quaternion _orientation_last = null;
    private Vector _position = null;
    private double _centripetalForce = 0.0;
    private double _bankingRoll = 0.0;

    public WheelTrackerMember(MinecartMember<?> owner) {
        this._owner = owner;
        this._front = new WheelTracker(owner, true);
        this._back = new WheelTracker(owner, false);
    }

    public MinecartMember<?> getOwner() {
        return this._owner;
    }

    public WheelTracker front() {
        return this._front;
    }

    public WheelTracker back() {
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
}
