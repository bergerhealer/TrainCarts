package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutCameraHandle;

/**
 * A type of entity that can be spectated, that has a particular appearance
 * when the player views himself in third-person (F5) view.
 */
public abstract class FirstPersonSpectatedEntity {
    protected final CartAttachmentSeat seat;
    protected final FirstPersonViewSpectator view;
    protected final Player player;

    public FirstPersonSpectatedEntity(CartAttachmentSeat seat, FirstPersonViewSpectator view, Player player) {
        this.seat = seat;
        this.view = view;
        this.player = player;
    }

    /**
     * Spawns whatever entity needs to be spectated, and starts spectating that entity
     *
     * @param baseTransform Base transformation matrix. If an eye transformation was 
     *                      configured, this is included in this transformation.
     */
    public abstract void start(Matrix4x4 baseTransform);

    /**
     * Stops spectating and despawns the entity/others used for spectating
     */
    public abstract void stop();

    /**
     * Updates the first-person view
     *
     * @param baseTransform Base transformation matrix. If an eye transformation was 
     *                      configured, this is included in this transformation.
     */
    public abstract void updatePosition(Matrix4x4 baseTransform);

    public abstract void syncPosition(boolean absolute);

    /**
     * Makes the player spectate a certain entity
     *
     * @param entityId ID of the entity to spectate, -1 to stop spectating
     */
    protected void spectate(int entityId) {
        PacketPlayOutCameraHandle packet = PacketPlayOutCameraHandle.T.newHandleNull();
        packet.setEntityId((entityId == -1) ? player.getEntityId() : entityId);
        PacketUtil.sendPacket(player, packet, false);
    }

    protected static float computeAltPitch(double currPitch, float currAltPitch) {
        currPitch = MathUtil.wrapAngle(currPitch); // Wrap between -180 and 180 degrees

        if (currPitch > 90.0) {
            return 181.0f;
        } else if (currPitch < -90.0) {
            return 179.0f;
        } else {
            return currAltPitch;
        }
    }

    public static FirstPersonSpectatedEntity create(CartAttachmentSeat seat, FirstPersonViewSpectator view, Player player) {
        if (view.getLiveMode() == FirstPersonViewMode.INVISIBLE) {
            return new FirstPersonSpectatedEntityInvisible(seat, view, player);
        } else {
            return new FirstPersonSpectatedEntityPlayer(seat, view, player);
        }
    }
}
