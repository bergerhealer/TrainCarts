package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Tracks the position of a wheel by looking at past positions, or alternatively,
 * walking the tracks to satisfy the wheel-to-center distance.
 */
public class WheelTracker {
    private final MinecartMember<?> member;
    private final boolean _front;
    private double _distance = 0.4; // Distance from the center this wheel is at
                                    // This will eventually be modified based on the model that is applied
    private Vector _position = new Vector(); // Position is relative to the minecart position

    public WheelTracker(MinecartMember<?> member, boolean front) {
        this.member = member;
        this._front = front;
    }

    /**
     * Gets the center-relative position of this wheel.
     * The center is the exact coordinates of the Minecart itself.
     * 
     * @return center-relative position
     */
    public Vector getPosition() {
        return this._position;
    }

    public void update() {
        if (true) return; // DISABLE

        CommonMinecart<?> entity = member.getEntity();
        if (_front) {
            // Walk the tracks
            
            
            
            
            
        } else {
            Matrix4x4 transform = new Matrix4x4();
            transform.rotateYawPitchRoll(entity.loc.getYaw() + 90.0f, entity.loc.getPitch(), member.getRoll());
            transform.translate(0.0, 0.0, _front ? _distance : -_distance);
            _position = transform.toVector();
        }
                
        // Debug: show some effect at the position
        Location loc = entity.getLocation().add(_position);
        loc.getWorld().spawnParticle(Particle.WATER_BUBBLE, loc.getX(), loc.getY(), loc.getZ(), 1);
    }
}
