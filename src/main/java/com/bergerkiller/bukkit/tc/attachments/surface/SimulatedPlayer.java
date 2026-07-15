package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;

/**
 * Stores the state of a player that is simulated while walking on a collision surface.
 * This is a data holder only for now.
 */
public class SimulatedPlayer {
    public static final double SIMULATED_VELOCITY_DRAG_XZ = 0.91;
    public static final double SIMULATED_VELOCITY_DRAG_Y = 0.98;
    public static final double SIMULATED_GRAVITY = 0.08;
    /** Minimum speed under which the player's simulated velocity is considered zero */
    public static double MINIMUM_PLAYER_VELOCITY = 0.003;
    public static final double SIMULATED_JUMP_IMPULSE = 0.42;
    /** Base walking speed of players */
    public static final double SIMULATED_WALK_ACCELERATION = 0.1;

    public final AttachmentViewer viewer;
    public final AttachmentViewer.MovementController pmc;
    public Vector lastPosition;
    public PlayerState lastDebugState = null;
    public Vector position;
    /** Velocity of the player relative to the surface or air. Does not include velocity of the surface itself. */
    public Vector velocity;
    public CollisionSurface lastSurface;
    public boolean flying;
    public boolean lastJumpInput;

    public SimulatedPlayer(AttachmentViewer viewer, Vector position) {
        this(viewer, viewer.controlMovement(), position, position.clone(), new Vector(), CollisionSurface.DISABLED, false);
    }

    public SimulatedPlayer(
            AttachmentViewer viewer,
            AttachmentViewer.MovementController pmc,
            Vector lastPosition,
            Vector position,
            Vector velocity,
            CollisionSurface lastSurface,
            boolean flying
    ) {
        this.viewer = viewer;
        this.pmc = pmc;
        this.lastPosition = lastPosition;
        this.position = position;
        this.velocity = velocity;
        this.lastSurface = lastSurface;
        this.flying = flying;
        this.lastJumpInput = false;
    }

    /**
     * Gets the slipperiness of the surface the player is currently walking on.
     * This controls the rate of speeding up / slowing down when walking on the surface.
     *
     * @return Ground slipperiness
     */
    public double getGroundSlipperiness() {
        // Air / stone surface
        return this.flying ? 1.0 : 0.6;
    }

    /**
     * Gets the player forward vector based on the player's look-at yaw.
     * This is the axis along which forward walking movement occurs.
     *
     * @return Player forward vector
     */
    public Vector getPlayerForward() {
        double yawRad = Math.toRadians(viewer.getPlayer().getEyeLocation().getYaw());
        return new Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }

    /**
     * Applies the velocity Y impulse effect of the player pressing the jump button
     */
    public void applyJumpImpulse() {
        this.velocity.setY(this.velocity.getY() + SIMULATED_JUMP_IMPULSE);
    }

    /**
     * Applies the effects of the player pressing w/a/s/d to do walking. Both used for air and walking on ground.
     * When walking on a surface, the velocity is then projected onto the surface plane.
     *
     * @param acceleration Acceleration from WASD input, with sneak or sprint multiplier already applied
     */
    public void applyWalkingVelocity(Vector acceleration) {
        // Effect of slipperiness is (0.6 / S) ^ 3
        double slipperinessFact = Math.pow(0.6 / getGroundSlipperiness(), 3.0);

        // Apply damping to the existing velocity first, then add acceleration.
        this.velocity.add(acceleration.clone().multiply(slipperinessFact));
    }

    /**
     * Applies the effect of gravity to the velocity of this player
     */
    public void applyGravity() {
        this.velocity.setY(this.velocity.getY() - SIMULATED_GRAVITY);
    }

    /**
     * Applies air/ground drag to the velocity of this player. This caps the movement speed and causes
     * the player to slowly stop again.
     */
    public void applyDrag() {
        final double slipperiness = this.getGroundSlipperiness();
        this.velocity.setX(SIMULATED_VELOCITY_DRAG_XZ * this.velocity.getX() * slipperiness);
        this.velocity.setY(SIMULATED_VELOCITY_DRAG_Y * this.velocity.getY());
        this.velocity.setZ(SIMULATED_VELOCITY_DRAG_XZ * this.velocity.getZ() * slipperiness);
    }

    /**
     * Applies the minimum speed cutoff to the player's velocity, setting it to 0 when moving too slow.
     */
    public void applyMinimumSpeed() {
        if (this.velocity.lengthSquared() < (MINIMUM_PLAYER_VELOCITY * MINIMUM_PLAYER_VELOCITY)) {
            MathUtil.setVector(this.velocity, 0.0, 0.0, 0.0);
        }
    }
}
