package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the rail logic of a minecart sliding on the ground
 */
public class RailLogicGround extends RailLogic {
	public static final RailLogicGround INSTANCE = new RailLogicGround();

	private RailLogicGround() {
		super(BlockFace.SELF);
	}

	@Override
	public boolean hasVerticalMovement() {
		return true;
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		// Apply flying friction
		member.getEntity().multiplyVelocity(member.getEntity().getDerailedVelocityMod());
	}
}
