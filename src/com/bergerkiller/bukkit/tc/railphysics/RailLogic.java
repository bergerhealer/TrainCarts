package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles how a minecart travels on top of a rail
 */
public abstract class RailLogic {
	private final BlockFace horizontalDir;
	protected final boolean alongZ, alongX, alongY, curved;

	public RailLogic(BlockFace horizontalDirection) {
		this.horizontalDir = horizontalDirection;
		this.alongX = FaceUtil.isAlongX(horizontalDirection);
		this.alongZ = FaceUtil.isAlongZ(horizontalDirection);
		this.alongY = FaceUtil.isAlongY(horizontalDirection);
		this.curved = !alongZ && !alongY && !alongX;
	}

	/**
	 * Gets the horizontal direction of the rails this logic is for
	 * 
	 * @return horizontal rail direction
	 */
	public BlockFace getDirection() {
		return this.horizontalDir;
	}

	/**
	 * Checks if this type of Rail Logic is for sloped tracks
	 * 
	 * @return True if sloped, False if not
	 */
	public boolean isSloped() {
		return false;
	}

	/**
	 * Gets whether vertical movement is performed by this rail logic
	 * 
	 * @return True if vertical movement is performed, False if not
	 */
	public boolean hasVerticalMovement() {
		return false;
	}

	/**
	 * Gets the vertical motion factor caused by gravity
	 * 
	 * @return gravity multiplier
	 */
	public double getGravityMultiplier(MinecartMember member) {
		return this.hasVerticalMovement() ? MinecartMember.GRAVITY_MULTIPLIER : 0.0;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "@" + this.getDirection();
	}

	/**
	 * Gets the minecart forward velocity on this type of Rail Logic
	 * 
	 * @param member to get the velocity of
	 * @return Forwards velocity of the minecart
	 */
	public double getForwardVelocity(MinecartMember member) {
		BlockFace direction = member.getDirection();
		return member.motY * direction.getModY() + -FaceUtil.sin(direction) * member.motZ - FaceUtil.cos(direction) * member.motX; 
	}

	/**
	 * Sets the minecart forward velocity to go to a given direction on this type of Rail Logic
	 * 
	 * @param member to set the velocity for
	 * @param force to set to, negative to reverse
	 */
	public void setForwardVelocity(MinecartMember member, double force) {
		if (!this.hasVerticalMovement() || !member.isMovingVerticalOnly()) {
			member.motX = force * -FaceUtil.cos(member.getDirection());
			member.motZ = force * -FaceUtil.sin(member.getDirection());
		} else {
			member.motY = force * member.getDirection().getModY();
		}
	}

	/**
	 * Is called right before the minecart will perform the movement updates<br>
	 * This event is called before the onPostMove event<br><br>
	 * 
	 * Velocity changes and positional fixes that influence the final movement should occur here
	 * 
	 * @param member to update
	 */
	public abstract void onPreMove(MinecartMember member);

	/**
	 * Is called after the minecart performed the movement updates<br>
	 * This event is called after the onPreMove event<br><br>
	 * 
	 * Final positioning updates and velocity changes for the next tick should occur here
	 * 
	 * @param member that moved
	 */
	public void onPostMove(MinecartMember member) {
	}

	/**
	 * Tries to find out the rail logic for the minecart specified
	 * 
	 * @param member to get the rail logic for
	 * @return Rail Logic
	 */
	public static RailLogic get(MinecartMember member) {
		if (member.isDerailed()) {
			// Two no-rail logic types
			if (member.isFlying()) {
				return RailLogicAir.INSTANCE;
			} else {
				return RailLogicGround.INSTANCE;
			}
		}
		Block rails = member.getBlock();
		int typeId = rails.getTypeId();
		if (MaterialUtil.ISRAILS.get(typeId)) {
			BlockFace direction = BlockUtil.getRails(rails).getDirection();
			if (Util.isSloped(rails.getData())) {
				if (Util.isVerticalAbove(rails, direction)) {
					// Slope-vertical logic
					return RailLogicVerticalSlopeDown.get(direction);
				} else {
					// Sloped logic
					return RailLogicSloped.get(direction);
				}
			} else {
				// Horizontal logic
				return RailLogicHorizontal.get(direction);
			}
		} else if (MaterialUtil.ISPRESSUREPLATE.get(typeId)) {
			// Get the direction of the rails to find out the logic to use
			BlockFace dir = Util.getPlateDirection(rails);
			if (dir == BlockFace.SELF) {
				//set track direction based on direction of this cart
				if (Math.abs(member.motX) > Math.abs(member.motZ)) {
					dir = BlockFace.EAST;
				} else {
					dir = BlockFace.SOUTH;
				}
			}
			return RailLogicHorizontal.get(dir);
		} else if (Util.ISVERTRAIL.get(typeId)) {
			// Vertical logic
			return RailLogicVertical.get(Util.getVerticalRailDirection(rails.getData()));
		} else {
			/*
			// Vertical to slope?
			Block below = rails.getRelative(BlockFace.DOWN);
			if (Util.ISVERTRAIL.get(below)) {
				BlockFace dir = Util.getVerticalRailDirection(below.getData());
				Rails r = BlockUtil.getRails(rails.getRelative(dir));
				if (r != null && r.isOnSlope() && r.getDirection() == dir) {
					return RailLogicVerticalSlopeUp.get(dir);
				}
			}
			*/
		}
		// Default, this should never be reached
		return RailLogicGround.INSTANCE;
	}
}
