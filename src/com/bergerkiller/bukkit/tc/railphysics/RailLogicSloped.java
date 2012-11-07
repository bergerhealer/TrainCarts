package com.bergerkiller.bukkit.tc.railphysics;

import net.minecraft.server.MathHelper;
import net.minecraft.server.Vec3D;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles minecart movement on sloped rails
 */
public class RailLogicSloped extends RailLogic {
	private static final RailLogicSloped [] values = new RailLogicSloped[4];
	private static final RailLogicSloped [] vertValues = new RailLogicSloped[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicSloped(FaceUtil.notchToFace(i << 1), false);
			vertValues[i] = new RailLogicSloped(FaceUtil.notchToFace(i << 1), true);
		}
	}

	private final RailLogicHorizontal horLogic;
	private final boolean wasVertical;

	private RailLogicSloped(BlockFace direction, boolean wasVertical) {
		super(direction);
		this.horLogic = RailLogicHorizontal.get(direction);
		this.wasVertical = wasVertical;
	}

	@Override
	public boolean isSloped() {
		return true;
	}

	@Override
	public void onPostMove(MinecartMember member) {
		int dx = member.getBlockX() - MathHelper.floor(member.locX);
		int dz = member.getBlockZ() - MathHelper.floor(member.locZ);
		if (dx == this.getDirection().getModX() && dz == this.getDirection().getModZ()) {
			member.locY -= 1.0;
		}

		// Slope physics and snap to rails logic

		// The below two Vec3D-producing functions are the same as the last part in preUpdate
		// It calculates the exact location a minecart should be on the rails

		// Note to self: For new rail types, this needs a rewrite to use a common function!
		// See the RailLogicHorizontal.onPreMove trailing part...no locY adjustment is done there
		Vec3D startVector = member.a(member.lastX, member.lastY, member.lastZ);
		if (startVector != null) {
			Vec3D endVector = member.a(member.locX, member.locY, member.locZ);
			if (endVector != null) {
				if (member.getGroup().getProperties().isSlowingDown()) {
					final double motLength = member.getXZForce();
					if (motLength > 0) {
						final double slopeSlowDown = (startVector.d - endVector.d) * 0.05 / motLength + 1.0;
						member.motX *= slopeSlowDown;
						member.motZ *= slopeSlowDown;
					}
				}
				double newLocY = endVector.d;
				if (member.isOnVertical()) {
					newLocY = Math.max(member.locY, newLocY);
				}
				member.setPosition(member.locX, newLocY, member.locZ);
			}
		}
	}

	@Override
	public void onPreMove(MinecartMember member) {
		MinecartGroup group = member.getGroup();
		// Velocity modifier for sloped tracks
		if (group.getProperties().isSlowingDown() && !group.isVelocityAction()) {
			member.motX -= MinecartMember.SLOPE_VELOCITY_MULTIPLIER * member.getRailDirection().getModX();
			member.motZ -= MinecartMember.SLOPE_VELOCITY_MULTIPLIER * member.getRailDirection().getModZ();
		}

		// Transfer vertical velocity
		if (this.wasVertical) {
			double force = member.motY;
			member.motX += force * this.getDirection().getModX();
			member.motZ += force * this.getDirection().getModZ();
			member.motY = 0.0;
		}

		// Stop movement if colliding with a block at the slope
		double blockedDistance = Double.MAX_VALUE;
		Block heading = member.getBlock(this.getDirection().getOppositeFace());
		if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
			if (MaterialUtil.SUFFOCATES.get(heading)) {
				blockedDistance = member.distanceXZ(heading) - 1.0;
			}
		} else if (member.isHeadingTo(this.getDirection())) {
			Block above = member.getBlock(BlockFace.UP);
			if (MaterialUtil.SUFFOCATES.get(above)) {
				blockedDistance = member.distanceXZ(above);
			}
		}
		if (member.getXZForce() > blockedDistance) {
			member.getGroup().setForwardForce(blockedDistance);
		}

		// Perform remaining positioning updates
		horLogic.onPreMove(member);
		member.locY += 1.0;
	}

	/**
	 * Gets the sloped rail logic for the the sloped track leading up on the direction specified
	 * 
	 * @param direction of the sloped rail
	 * @param wasVertical, whether it was a change from vertical to sloped
	 * @return Rail Logic
	 */
	public static RailLogicSloped get(BlockFace direction, boolean wasVertical) {
		if (wasVertical) {
			return vertValues[FaceUtil.faceToNotch(direction) >> 1];
		} else {
			return values[FaceUtil.faceToNotch(direction) >> 1];
		}
	}
}
