package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
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
    public void makeVisible(Player viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            // Despawn/hide original entity
            if (entity != viewer && !isDummyPlayerDisplayed()) {
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
            PlayerUtil.getVehicleMountController(viewer).mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        if (isPlayer() || isDummyPlayerDisplayed()) {
            if (skull != null) {
                skull.destroy(viewer);

                // Force a reset when the passenger changes
                if (!skull.hasViewers()) {
                    skull = null;
                }
            }

            // Show real player again
            if (viewer != entity && !isDummyPlayerDisplayed()) {
                showRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Unmount for generic entities
            PlayerUtil.getVehicleMountController(viewer).unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);
        }
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = this.seat.firstPerson.getMode();
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        if (this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        // No other mode is supported here
        if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
            new_firstPersonMode = FirstPersonViewMode.THIRD_P;
        }

        // If unchanged, do nothing
        if (new_smoothCoasters == seat.firstPerson.useSmoothCoasters() &&
            new_firstPersonMode == seat.firstPerson.getLiveMode())
        {
            return;
        }

        // Sometimes a full reset of the FPV controller is required. Avoid when silent.
        if (!silent &&
            seat.firstPerson.doesViewModeChangeRequireReset(new_firstPersonMode) &&
            seat.getViewersSynced().contains(this.getEntity()))
        {
            // Hide, change, and make visible again, just for the first-player-view player
            Player viewer = (Player) this.getEntity();
            seat.makeHiddenImpl(viewer);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            seat.makeVisibleImpl(viewer);
            return;
        }

        // Silent update
        seat.firstPerson.setLiveMode(new_firstPersonMode);
        seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
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

    /**
     * Creates a skull item representing an Entity. If not possible, returns null.
     *
     * @param entity Entity for which the skull is. Null creates a dummy one.
     * @return skull item best representing this entity, null otherwise
     */
    @SuppressWarnings("deprecation")
    public static ItemStack createSkullItem(Entity entity) {
        if (entity == null || entity instanceof Player) {
            // For players, or the dummy player skin
            if (Common.hasCapability("Common:Item:CreatePlayerHeadUsingGameProfile")) {
                return createSkullFromProfile((Player) entity);
            } else {
                ItemStack skullItem = new ItemStack(MaterialUtil.getFirst("PLAYER_HEAD", "LEGACY_SKULL_ITEM"));
                skullItem.setDurability((short) 3); // For 1.12.2 and before support
                if (entity != null) {
                    SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
                    meta.setOwningPlayer((Player) entity);
                    skullItem.setItemMeta(meta);
                }
                return skullItem;
            }
        } else {
            //TODO: Skeleton and such maybe?
            return null;
        }
    }

    private static ItemStack createSkullFromProfile(Player player) {
        GameProfileHandle profile = player == null
                ? FakePlayerSpawner.createDummyPlayerProfile() : GameProfileHandle.getForPlayer(player);
        return ItemUtil.createPlayerHeadItem(profile);
    }
}
