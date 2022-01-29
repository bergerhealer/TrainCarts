package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;

/**
 * Initializes and tracks the player input, which is used to move the spectator
 * camera around.
 */
class SpectatorInput {
    private int blindTicks = 0;
    private final Quaternion orientation = new Quaternion();
    private Player player;
    private float yawLimit = 360.0f;
    private boolean wasUpsideDown = false;
    private double yawOffset = 0.0; // Used to make upside-down input look good

    /**
     * Resets the player orientation to look at 0/0 and starts
     * reading yaw/pitch orientation details for this player.
     *
     * @param player
     */
    public void start(Player player, float yawLimit) {
        this.player = player;
        this.blindTicks = CommonUtil.getServerTicks() + 5; // no input for 5 ticks
        this.orientation.setIdentity();
        this.yawLimit = yawLimit;
        sendRotation(0.0f, 0.0f); // reset
    }

    /**
     * Stops intercepting input. Resets the look orientation to
     * what the eye last looked at.
     */
    public void stop(Matrix4x4 currentEyeTransform) {
        if (this.player != null) {
            FirstPersonView.HeadRotation headRot = FirstPersonView.HeadRotation.compute(currentEyeTransform);
            headRot = headRot.ensureLevel();
            sendRotation(headRot.pitch, headRot.yaw);
        }
        this.player = null;
        this.blindTicks = 0;
    }

    /**
     * Gets the current relative orientation of the Player's view
     *
     * @return orientation
     */
    public Quaternion get() {
        return this.orientation;
    }

    public void applyTo(Matrix4x4 transform) {
        //transform.rotate(this.orientation);

        Vector inpos = transform.toVector();
        Vector inpyr = transform.getYawPitchRoll();
        Vector playerpyr = this.orientation.getYawPitchRoll();

        // Apply player input before roll is applied to prevent weirdness
        transform.setIdentity();
        transform.translate(inpos);
        transform.rotateY(-inpyr.getY());
        transform.rotateX(inpyr.getX());
        transform.rotateY(-playerpyr.getY());
        transform.rotateX(playerpyr.getX());
        transform.rotateZ(inpyr.getZ());

        // This stuff down below works, but it causes pitch to occur along the yaw instead
        // of along the vehicle. This causes some really bizar effects.
        // Kept here for the upside-down logic.

        /*
        Vector pos = transform.toVector();
        Vector pyr1 = transform.getYawPitchRoll();
        Vector pyr2 = this.orientation.getYawPitchRoll();

        double combinedPitch = MathUtil.wrapAngle(pyr1.getX() + pyr2.getX());
        double inputyaw;

        if (Math.abs(combinedPitch) >= 90.0) {
            // Upside-down

            // Went from up-right to upside-down
            if (!wasUpsideDown) {
                wasUpsideDown = true;

                // Store the relative yaw, as from now on it all gets inverted
                yawOffset = 0.5 * yawOffset + pyr2.getY();

                // Avoid run-away
                yawOffset = MathUtil.wrapAngle(yawOffset);
                
                CommonUtil.broadcast("UPSIDEDOWN " + yawOffset);
            }

            inputyaw = 2.0 * yawOffset - pyr2.getY();
        } else {
            // Up-right
            if (wasUpsideDown) {
                wasUpsideDown = false;

                // Input is flipped, get an appropriate yaw offset to look in the same direction
                yawOffset = 2.0 * yawOffset - 2.0 * pyr2.getY();

                // Avoid run-away
                yawOffset = MathUtil.wrapAngle(yawOffset);
                
                CommonUtil.broadcast("UPRIGHT " + yawOffset);
            }
            inputyaw = yawOffset + pyr2.getY();
        }

        transform.setIdentity();
        transform.translate(pos);
        transform.rotateY(-inputyaw);
        transform.rotateX(pyr1.getX());
        transform.rotateY(-pyr1.getY());
        transform.rotateX(pyr2.getX());

        
        transform.rotateZ(pyr1.getZ());
        */
    }

    /**
     * Updates the view orientation of the Player
     *
     * @param eyeTransform
     */
    public void update() {
        if (player == null) {
            return; // No player inside - no input
        }
        if (blindTicks != 0) {
            if (CommonUtil.getServerTicks() >= blindTicks) {
                blindTicks = 0;
            } else {
                return; // Identity - input disabled until client sync'd up
            }
        }

        Location eye = player.getEyeLocation();

        // Limit yaw, if needed
        float headYaw = eye.getYaw();
        if (headYaw > this.yawLimit) {
            correctYaw(this.yawLimit - headYaw);
            headYaw = this.yawLimit;
        } else if (headYaw < -this.yawLimit) {
            correctYaw(-this.yawLimit - headYaw);
            headYaw = -this.yawLimit;
        }

        // Update orientation
        this.orientation.setIdentity();
        this.orientation.rotateY(-headYaw);
        this.orientation.rotateX(eye.getPitch());
    }

    private void correctYaw(float correction) {
        PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, correction, 0.0f));
    }

    private void sendRotation(float pitch, float yaw) {
        PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, yaw, pitch);
        p.setRotationRelative(false);
        PacketUtil.sendPacket(player, p);
    }
}
