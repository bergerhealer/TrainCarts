package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.collections.BlockMap;

/**
 * Used to store a simple 'time out' value to a block, 
 * and quickly find out if this block can be used again.
 */
public class BlockTimeoutMap extends BlockMap<Long> {
	private static final long serialVersionUID = 1L;

	public void mark(Block block) {
		super.put(block, System.currentTimeMillis());
	}
	
	public boolean isMarked(Block block, final long timeout) {
		Long value = super.get(block);
		return value != null && value + timeout > System.currentTimeMillis();
	}
}
