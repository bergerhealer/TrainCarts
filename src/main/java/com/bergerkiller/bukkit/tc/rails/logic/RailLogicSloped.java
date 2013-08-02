package com.bergerkiller.bukkit.tc.rails.logic;

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

		int dx = member.getBlockPos().x - entity.loc.x.block();
		int dz = member.getBlockPos().z - entity.loc.z.block();
		if (dx == this.getDirection().getModX() && dz == this.getDirection().getModZ()) {
			entity.loc.y.subtract(1.0);
		}

		// Slope physics and snap to rails logic

		// The below two Vector-producing functions are the same as the last part in preUpdate
		// It calculates the exact location a minecart should be on the rails

		// Note to self: For new rail types, this needs a rewrite to use a common function!
		// See the RailLogicHorizontal.onPreMove trailing part...no locY adjustment is done there
		Vector startVector = entity.getSlopedPosition(entity.last.getX(), entity.last.getY(), entity.last.getZ());
		if (startVector != null) {
			Vector endVector = entity.getSlopedPosition(entity.loc.getX(), entity.loc.getY(), entity.loc.getZ());
			if (endVector != null) {
				if (member.getGroup().getProperties().isSlowingDown()) {
					final double motLength = entity.vel.xz.length();
					if (motLength > 0) {
						entity.vel.xz.multiply((startVector.getY() - endVector.getY()) * 0.05 / motLength + 1.0);
					}
				}
				entity.setPosition(entity.loc.getX(), endVector.getY(), entity.loc.getZ());
			}
		}
	}

	@Override
	public void onPreMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
	
		MinecartGroup group = member.getGroup();
		// Velocity modifier for sloped tracks
		if (group.getProperties().isSlowingDown() && !member.isMovementControlled()) {
			entity.vel.xz.subtract(this.getDirection(), MinecartMember.SLOPE_VELOCITY_MULTIPLIER);
		}
		entity.vel.xz.add(this.getDirection(), entity.vel.getY());
		entity.vel.y.setZero();

		// Stop movement if colliding with a block at the slope
		double blockedDistance = Double.MAX_VALUE;
		Block heading = member.getBlock(this.getDirection().getOppositeFace());
		if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
			if (MaterialUtil.SUFFOCATES.get(heading)) {
				blockedDistance = entity.loc.xz.distance(heading) - 1.0;
			}
		} else if (member.isHeadingTo(this.getDirection())) {
			Block above = member.getBlock(BlockFace.UP);
			if (MaterialUtil.SUFFOCATES.get(above)) {
				blockedDistance = entity.loc.xz.distance(above);
			}
		}
		if (entity.vel.xz.length() > blockedDistance) {
			member.getGroup().setForwardForce(blockedDistance);
		}

		// Perform remaining positioning updates
		super.onPreMove(member);
		entity.loc.y.add(1.0);
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
