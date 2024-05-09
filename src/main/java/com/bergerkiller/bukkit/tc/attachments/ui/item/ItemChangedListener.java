package com.bergerkiller.bukkit.tc.attachments.ui.item;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;

/**
 * General interface for handling when an item in a view changes
 */
public interface ItemChangedListener {
    void onItemChanged(CommonItemStack item);
}
