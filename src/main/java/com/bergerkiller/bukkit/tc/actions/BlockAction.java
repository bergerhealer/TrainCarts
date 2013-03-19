package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.Block;

public class BlockAction extends Action {
	private final Block block;

	public BlockAction(Block block) {
		this.block = block;
	}

	public Block getBlock() {
		return this.block;
	}
}
