package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * Accepts block data dropped/selected into a widget. When the Player drops
 * and item from their inventory that is a block, and a widget is active implementing this
 * interface, then it is sent to {@link #acceptBlockData}. If that method returns true,
 * then the drop is blocked and the block data is presumably set in the widget.
 */
public interface BlockDataDropTarget {

    /**
     * Accepts BlockData that the player is trying to set (through an item drop or otherwise)
     *
     * @param blockData BlockData
     * @return True if the block data is valid and accepted by the widget at this time.
     *                Should return false if the widget is not in a state to accept block data
     *                right now.
     */
    boolean acceptBlockData(BlockData blockData);
}
