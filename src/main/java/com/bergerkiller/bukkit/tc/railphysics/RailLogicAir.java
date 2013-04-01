package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;
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
	public BlockFace getMovementDirection(MinecartMember<?> member, Vector movement) {
		if (member.isMovingVerticalOnly()) {
			return Util.getVerticalFace(movement.getY() > 0.0);
		} else {
			return FaceUtil.getDirection(movement);
		}
	}

	@Override
	public boolean hasVerticalMovement() {
		return true;
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		// Only do this logic if the head is is not moving vertically
		// Or if this member is the head, of course
		if (member.isMovingVerticalOnly()) {
			MinecartMember<?> head = member.getGroup().head();
			if (member != head && head.isMovingVerticalOnly()) {
				return;
			}
		}
		// Apply flying friction
		member.getEntity().vel.multiply(member.getEntity().getFlyingVelocityMod());
	}
}
