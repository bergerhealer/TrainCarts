package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Redstone;

import com.bergerkiller.bukkit.common.utils.BlockUtil;

public enum PowerState {
	ON, OFF, NONE;
	
	public static PowerState get(Block block, BlockFace from) {
		block = block.getRelative(from);
		Material type = block.getType();

		if (type == Material.REDSTONE_TORCH_ON) {
			return PowerState.ON;
		} else if (type == Material.REDSTONE_TORCH_OFF) {
			return PowerState.OFF;
		} else {
			MaterialData dat = type.getNewData(block.getData());
			if (dat != null && dat instanceof Redstone) {
				return ((Redstone) dat).isPowered() ? PowerState.ON : PowerState.OFF;
			} else if (from != BlockFace.DOWN && from != BlockFace.UP)  {
				if (type == Material.REDSTONE_WIRE) {
					return (block.getData() != 0) ? PowerState.ON : PowerState.OFF;
				} else if (type == Material.DIODE_BLOCK_ON) {
					return (BlockUtil.getFacing(block) != from) ? PowerState.ON : PowerState.OFF;
				}
			}
		}
		return PowerState.NONE;
	}
	
	public boolean hasPower() {
		switch (this) {
		case ON : return true;
		default : return false;
		}
	}
}