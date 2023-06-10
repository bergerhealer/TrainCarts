package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;
import org.bukkit.inventory.ItemStack;

/**
 * Extra utilities to move a 1.19.4+ Display Item entity around
 */
public class VirtualDisplayItemEntity extends VirtualDisplayEntity {
    // This (unchanging) read-only metadata is used when spawning the mount of the display entity
    private static final DataWatcher MOUNT_METADATA = new DataWatcher();
    static {
        MOUNT_METADATA.watch(EntityHandle.DATA_NO_GRAVITY, true);
        MOUNT_METADATA.watch(EntityHandle.DATA_FLAGS,
                (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        MOUNT_METADATA.watch(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                (byte) (EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                        EntityArmorStandHandle.DATA_FLAG_IS_SMALL |
                        EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE));
    }

    /**
     * On Minecraft 1.19.4 display entities had their yaw flipped. As such an extra flip was needed
     * to correct for this. No per-player logic is needed as ViaVersion will flip yaw automatically
     * when 1.19.4 clients connect to a 1.20 server.
     */
    private static final float INITIAL_YAW = Common.evaluateMCVersion("<=", "1.19.4") ? 180.0f : 0.0f;

    // Properties
    private ItemDisplayMode mode;
    private ItemStack item;
    private double clip;
    private boolean appliedClip;

    public VirtualDisplayItemEntity(AttachmentManager manager) {
        super(manager, ITEM_DISPLAY_ENTITY_TYPE);
        metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_ITEM_DISPLAY_MODE, ItemDisplayMode.HEAD);
        metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_ITEM_STACK, null);

        mode = ItemDisplayMode.HEAD;
        item = null;
        clip = 0.0;
        appliedClip = false;
    }

    public ItemStack getItem() {
        return item;
    }

    public ItemDisplayMode getMode() {
        return mode;
    }

    public void setItem(ItemDisplayMode mode, ItemStack item) {
        if (mode == null) {
            throw new IllegalArgumentException("Null dispay mode specified. Invalid transform type?");
        }
        if (!LogicUtil.bothNullOrEqual(item, this.item) || this.mode != mode) {
            this.item = item;
            this.mode = mode;
            this.metadata.set(DisplayHandle.ItemDisplayHandle.DATA_ITEM_STACK, item);
            this.metadata.set(DisplayHandle.ItemDisplayHandle.DATA_ITEM_DISPLAY_MODE, mode);
            syncMeta(); // Changes in item should occur immediately
        }
    }

    @Override
    protected void onScaleUpdated() {
        super.onScaleUpdated();
        applyClip();
    }

    @Override
    protected float getInitialYaw() {
        return INITIAL_YAW;
    }

    /**
     * Sets a new clip size. This together with the scale axis defines the size of the entity
     *
     * @param clip Clip bounding box size (before scale)
     */
    public void setClip(double clip) {
        if (this.clip != clip) {
            this.clip = clip;
            applyClip();
        }
    }

    private void applyClip() {
        if (this.clip != 0.0) {
            if (!appliedClip) {
                appliedClip = true;
                metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_WIDTH, 0.0f);
                metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_HEIGHT, 0.0f);
            }
            float f = (float) (this.clip * BBOX_FACT * Util.absMaxAxis(scale));
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_WIDTH, f);
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_HEIGHT, f);
        } else if (appliedClip) {
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_WIDTH, 0.0f);
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_HEIGHT, 0.0f);
        }
    }
}
