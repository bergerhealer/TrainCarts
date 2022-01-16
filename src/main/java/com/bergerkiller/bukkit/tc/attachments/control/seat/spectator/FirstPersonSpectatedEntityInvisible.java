package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.Util;
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
    private VirtualEntity entity;
    private VirtualEntity entityAlt; // 180 degree rotation glitch

    public FirstPersonSpectatedEntityInvisible(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        super(seat, view, vmc);
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        entity = createEntity();
        entityAlt = createEntity();

        // Position primary entity in the normal way
        entity.updatePosition(eyeTransform);
        entity.syncPosition(true);

        // Position the alt entity with head/body 180 degrees rotated at pitch 180
        entityAlt.updatePosition(entity.getPos(), new Vector(
                computeAltPitch(entity.getYawPitchRoll().getX(), 179.0f),
                entity.getYawPitchRoll().getY(),
                0.0));
        entityAlt.syncPosition(true);

        // Spawn the entities for the Player
        entity.spawn(player, seat.calcMotion());
        entityAlt.spawn(player, new Vector());

        // Spectate primary entity
        Util.startSpectating(vmc, entity.getEntityId());
    }

    private VirtualEntity createEntity() {
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
    }

    @Override
    public void stop() {
        Util.stopSpectating(vmc, entity.getEntityId());
        entity.destroy(player);
        entityAlt.destroy(player);
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        entity.updatePosition(eyeTransform);

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(this.entity.getSyncPitch(), this.entity.getLivePitch())) {
            // Spectate other entity
            Util.swapSpectating(vmc, entity.getEntityId(), entityAlt.getEntityId());

            // Swap them out, continue working with alt
            {
                VirtualEntity tmp = this.entity;
                this.entity = this.entityAlt;
                this.entityAlt = tmp;
            }

            // Give the fake player full sync pitch
            entity.updatePosition(eyeTransform);
        }

        // Calculate what new alt-pitch should be used. This swaps over at the 180-degree mark
        {
            float newAltPitch = computeAltPitch(this.entity.getYawPitchRoll().getX(),
                                                this.entityAlt.getLivePitch());
            boolean isAltPitchDifferent = (newAltPitch != this.entityAlt.getLivePitch());

            // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
            this.entityAlt.updatePosition(entity.getPos(), new Vector(
                    newAltPitch, this.entity.getYawPitchRoll().getY(), 0.0));

            if (isAltPitchDifferent) {
                // We cannot safely rotate between these two - it requires a respawn to do this quickly
                this.entityAlt.destroy(player);
                this.entityAlt.syncPosition(true);
                this.entityAlt.spawn(player, new Vector());
            }
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
        entityAlt.syncPosition(absolute);
    }
}
