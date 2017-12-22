package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class WheelTrackerMember {
    private final MinecartMember<?> _owner;
    private final WheelTracker _front;
    private final WheelTracker _back;
    private Quaternion _memberDir = null;
    private Quaternion _rotation = null;
    private Quaternion _rotation_last = null;
    private Vector _position = null;
    private double _centripetalForce = 0.0;
    private double _bankingRoll = 0.0;

    public WheelTrackerMember(MinecartMember<?> owner) {
        this._owner = owner;
        this._front = new WheelTracker(this, true);
        this._back = new WheelTracker(this, false);
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

    public Quaternion getLastRotation() {
        if (this._rotation_last == null) {
            this._rotation_last = this.getRotation();
        }
        return this._rotation_last;
    }
    
    /**
     * Obtains the rotation transformation that is applied
     * 
     * @return rotation
     */
    public Quaternion getRotation() {
        if (this._rotation == null) {
            Vector dir = front().getPosition().clone().subtract(back().getPosition());
            if (dir.lengthSquared() < 0.0001) {
                dir = this.getMemberDirection().forwardVector();
            }
            Vector up = front().getUp().clone().add(back().getUp()).multiply(0.5);
            if (up.lengthSquared() < 0.0001) {
                up = this.getMemberDirection().upVector();
            }
            this._rotation = Quaternion.fromLookDirection(dir, up);
        }
        return this._rotation;
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

    /**
     * Gets the (cached) direction a Minecart faces based on its own yaw/pitch/roll
     * 
     * @return member direction from entity yaw/pitch/roll
     */
    public Quaternion getMemberDirection() {
        if (this._memberDir == null) {
            this._memberDir = new Quaternion();
            this._memberDir.rotateYawPitchRoll(
                    _owner.getEntity().loc.getPitch(),
                    _owner.getEntity().loc.getYaw() + 90.0f,
                    _owner.getRoll()
            );
        }
        return this._memberDir;
    }

    public double getBankingRoll() {
        return this._bankingRoll;
    }
    
    public void update() {
        this._rotation_last = this.getRotation();
        this._memberDir = null; // Reset
        this._rotation = null; // Reset
        this._position = null; // Reset
        this._front.update();
        this._back.update();

        // Calculate banking effects
        TrainProperties props = this._owner.getGroup().getProperties();
        if (props.getBankingStrength() != 0.0) {
            // Get the orientation difference between the current and last rotation
            Quaternion q = Quaternion.divide(getRotation(), getLastRotation());

            // Calculate the forward vector - the x (left/right) is what is interesting
            // This stores the change in direction, which allows calculation of centripetal force
            double centripetalForceStep = q.forwardVector().getX();
            if (MathUtil.isHeadingTo(_owner.getDirection(), getRotation().forwardVector())) {
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
    }
}
