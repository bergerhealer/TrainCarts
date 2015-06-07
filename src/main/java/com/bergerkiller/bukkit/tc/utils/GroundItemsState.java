package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.bases.BlockStateBase;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;

public class GroundItemsState extends BlockStateBase implements InventoryHolder {
    private GroundItemsInventory inventory;

    public GroundItemsState(Block block, int radius) {
        super(block);
        this.inventory = new GroundItemsInventory(block, (double) radius + 0.5);
    }

    @Override
    public GroundItemsInventory getInventory() {
        return this.inventory;
    }
}
