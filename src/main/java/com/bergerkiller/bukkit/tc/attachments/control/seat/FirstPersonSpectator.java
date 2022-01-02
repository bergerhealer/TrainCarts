package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonSpectatedEntity;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonSpectatedEntityPlayer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutCameraHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Makes the player spectate an Entity and then moves that Entity to
 * move the camera around.
 */
public class FirstPersonSpectator extends FirstPersonDefault {
    private FirstPersonSpectatedEntity _spectatedEntity = null;

    public FirstPersonSpectator(CartAttachmentSeat seat) {
        super(seat);
    }

    @Override
    public boolean isFakeCameraUsed() {
        return true;
    }

    @Override
    public void makeVisible(Player viewer) {
        super.makeVisible(viewer); // Puts the viewer in a fake mount, out of view
        setSelfVisible(viewer, false); // Make the viewer completely invisible to himself

        this._spectatedEntity = new FirstPersonSpectatedEntityPlayer(seat, viewer);
        this._spectatedEntity.start();
    }

    @Override
    public void makeHidden(Player viewer) {
        setSelfVisible(viewer, true); // Make viewer visible to himself again (restore)
        super.makeHidden(viewer); // Release viewer from the fake mount

        // Release camera of spectated entity & destroy it
        if (_spectatedEntity != null) {
            _spectatedEntity.stop();
            _spectatedEntity = null;

            // Stop spectating by spectating the player itself
            PacketPlayOutCameraHandle packet = PacketPlayOutCameraHandle.T.newHandleNull();
            packet.setEntityId(viewer.getEntityId());
            PacketUtil.sendPacket(viewer, packet, false);
        }
    }

    @Override
    public void onTick() {
        // Update spectated entity
        if (_spectatedEntity != null) {
            _spectatedEntity.updatePosition(seat.getTransform());
        }
    }

    @Override
    public void onMove(boolean absolute) {
        super.onMove(absolute); // Moves the real, actual player around

        // Move the spectated entity
        if (_spectatedEntity != null) {
            _spectatedEntity.syncPosition(absolute);
        }
    }

    private void setSelfVisible(Player viewer, boolean visible) {
        if (visible) {
            // Restore original metadata
            DataWatcher metaTmp = EntityUtil.getDataWatcher(viewer);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(viewer.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            // Make the real player invisible using a metadata change
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(viewer.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        }
    }
}
