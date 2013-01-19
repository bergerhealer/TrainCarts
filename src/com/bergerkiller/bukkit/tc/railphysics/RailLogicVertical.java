package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class RailLogicVertical extends RailLogic {
	private static final RailLogicVertical[] values = new RailLogicVertical[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicVertical(FaceUtil.notchToFace(i << 1));
		}
	}

	private RailLogicVertical(BlockFace direction) {
		super(direction);
	}

	@Override
	public double getForwardVelocity(MinecartMember member) {
		return member.getDirection().getModY() * member.motY;
	}

	@Override
	public void setForwardVelocity(MinecartMember member, double force) {
		member.motY = member.getDirection().getModY() * force;
	}

	@Override
	public boolean hasVerticalMovement() {
		return true;
	}

	@Override
	public double getGravityMultiplier(MinecartMember member) {
		return member.getGroup().getProperties().isSlowingDown() ? MinecartMember.VERTRAIL_MULTIPLIER : 0.0;
	}

	@Override
	public void onPreMove(MinecartMember member) {
		// Horizontal rail force to motY
		member.motY += member.getXZForce() * member.getDirection().getModY();
		member.motX = 0.0;
		member.motZ = 0.0;
		// Position update
		member.locX = (double) member.getBlockX() + 0.5;
		member.locZ = (double) member.getBlockZ() + 0.5;
	}

	/**
	 * Gets the vertical rail logic for the direction specified
	 * 
	 * @param direction of the rail
	 * @param upSlope whether the minecart went up from a slope
	 * @return Rail Logic
	 */
	public static RailLogicVertical get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction) >> 1];
	}
}
