package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class RailLogicVertical extends RailLogic {
	private static final RailLogicVertical[] valuesUp = new RailLogicVertical[4];
	private static final RailLogicVertical[] valuesDown = new RailLogicVertical[4];
	static {
		for (int i = 0; i < 4; i++) {
			valuesUp[i] = new RailLogicVertical(FaceUtil.notchToFace(i << 1), true);
			valuesDown[i] = new RailLogicVertical(FaceUtil.notchToFace(i << 1), false);
		}
	}

	private final boolean upSlope;
	
	private RailLogicVertical(BlockFace direction, boolean upSlope) {
		super(direction);
		this.upSlope = upSlope;
	}

	@Override
	public boolean isSloped() {
		return this.upSlope;
	}

	@Override
	public void onPreMove(MinecartMember member) {
		// Horizontal rail force to motY
		member.motY += Util.invert(member.getXZForce(), !this.upSlope);
		member.motX = 0.0;
		member.motZ = 0.0;
		// Position update
		member.locX = (double) member.getBlockX() + 0.5;
		member.locZ = (double) member.getBlockZ() + 0.5;
	}

	@Override
	public void onPostMove(MinecartMember member) {
		if (this.upSlope) {
			RailLogicSloped.get(this.getDirection(), false).onPostMove(member);
		}
	}

	/**
	 * Gets the vertical rail logic for the direction specified
	 * 
	 * @param direction of the rail
	 * @param upSlope whether the minecart went up from a slope
	 * @return Rail Logic
	 */
	public static RailLogicVertical get(BlockFace direction, boolean upSlope) {
		if (upSlope) {
			return valuesUp[FaceUtil.faceToNotch(direction) >> 1];
		} else {
			return valuesDown[FaceUtil.faceToNotch(direction) >> 1];
		}
	}
}
