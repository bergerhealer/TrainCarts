package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.item.ItemEntityHandle;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * A virtual entity that displays a dropped item (item entity on the ground).
 */
public class VirtualDroppedItemEntity extends VirtualEntity implements VirtualSpawnableObject.ItemDisplay {
    private ItemStack item;

    public VirtualDroppedItemEntity() {
        this(null);
    }

    public VirtualDroppedItemEntity(AttachmentManager manager) {
        super(manager);
        this.setEntityType(EntityType.DROPPED_ITEM);
        this.item = null;
    }

    @Override
    public ItemStack getItem() {
        return item;
    }

    @Override
    public void setItem(ItemStack item) {
        this.item = item;
        this.getMetaData().set(ItemEntityHandle.DATA_ITEM, item);
        this.syncMetadata();
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        // Dropped items do not support glowing in this context
    }
}
