package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the rail logic of a minecart sliding on the ground
 */
public class RailLogicGround extends RailLogic {
	public static final RailLogicGround INSTANCE = new RailLogicGround();

	private RailLogicGround() {
	}

	@Override
	public void update(MinecartMember member) {
		Vector friction = member.getDerailedVelocityMod();
		member.motX *= friction.getX();
		member.motY *= friction.getY();
		member.motZ *= friction.getZ();
	}
}
