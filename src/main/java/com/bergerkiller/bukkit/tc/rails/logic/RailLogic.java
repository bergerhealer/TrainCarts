package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
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
	public double getGravityMultiplier(MinecartMember<?> member) {
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
	public double getForwardVelocity(MinecartMember<?> member) {
		final CommonEntity<?> e = member.getEntity();
		final BlockFace direction = member.getDirection();
		double vel = 0.0;
		vel += e.vel.getX() * FaceUtil.cos(direction);
		vel += e.vel.getY() * direction.getModY();
		vel += e.vel.getZ() * FaceUtil.sin(direction);
		return vel;
	}

	/**
	 * Sets the minecart forward velocity to go to a given direction on this type of Rail Logic
	 * 
	 * @param member to set the velocity for
	 * @param force to set to, negative to reverse
	 */
	public void setForwardVelocity(MinecartMember<?> member, double force) {
		final CommonEntity<?> e = member.getEntity();
		if (!this.hasVerticalMovement() || !member.isMovingVerticalOnly()) {
			e.vel.setX(force * FaceUtil.cos(member.getDirection()));
			e.vel.setZ(force * FaceUtil.sin(member.getDirection()));
		} else {
			e.vel.setY(force * member.getDirection().getModY());
		}
	}

	/**
	 * Obtains the direction to which a Minecart is moving on this type of Rail Logic
	 * 
	 * @param member that is moving
	 * @param movement that is preferred
	 * @return the BlockFace direction
	 */
	public abstract BlockFace getMovementDirection(MinecartMember<?> member, Vector movement);

	/**
	 * Is called right before the minecart will perform the movement updates<br>
	 * This event is called before the onPostMove event<br><br>
	 * 
	 * Velocity changes and positional fixes that influence the final movement should occur here
	 * 
	 * @param member to update
	 */
	public abstract void onPreMove(MinecartMember<?> member);

	/**
	 * Is called after the minecart performed the movement updates<br>
	 * This event is called after the onPreMove event<br><br>
	 * 
	 * Final positioning updates and velocity changes for the next tick should occur here
	 * 
	 * @param member that moved
	 */
	public void onPostMove(MinecartMember<?> member) {
	}
}
