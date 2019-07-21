package com.bergerkiller.bukkit.tc.attachments.ui.item;

import org.bukkit.inventory.ItemStack;

/**
 * General interface for handling when an item in a view changes
 */
public interface ItemChangedListener {
    void onItemChanged(ItemStack item);
}
