package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
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

	private final double dy, startY;

	protected RailLogicSloped(BlockFace direction) {
		super(direction);
		if (direction == BlockFace.SOUTH || direction == BlockFace.EAST) {
			this.dy = 1.0;
			this.startY = 0.0;
		} else {
			this.dy = -1.0;
			this.startY = 1.0;
		}
	}

	@Override
	public boolean isSloped() {
		return true;
	}

	@Override
	public void onPostMove(MinecartMember<?> member) {
		final CommonMinecart<?> entity = member.getEntity();
		// Adjust the Y-position of the Minecart on this slope and calculate velocity
		int dx = member.getBlockPos().x - entity.loc.x.block();
		int dz = member.getBlockPos().z - entity.loc.z.block();
		if (dx == this.getDirection().getModX() && dz == this.getDirection().getModZ()) {
			entity.loc.y.subtract(1.0);
		}

		RailLogic logic = member.getRailTracker().getLastLogic();
		IntVector3 lastRailPos = new IntVector3(member.getRailTracker().getLastBlock());

		// Get from and to rail-fixed positions
		Vector startVector = logic.getFixedPosition(entity, entity.last, lastRailPos);
		Vector endVector = getFixedPosition(entity, entity.loc, member.getBlockPos());

		// Update fixed Y-position
		entity.setPosition(entity.loc.getX(), endVector.getY(), entity.loc.getZ());

		// Apply velocity factors from going up/down the slope
		if (member.getGroup().getProperties().isSlowingDown()) {
			final double motLength = entity.vel.xz.length();
			if (motLength > 0) {
				entity.vel.xz.multiply((startVector.getY() - endVector.getY()) * 0.05 / motLength + 1.0);
			}
		}
	}

	@Override
	public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
		Vector pos = super.getFixedPosition(entity, x, y, z, railPos);
		// Adjust the Y-position to match this rail
		double stage = 0.0;
		if (alongZ) {
			stage = z - (double) railPos.z;
		} else if (alongX) {
			stage = x - (double) railPos.x;
		}
		pos.setY(railPos.midY() + startY + dy * stage);
		return pos;
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
		Block inside = member.getRailType().findMinecartPos(member.getBlock());
		Block heading = inside.getRelative(this.getDirection().getOppositeFace());
		if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
			if (MaterialUtil.SUFFOCATES.get(heading)) {
				blockedDistance = entity.loc.xz.distance(heading) - 1.0;
			}
		} else if (member.isHeadingTo(this.getDirection())) {
			Block above = inside.getRelative(BlockFace.UP);
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
