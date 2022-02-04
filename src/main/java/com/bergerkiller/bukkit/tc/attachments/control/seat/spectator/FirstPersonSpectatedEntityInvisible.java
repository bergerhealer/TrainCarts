package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * An invisible entity that is spectated. The player sees nobody sitting in the
 * seat he is occupying.
 * Also used for the third-person view mode, as the mechanics are identical except
 * that a different offset is used (potentially).
 */
class FirstPersonSpectatedEntityInvisible extends FirstPersonSpectatedEntity {
    private PitchSwappedEntity<VirtualEntity> entity;

    public FirstPersonSpectatedEntityInvisible(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        super(seat, view, vmc);
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        entity = PitchSwappedEntity.create(vmc, () -> {
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

    @Override
    public void stop() {
        entity.destroy();
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        entity.updatePosition(eyeTransform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
    }

    @Override
    public VirtualEntity getCurrentEntity() {
        return entity.entity;
    }
}
