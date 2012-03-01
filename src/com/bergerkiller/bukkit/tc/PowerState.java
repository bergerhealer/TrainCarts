package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Lever;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Redstone;

import com.bergerkiller.bukkit.common.utils.BlockUtil;

public enum PowerState {
	ON, OFF, NONE;

	private static boolean isDistractingColumn(Block main, BlockFace face) {
		Block side = main.getRelative(face);
		Material type = side.getType();
		if (Util.canDistractWire(type)) {
			return true;
		} else if (type == Material.AIR) {
			//check level below
			if (Util.canDistractWire(side.getRelative(BlockFace.DOWN).getType())) {
				return true;
			}
		} else if (type == Material.DIODE_BLOCK_ON || type == Material.DIODE_BLOCK_OFF) {
			//powered by repeater?
			BlockFace facing = BlockUtil.getFacing(side);
			return facing == face;
		}
		if (main.getRelative(BlockFace.UP).getType() == Material.AIR) {
			//check level on top
			return Util.canDistractWire(side.getRelative(BlockFace.UP).getType());
		} else {
			return false;
		}
	}
	private static boolean isDistracted(Block wire, BlockFace face) {
		if (face == BlockFace.NORTH) face = BlockFace.SOUTH;
		if (face == BlockFace.EAST) face = BlockFace.WEST; 
		BlockFace f1 = (face == BlockFace.SOUTH) ? BlockFace.WEST : BlockFace.SOUTH;
		BlockFace f2 = (face == BlockFace.SOUTH) ? BlockFace.EAST : BlockFace.NORTH;
		return isDistractingColumn(wire, f1) || isDistractingColumn(wire, f2);
	}
	
	public static PowerState get(Block block, BlockFace from) {
		return get(block, from, true);
	}
	public static PowerState get(Block block, BlockFace from, boolean useSignLogic) {
		block = block.getRelative(from);
		Material type = block.getType();

		if (type == Material.REDSTONE_TORCH_ON) {
			if (useSignLogic || from == BlockFace.DOWN) {
				return PowerState.ON;
			} else {
				return PowerState.NONE;
			}
		} else if (type == Material.REDSTONE_TORCH_OFF) {
			if (useSignLogic || from == BlockFace.DOWN) {
				return PowerState.OFF;
			} else {
				return PowerState.NONE;
			}
		} else if (type == Material.REDSTONE_WIRE) {
			if (useSignLogic || from == BlockFace.UP) {
				return (block.getData() != 0) ? PowerState.ON : PowerState.OFF;
			} else if (from != BlockFace.DOWN && !isDistracted(block, from)) {
				//facing towards this block
				return (block.getData() != 0) ? PowerState.ON : PowerState.OFF;
			} else {
				return PowerState.NONE;
			}
		} else if (type == Material.DIODE_BLOCK_ON && from != BlockFace.DOWN && from != BlockFace.UP) {
			return (BlockUtil.getFacing(block) != from) ? PowerState.ON : PowerState.OFF;
		} else if (type == Material.LEVER) {
			if (useSignLogic) {
				if (BlockUtil.getData(block, Lever.class).isPowered()) {
					return PowerState.ON;
				} else {
					return PowerState.OFF;
				}
			} else {
				return PowerState.NONE;
			}
		} else if (from != BlockFace.DOWN) {
			MaterialData dat = type.getNewData(block.getData());
			if (dat != null && dat instanceof Redstone) {
				return ((Redstone) dat).isPowered() ? PowerState.ON : PowerState.OFF;
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