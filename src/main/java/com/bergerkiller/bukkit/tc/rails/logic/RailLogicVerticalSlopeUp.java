package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the rail logic for a vertical rail to sloped rail<br>
 * This is a stub: This should eventually replace the teleport logic in the Minecart members' move info
 */
public class RailLogicVerticalSlopeUp extends RailLogicHorizontal {
	private static final RailLogicVerticalSlopeUp[] values = new RailLogicVerticalSlopeUp[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicVerticalSlopeUp(FaceUtil.notchToFace(i << 1));
		}
	}

	protected RailLogicVerticalSlopeUp(BlockFace direction) {
		super(direction);
	}

	@Override
	public boolean isSloped() {
		return true;
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
		entity.vel.xz.add(this.getDirection(), entity.vel.getY());
		entity.vel.y.setZero();
		super.onPreMove(member);
	}

	/**
	 * Gets the sloped-vertical rail logic for the the vertical track leading horizontal facing the direction specified
	 * 
	 * @param direction of the sloped rail
	 * @return Rail Logic
	 */
	public static RailLogicVerticalSlopeUp get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction) >> 1];
	}
}
