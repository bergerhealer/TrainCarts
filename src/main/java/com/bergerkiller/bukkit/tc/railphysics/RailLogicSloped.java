package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles minecart movement on sloped rails
 */
public class RailLogicSloped extends RailLogicHorizontal {
	private static final RailLogicSloped [] values = new RailLogicSloped[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicSloped(FaceUtil.notchToFace(i << 1));
		}
	}

	protected RailLogicSloped(BlockFace direction) {
		super(direction);
	}

	@Override
	public boolean isSloped() {
		return true;
	}

	@Override
	public void onPostMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();

		int dx = member.getBlockPos().x - entity.getLocBlockX();
		int dz = member.getBlockPos().z - entity.getLocBlockZ();
		if (dx == this.getDirection().getModX() && dz == this.getDirection().getModZ()) {
			entity.setLocY(entity.getLocY() - 1.0);
		}

		// Slope physics and snap to rails logic

		// The below two Vector-producing functions are the same as the last part in preUpdate
		// It calculates the exact location a minecart should be on the rails

		// Note to self: For new rail types, this needs a rewrite to use a common function!
		// See the RailLogicHorizontal.onPreMove trailing part...no locY adjustment is done there
		Vector startVector = entity.getSlopedPosition(entity.getLastX(), entity.getLastY(), entity.getLastZ());
		if (startVector != null) {
			Vector endVector = entity.getSlopedPosition(entity.getLocX(), entity.getLocY(), entity.getLocZ());
			if (endVector != null) {
				if (member.getGroup().getProperties().isSlowingDown()) {
					final double motLength = member.getXZForce();
					if (motLength > 0) {
						final double fact = (startVector.getY() - endVector.getY()) * 0.05 / motLength + 1.0;
						entity.multiplyVelocity(fact, 1.0, fact);
					}
				}
				entity.setPosition(entity.getLocX(), endVector.getY(), entity.getLocZ());
			}
		}
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
	
		MinecartGroup group = member.getGroup();
		// Velocity modifier for sloped tracks
		if (group.getProperties().isSlowingDown() && !group.isMovementControlled()) {
			entity.addMotX(-MinecartMember.SLOPE_VELOCITY_MULTIPLIER * this.getDirection().getModX());
			entity.addMotZ(-MinecartMember.SLOPE_VELOCITY_MULTIPLIER * this.getDirection().getModZ());
		}
		entity.addMotX(entity.getMotY() * this.getDirection().getModX());
		entity.addMotZ(entity.getMotY() * this.getDirection().getModZ());
		entity.setMotY(0.0);

		// Stop movement if colliding with a block at the slope
		double blockedDistance = Double.MAX_VALUE;
		Block heading = member.getBlock(this.getDirection().getOppositeFace());
		if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
			if (MaterialUtil.SUFFOCATES.get(heading)) {
				blockedDistance = entity.distanceXZTo(heading) - 1.0;
			}
		} else if (member.isHeadingTo(this.getDirection())) {
			Block above = member.getBlock(BlockFace.UP);
			if (MaterialUtil.SUFFOCATES.get(above)) {
				blockedDistance = entity.distanceXZTo(above);
			}
		}
		if (member.getXZForce() > blockedDistance) {
			member.getGroup().setForwardForce(blockedDistance);
		}

		// Perform remaining positioning updates
		super.onPreMove(member);
		entity.setLocY(entity.getLocY() + 1.0);
	}

	/**
	 * Gets the sloped rail logic for the the sloped track leading up on the direction specified
	 * 
	 * @param direction of the sloped rail
	 * @param wasVertical, whether it was a change from vertical to sloped
	 * @return Rail Logic
	 */
	public static RailLogicSloped get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction) >> 1];
	}
}
