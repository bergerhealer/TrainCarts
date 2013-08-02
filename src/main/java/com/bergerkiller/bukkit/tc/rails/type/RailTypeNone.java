package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.World;
import org.bukkit.block.Block;

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
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		// Two no-rail logic types
		if (member.isFlying()) {
			return RailLogicAir.INSTANCE;
		} else {
			return RailLogicGround.INSTANCE;
		}
	}
}
