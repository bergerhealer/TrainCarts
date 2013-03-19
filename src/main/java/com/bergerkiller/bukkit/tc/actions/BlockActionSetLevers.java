package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class BlockActionSetLevers extends BlockAction {
	private final boolean down;

	public BlockActionSetLevers(Block block, boolean down) {
		super(block);
		this.down = down;
	}

	@Override
	public void start() {
		BlockUtil.setLeversAroundBlock(this.getBlock(), this.down);
	}
}
