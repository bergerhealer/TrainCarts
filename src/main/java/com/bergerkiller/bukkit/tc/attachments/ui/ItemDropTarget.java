package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.inventory.ItemStack;

/**
 * Accepts items dropped/selected into a widget. When the Player drops
 * an item from their inventory, and a widget is active implementing this
 * interface, then it is sent to {@link #acceptItem}. If that method returns true,
 * then the drop is blocked and the item is presumably set in the widget.
 */
public interface ItemDropTarget {

    /**
     * Accepts ItemStack that the player is trying to set through an item drop
     *
     * @param item ItemStack
     * @return True if the item is valid and accepted by the widget at this time.
     *                Should return false if the widget is not in a state to accept
     *                items right now.
     */
    boolean acceptItem(ItemStack item);
}
