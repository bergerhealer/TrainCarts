package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Spectates the head of the armorstand holding a player skull. Used with the HEAD
 * seated entity logic. To properly handle EYE positioning, and avoid glitches
 * when rotating beyond 180 degrees, it spawns its own armorstand.
 * 
 * @deprecated No longer used in favor of {@link FirstPersonSpectatedEntityPlayer} and just
 *             using a skull item there.
 */
@Deprecated
class FirstPersonSpectatedEntityHead extends FirstPersonSpectatedEntity {
    private final ItemStack skullItem;
    private PitchSwappedEntity<VirtualArmorStandItemEntity> skull;

    public FirstPersonSpectatedEntityHead(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        super(seat, view, vmc);
        this.skullItem = SeatedEntityHead.createSkullItem(player);
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        skull = PitchSwappedEntity.create(vmc, () -> {
            VirtualArmorStandItemEntity entity = new VirtualArmorStandItemEntity(seat.getManager());
            entity.setSyncMode(SyncMode.NORMAL);
            entity.setUseMinecartInterpolation(seat.isMinecartInterpolation());
            if (!view.getEyePosition().isDefault() || view.getLiveMode() == FirstPersonViewMode.THIRD_P) {
                // Position it where the eyes are at, instead of the bottom of the head model
                entity.setRelativeOffset(0.0, 0.24, 0.0);
            } else {
                // Compensate for offset in SeatedEntityHead so the head rests where it should be
                // The camera will be below where the head is. This is sometimes ok. If not, it can
                // be changed by modifying the first-person view position.
                entity.setRelativeOffset(0.0, -0.215, 0.0);
            }
            entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, FakePlayerSpawner.UPSIDEDOWN.getPlayerName());
            entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
            return entity;
        });
        skull.beforeSwap(swapped -> {
            // Make original invisible, make alt visible
            skull.entity.setItem(ItemTransformType.SMALL_HEAD, null);
            swapped.setItem(ItemTransformType.SMALL_HEAD, skullItem);
        });
        skull.entity.setItem(ItemTransformType.SMALL_HEAD, skullItem);
        skull.entityAlt.setItem(ItemTransformType.SMALL_HEAD, null);
        skull.spawn(eyeTransform, new Vector());
        skull.spectate();
    }

    @Override
    public void stop() {
        skull.destroy();
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        skull.updatePosition(eyeTransform);
        skull.entity.syncMetadata(); // Ensures pose is smooth by updating every tick
    }

    @Override
    public void syncPosition(boolean absolute) {
        skull.syncPosition(absolute);
    }

    @Override
    public VirtualEntity getCurrentEntity() {
        return skull.entity;
    }
}
