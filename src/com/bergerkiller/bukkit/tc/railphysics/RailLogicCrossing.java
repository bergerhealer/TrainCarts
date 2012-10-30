package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Rail logic for the X-crossing made of pressure plates
 */
public class RailLogicCrossing extends RailLogic {
	public static final RailLogicCrossing INSTANCE = new RailLogicCrossing();
	private final RailLogicHorizontal SOUTH = RailLogicHorizontal.get(BlockFace.SOUTH);
	private final RailLogicHorizontal WEST = RailLogicHorizontal.get(BlockFace.WEST);

	private RailLogicCrossing() {
	}

	@Override
	public void update(MinecartMember member) {
		//TODO: Implement this!
	}
}
