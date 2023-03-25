package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
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

    // Properties
    private ItemDisplayMode mode;
    private ItemStack item;

    public VirtualDisplayItemEntity(AttachmentManager manager) {
        super(manager, ITEM_DISPLAY_ENTITY_TYPE);
        metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_ITEM_DISPLAY_MODE, ItemDisplayMode.HEAD);
        metadata.watch(DisplayHandle.ItemDisplayHandle.DATA_ITEM_STACK, null);

        mode = ItemDisplayMode.HEAD;
        item = null;
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
}
