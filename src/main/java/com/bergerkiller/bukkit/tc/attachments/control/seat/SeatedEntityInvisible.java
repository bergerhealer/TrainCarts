package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Seated entity that only makes the passenger invisible. Nothing is displayed
 * sitting in the seat.
 */
public class SeatedEntityInvisible extends SeatedEntity {

    public SeatedEntityInvisible(CartAttachmentSeat seat) {
        super(seat);
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 0.0, 0.0);
    }

    @Override
    public Vector getFirstPersonCameraOffset() {
        return new Vector(0.0, 0.0, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            // Despawn original player entity
            if (entity != viewer && !isDummyPlayerDisplayed()) {
                hideRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Make other types of entities invisible
            {
                DataWatcher metaTmp = new DataWatcher();
                metaTmp.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entity.getEntityId(), metaTmp, true));
            }

            // Mount them so they stay near the vehicle
            PlayerUtil.getVehicleMountController(viewer).mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            // Show real player again
            if (viewer != entity && !isDummyPlayerDisplayed()) {
                showRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Unmount for generic entities
            PlayerUtil.getVehicleMountController(viewer).unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);

            // Make entity visible again by resetting metadata
            {
                DataWatcher metaTmp = EntityHandle.fromBukkit(this.entity).getDataWatcher();
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entity.getEntityId(), metaTmp, true));
            }
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        updateVehicleMountPosition(transform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        syncVehicleMountPosition(absolute);
    }

    @Override
    public void updateFocus(boolean focused) {
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return false;
    }
}
