package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;

/**
 * Initializes and tracks the player input, which is used to move the spectator
 * camera around.
 */
class SpectatorInput {
    private int blindTicks = 0;
    private final Matrix4x4 lastEyeTransform = new Matrix4x4();
    private Player player;

    /**
     * Resets the player orientation to look at 0/0 and starts
     * reading yaw/pitch orientation details for this player.
     *
     * @param player
     */
    public void start(Player player) {
        this.player = player;
        this.blindTicks = CommonUtil.getServerTicks() + 5; // no input for 5 ticks
        sendRotation(0.0f, 0.0f); // reset
    }

    /**
     * Stops intercepting input. Resets the look orientation to
     * what the eye last looked at.
     */
    public void stop() {
        if (this.player != null) {
            Vector pyr = lastEyeTransform.getYawPitchRoll();
            sendRotation((float) pyr.getX(), (float) pyr.getY());
        }
        this.player = null;
        this.blindTicks = 0;
    }

    /**
     * Updates the input matrix with the relative rotation of the Player
     *
     * @param eyeTransform
     */
    public void update(Matrix4x4 eyeTransform) {
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
        eyeTransform.rotateY(-eye.getYaw());
        eyeTransform.rotateX(eye.getPitch());
        lastEyeTransform.set(eyeTransform);
    }

    private void sendRotation(float pitch, float yaw) {
        PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, yaw, pitch);
        p.setRotationRelative(false);
        PacketUtil.sendPacket(player, p);
    }
}
