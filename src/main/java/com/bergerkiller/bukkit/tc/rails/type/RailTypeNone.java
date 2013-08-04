package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicGround;

public class RailTypeNone extends RailType {

	/**
	 * None never matches - it is returned when no other rail type is found
	 */
	@Override
	public boolean isRail(int typeId, int data) {
		return false;
	}

	@Override
	public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
		return pos;
	}

	@Override
	public Block findMinecartPos(Block trackBlock) {
		return trackBlock;
	}

	@Override
	public BlockFace[] getPossibleDirections(Block trackBlock) {
		return new BlockFace[0];
	}

	@Override
	public Block findRail(Block pos) {
		return pos;
	}

	@Override
	public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
		return null;
	}

	@Override
	public BlockFace getDirection(Block railsBlock) {
		return BlockFace.SELF;
	}

	@Override
	public BlockFace getSignColumnDirection(Block railsBlock) {
		return BlockFace.SELF;
	}

	@Override
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		// Two no-rail logic types
		if (member.isFlying()) {
			return RailLogicAir.INSTANCE;
		} else {
			return RailLogicGround.INSTANCE;
		}
	}
}
