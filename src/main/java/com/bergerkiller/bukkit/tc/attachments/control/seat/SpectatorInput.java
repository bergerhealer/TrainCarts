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
            currentEyeTransform = currentEyeTransform.clone();
            currentEyeTransform.rotate(this.orientation);
            Vector pyr = currentEyeTransform.getYawPitchRoll();
            sendRotation((float) pyr.getX(), (float) pyr.getY());
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
