package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Previews the eye coordinates for a number of ticks
 */
public class FirstPersonEyePreview {
    public final CartAttachmentSeat seat;
    public final Player player;
    private int remaining = 0;
    private PitchSwappedEntity<VirtualEntity> entity;

    public FirstPersonEyePreview(CartAttachmentSeat seat, Player player) {
        this.seat = seat;
        this.player = player;
    }

    public boolean updateRemaining() {
        if (remaining == 1) {
            remaining = 0;
            handleStop();
            return false;
        } else if (remaining > 1) {
            remaining--;
            return true;
        } else {
            return true;
        }
    }

    public boolean start(int numTicks, Matrix4x4 eyeTransform) {
        if (remaining == 0 && numTicks > 0) {
            handleStart(eyeTransform);
            remaining = numTicks;
            return true;
        } else if (remaining > 0 && numTicks == 0) {
            remaining = 0;
            handleStop();
            return false;
        } else {
            remaining = numTicks;
            return false;
        }
    }

    public void stop() {
        if (remaining > 0) {
            remaining = 0;
            handleStop();
        }
    }

    private void handleStart(Matrix4x4 eyeTransform) {
        entity = PitchSwappedEntity.create(PlayerUtil.getVehicleMountController(player), () -> {
            VirtualEntity entity = new VirtualEntity(seat.getManager());
            entity.setEntityType(EntityType.ARMOR_STAND);
            entity.setSyncMode(SyncMode.NORMAL);
            entity.setUseMinecartInterpolation(seat.isMinecartInterpolation());

            // We spectate an invisible armorstand that has MARKER set
            // This causes the spectator to view from 0/0/0, avoiding having to do any extra offsets
            entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            entity.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            entity.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));

            return entity;
        });
        entity.spawn(eyeTransform, seat.calcMotion());
        entity.spectate();
    }

    private void handleStop() {
        entity.destroy();
    }

    public void updatePosition(Matrix4x4 eyeTransform) {
        entity.updatePosition(eyeTransform);
    }

    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
    }
}
