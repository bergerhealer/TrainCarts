package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles movement of a minecart that is flying through the air
 */
public class RailLogicAir extends RailLogic {
	public static final RailLogicAir INSTANCE = new RailLogicAir();

	private RailLogicAir() {
		super(BlockFace.SELF);
	}

	@Override
	public void update(MinecartMember member) {
		Vector friction = member.getFlyingVelocityMod();
		member.motX *= friction.getX();
		member.motY *= friction.getY();
		member.motZ *= friction.getZ();
	}
}
