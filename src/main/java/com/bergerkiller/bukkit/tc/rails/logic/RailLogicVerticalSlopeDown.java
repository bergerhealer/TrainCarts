package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
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
	public void onPostMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
		final IntVector3 block = member.getBlockPos();

		// First: check whether the Minecart is moving up this slope
		// If moving down, simply use the Sloped logic in the underlying super Class
		if (member.getDirectionTo() == this.getDirection().getOppositeFace()) {
			super.onPostMove(member);
			return;
		}

		double factor = 0.0;
		if (this.alongZ) {
			factor = this.getDirection().getModZ() * (block.midZ() - entity.loc.getZ());
		} else if (this.alongX) {
			factor = this.getDirection().getModX() * (block.midX() - entity.loc.getX());
		}
		double posYAdd = (0.5 - MathUtil.clamp(factor, 0.0, 0.5)) * 2.0;
		entity.loc.y.set(block.y + posYAdd);
		if (posYAdd >= 1.0) {
			// Go to the vertical rail
			entity.loc.y.add(1.0);
			entity.loc.x.set(block.midX());
			entity.loc.z.set(block.midZ());
			// Turn velocity to the vertical type
			entity.vel.y.set(entity.vel.xz.length());
			entity.vel.xz.setZero();
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
