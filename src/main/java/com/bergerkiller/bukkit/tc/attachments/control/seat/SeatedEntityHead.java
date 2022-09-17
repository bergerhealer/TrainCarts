package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Seated entity that is using flying skull with a player skin on an
 * armor stand. Some types of mobs are also supported as skulls.
 */
public class SeatedEntityHead extends SeatedEntity {
    private VirtualArmorStandItemEntity skull;

    public SeatedEntityHead(CartAttachmentSeat seat) {
        super(seat);
    }

    public int getSkullEntityId() {
        return skull.getEntityId();
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 1.0, 0.0);
    }

    @Override
    public Vector getFirstPersonCameraOffset() {
        return new Vector(0.0, 0.215, 0.0);
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            // Despawn/hide original entity
            if (entity != viewer.getPlayer() && !isDummyPlayerDisplayed()) {
                hideRealPlayer(viewer);
            }

            if (skull == null) {
                skull = new VirtualArmorStandItemEntity(seat.getManager());
                skull.setSyncMode(SyncMode.ITEM);
                skull.setUseMinecartInterpolation(seat.isMinecartInterpolation());
                skull.setItem(ItemTransformType.SMALL_HEAD, createSkullItem(entity));
                skull.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, FakePlayerSpawner.UPSIDEDOWN.getPlayerName());
                skull.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
                updateFocus(seat.isFocused());
                skull.updatePosition(seat.getTransform(), getCurrentHeadRotationQuat(seat.getTransform()));
                skull.syncPosition(true);
            }

            // Spawn and send head equipment
            skull.spawn(viewer, seat.calcMotion());
        } else if (!isEmpty()) {
            // Default behavior for non-player entities is just to mount them
            viewer.getVehicleMountController().mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            if (skull != null) {
                skull.destroy(viewer);

                // Force a reset when the passenger changes
                if (!skull.hasViewers()) {
                    skull = null;
                }
            }

            // Show real player again
            if (viewer.getPlayer() != entity && !isDummyPlayerDisplayed()) {
                showRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Unmount for generic entities
            viewer.getVehicleMountController().unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        if (skull != null) {
            skull.updatePosition(transform, getCurrentHeadRotationQuat(transform));
            skull.syncMetadata(); // Ensures pose is smooth by updating every tick
        }
        updateVehicleMountPosition(transform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (skull != null) {
            skull.syncPosition(absolute);
        }
        syncVehicleMountPosition(absolute);
    }

    @Override
    public void updateFocus(boolean focused) {
        if (skull != null) {
            skull.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING | EntityHandle.DATA_FLAG_ON_FIRE, focused);
            skull.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER, focused);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (skull != null && entityId == skull.getEntityId()) {
            return true;
        }
        return false;
    }

    /**
     * Creates a skull item representing an Entity. If not possible, returns null.
     *
     * @param entity Entity for which the skull is. Null creates a dummy one.
     * @return skull item best representing this entity, null otherwise
     */
    public static ItemStack createSkullItem(Entity entity) {
        if (entity == null || entity instanceof Player) {
            // For players, or the dummy player skin
            GameProfileHandle profile = entity == null
                    ? FakePlayerSpawner.createDummyPlayerProfile() : GameProfileHandle.getForPlayer((Player) entity);
            return ItemUtil.createPlayerHeadItem(profile);
        } else {
            //TODO: Skeleton and such maybe?
            return null;
        }
    }
}
