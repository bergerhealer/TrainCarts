package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.inventory.ItemStack;

public interface ItemDropTarget {

    public boolean acceptItem(ItemStack item);
}
