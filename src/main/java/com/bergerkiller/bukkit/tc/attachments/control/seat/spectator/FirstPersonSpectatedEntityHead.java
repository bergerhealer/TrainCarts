package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
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
 */
class FirstPersonSpectatedEntityHead extends FirstPersonSpectatedEntity {
    private final ItemStack skullItem;
    private VirtualArmorStandItemEntity skull;
    private VirtualArmorStandItemEntity skullAlt; // 180 degree flip

    public FirstPersonSpectatedEntityHead(CartAttachmentSeat seat, FirstPersonViewSpectator view, Player player) {
        super(seat, view, player);
        this.skullItem = SeatedEntityHead.createSkullItem(player);
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        skull = createSkull();
        skullAlt = createSkull();

        // Position primary entity in the normal way
        skull.setItem(ItemTransformType.SMALL_HEAD, skullItem);
        skull.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());
        skull.syncPosition(true);

        // Position the alt entity with head/body 180 degrees rotated at pitch 180
        skullAlt.setItem(ItemTransformType.SMALL_HEAD, null);
        skullAlt.updatePosition(eyeTransform, new Vector(
                computeAltPitch(skull.getYawPitchRoll().getX(), 179.0f),
                skull.getYawPitchRoll().getY(),
                0.0));
        skullAlt.syncPosition(true);

        // Spawn the entities for the Player
        skull.spawn(player, seat.calcMotion());
        skullAlt.spawn(player, new Vector());

        // Spectate primary entity
        spectate(skull.getEntityId());
    }

    private VirtualArmorStandItemEntity createSkull() {
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
    }

    @Override
    public void stop() {
        spectate(-1);
        skull.destroy(player);
        skullAlt.destroy(player);
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        skull.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(this.skull.getSyncPitch(), this.skull.getLivePitch())) {
            // Spectate other entity
            spectate(skullAlt.getEntityId());

            // Make original invisible, make alt visible
            skull.setItem(ItemTransformType.SMALL_HEAD, null);
            skullAlt.setItem(ItemTransformType.SMALL_HEAD, skullItem);

            // Swap them out, continue working with alt
            {
                VirtualArmorStandItemEntity tmp = this.skull;
                this.skull = this.skullAlt;
                this.skullAlt = tmp;
            }

            // Give the fake player full sync pitch
            skull.updatePosition(eyeTransform, eyeTransform.getYawPitchRoll());

            // Sync these right away
            skull.syncMetadata();
            skullAlt.syncMetadata();
        }

        // Calculate what new alt-pitch should be used. This swaps over at the 180-degree mark
        {
            float newAltPitch = computeAltPitch(this.skull.getYawPitchRoll().getX(),
                                                this.skullAlt.getLivePitch());
            boolean isAltPitchDifferent = (newAltPitch != this.skullAlt.getLivePitch());

            // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
            this.skullAlt.updatePosition(eyeTransform, new Vector(
                    newAltPitch, this.skull.getYawPitchRoll().getY(), 0.0));

            if (isAltPitchDifferent) {
                // We cannot safely rotate between these two - it requires a respawn to do this quickly
                this.skullAlt.destroy(player);
                this.skullAlt.syncPosition(true);
                this.skullAlt.spawn(player, new Vector());
            }
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        skull.syncPosition(absolute);
        skullAlt.syncPosition(absolute);
    }
}
