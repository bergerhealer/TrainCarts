package com.bergerkiller.bukkit.tc.railphysics;

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
	public void update(MinecartMember member) {
		MinecartGroup group = member.getGroup();
		// Velocity modifier for sloped tracks
		if (group.getProperties().isSlowingDown() && !group.isVelocityAction()) {
			// Disable sloped motion if requested
			member.motX -= MinecartMember.SLOPE_VELOCITY_MULTIPLIER * member.getRailDirection().getModX();
			member.motZ -= MinecartMember.SLOPE_VELOCITY_MULTIPLIER * member.getRailDirection().getModZ();
		}

		// Transfer vertical velocity
		if (this.wasVertical) {
			double force = member.motY / MinecartMember.HOR_VERT_TRADEOFF;
			member.motX += force * member.getRailDirection().getModX();
			member.motZ += force * member.getRailDirection().getModZ();
			member.motY = 0.0;
		}

		// Stop movement if colliding downwards (fixes cart-in block bug)
		if (!member.isMoving() || member.isHeadingTo(this.getDirection().getOppositeFace())) {
			Block heading = member.getBlock(this.getDirection().getOppositeFace());
			if (MaterialUtil.SUFFOCATES.get(heading)) {
				double minDist = (member.distanceXZ(heading) - 1.0);
				if (member.getXZForce() > minDist) {
					member.getGroup().setForwardForce(minDist);
				}
			}
		}

		// Perform remaining positioning updates
		horLogic.update(member);
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
