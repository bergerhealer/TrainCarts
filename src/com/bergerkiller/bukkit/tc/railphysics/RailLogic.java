package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles how a minecart travels on top of a rail
 */
public abstract class RailLogic {

	/**
	 * Updates the velocity and positioning of a minecart
	 * 
	 * @param member to update
	 */
	public abstract void update(MinecartMember member);

	/**
	 * Tries to find out the rail logic for the minecart specified
	 * 
	 * @param member to get the rail logic for
	 * @return Rail Logic
	 */
	public static RailLogic get(MinecartMember member) {
		Block rails = member.getBlock();
		int typeId = rails.getTypeId();
		if (MaterialUtil.ISRAILS.get(typeId)) {
			if (Util.isSloped(rails.getData())) {
				// Sloped logic
			} else {
				// Horizontal logic
			}
		} else if (MaterialUtil.ISPRESSUREPLATE.get(typeId)) {
			return RailLogicCrossing.INSTANCE;
		} else if (Util.ISVERTRAIL.get(typeId)) {
			return RailLogicVertical.get(Util.getVerticalRailDirection(rails.getData()));
		}
		// Final two no-rail logic types
		if (member.isFlying()) {
			return RailLogicAir.INSTANCE;
		} else {
			return RailLogicGround.INSTANCE;
		}
	}
}
