package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
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
	public BlockFace getMovementDirection(MinecartMember<?> member, Vector movement) {
		return FaceUtil.getDirection(movement);
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		// Apply ground friction
		member.getEntity().vel.multiply(member.getEntity().getDerailedVelocityMod());
	}
}
