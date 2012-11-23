package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the rail logic of a sloped rail with a vertical rail above
 */
public class RailLogicVerticalSlopeDown extends RailLogicSloped {
	private static final RailLogicVerticalSlopeDown[] values = new RailLogicVerticalSlopeDown[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicVerticalSlopeDown(FaceUtil.notchToFace(i << 1));
		}
	}

	private RailLogicVerticalSlopeDown(BlockFace direction) {
		super(direction);
	}

	@Override
	public boolean isSloped() {
		return true;
	}

	@Override
	public void onPostMove(MinecartMember member) {
		double factor = 0.0;
		if (this.alongX) {
			factor = this.getDirection().getModZ() * ((member.getBlockZ() + 0.5) - member.locZ);
		} else if (this.alongZ) {
			factor = this.getDirection().getModX() * ((member.getBlockX() + 0.5) - member.locX);
		}
		double posYAdd = (0.5 - MathUtil.clamp(factor, 0.0, 0.5)) * 2.0;
		member.locY = member.getBlockY() + posYAdd;
		if (posYAdd >= 1.0) {
			// Go to the vertical rail
			member.locY += 1.0;
			member.locX = member.getBlockX() + 0.5;
			member.locZ = member.getBlockZ() + 0.5;
			// Turn velocity to the vertical type
			member.motY = member.getXZForce();
			member.motX = 0.0;
			member.motZ = 0.0;
		}
	}

	/**
	 * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
	 * 
	 * @param direction of the sloped rail
	 * @return Rail Logic
	 */
	public static RailLogicVerticalSlopeDown get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction) >> 1];
	}
}
