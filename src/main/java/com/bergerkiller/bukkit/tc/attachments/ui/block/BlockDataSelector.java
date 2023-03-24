package com.bergerkiller.bukkit.tc.attachments.ui.block;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * A type of BlockData selector widget
 */
public interface BlockDataSelector {
    /**
     * Called when the user selects a different type of block data. This is
     * <b>only</b> called when the user does so, not when
     * {@link #setSelectedBlockData(BlockData)} is called.
     *
     * @param blockData The newly selected BlockData
     */
    void onSelectedBlockDataChanged(BlockData blockData);

    /**
     * Gets the currently selected block data
     *
     * @return selected BlockData
     */
    BlockData getSelectedBlockData();

    /**
     * Selects a different block data in this menu, or sets the initial block
     * data that is selected. Does not call {@link #onSelectedBlockDataChanged(BlockData)}!
     *
     * @param blockData BlockData to select
     * @return this
     */
    BlockDataSelector setSelectedBlockData(BlockData blockData);
}
