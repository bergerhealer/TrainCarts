package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;
import org.bukkit.block.ContainerBlock;
import org.bukkit.craftbukkit.v1_4_6.block.CraftBlockState;

@SuppressWarnings("deprecation")
public class GroundItemsState extends CraftBlockState implements ContainerBlock {
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
