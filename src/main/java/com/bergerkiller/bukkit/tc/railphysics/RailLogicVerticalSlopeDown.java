package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.CommonMinecart;
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
	public void onPostMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
		final IntVector3 block = member.getBlockPos();

		double factor = 0.0;
		if (this.alongZ) {
			factor = this.getDirection().getModZ() * ((block.z + 0.5) - entity.getLocZ());
		} else if (this.alongX) {
			factor = this.getDirection().getModX() * ((block.x + 0.5) - entity.getLocX());
		}
		double posYAdd = (0.5 - MathUtil.clamp(factor, 0.0, 0.5)) * 2.0;
		entity.setLocY(block.y + posYAdd);
		if (posYAdd >= 1.0) {
			// Go to the vertical rail
			entity.setLocY(entity.getLocY() + 1.0);
			entity.setLocX(block.x + 0.5);
			entity.setLocZ(block.z + 0.5);
			// Turn velocity to the vertical type
			entity.setVelocity(0.0, member.getXZForce(), 0.0);
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
