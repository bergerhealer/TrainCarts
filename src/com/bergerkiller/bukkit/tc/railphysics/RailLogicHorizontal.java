package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Horizontal rail logic that does not operate on the vertical motion and position
 */
public class RailLogicHorizontal extends RailLogic {
	private static final RailLogicHorizontal[] values = new RailLogicHorizontal[8];
	static {
		for (int i = 0; i < 8; i++) {
			values[i] = new RailLogicHorizontal(FaceUtil.notchToFace(i));
		}
	}

	private final double dx, dz;
	private final double x1, z1, x2, z2;
	private final BlockFace[] faces;

	protected RailLogicHorizontal(BlockFace direction) {
		super(direction);
		// Fix north/east, they are non-existent
		if (direction == BlockFace.NORTH || direction == BlockFace.EAST) {
			direction = direction.getOppositeFace();
		}
		// Generate faces and movement offset data
		this.faces = FaceUtil.getFaces(direction);
		this.x1 = -0.5 * faces[0].getModX();
		this.z1 = -0.5 * faces[0].getModZ();
		this.x2 = -0.5 * faces[1].getModX();
		this.z2 = -0.5 * faces[1].getModZ();
		this.dx = this.x2 - this.x1;
		this.dz = this.z2 - this.z1;
		// Invert north and south (is for some reason needed)
		for (int i = 0; i < this.faces.length; i++) {
			if (this.faces[i] == BlockFace.NORTH || this.faces[i] == BlockFace.SOUTH) {
				this.faces[i] = this.faces[i].getOppositeFace();
			}
		}
	}

	@Override
	public double getForwardVelocity(MinecartMember member) {
		BlockFace direction = member.getDirection();
		return -FaceUtil.sin(direction) * member.motZ - FaceUtil.cos(direction) * member.motX; 
	}

	@Override
	public void setForwardVelocity(MinecartMember member, double force) {
		member.motX = -FaceUtil.cos(member.getDirection()) * force;
		member.motZ = -FaceUtil.sin(member.getDirection()) * force;
	}

	@Override
	public void onPreMove(MinecartMember member) {
		// Apply velocity modifiers
		boolean invert = false;
		if (this.curved) {
			// Invert only if the delta z is inverted
			BlockFace from = FaceUtil.getDirection(member.motX, member.motZ, false);
			invert = from == this.faces[0] || from == this.faces[1];
		} else {
			// Invert only if the direction is inverted relative to cart velocity
			invert = (member.motX * this.dx + member.motZ * this.dz) < 0.0;
		}
		double railFactor = MathUtil.invert(MathUtil.normalize(this.dx, this.dz, member.motX, member.motZ), invert);
		member.motX = railFactor * this.dx;
		member.motZ = railFactor * this.dz;
		member.motY = 0.0;

		//location is updated to follow the tracks
		double newLocX = (double) member.getBlockX() + 0.5 + this.x1;
		double newLocY = (double) member.getBlockY() + (double) member.height;
		double newLocZ = (double) member.getBlockZ() + 0.5 + this.z1;
		if (this.alongX) {
			// Moving along the X-axis
			newLocZ += this.dz * (member.locZ - member.getBlockZ());
		} else if (this.alongZ) {
			// Moving along the Z-axis
			newLocX += this.dx * (member.locX - member.getBlockX());
		} else {
			// Curve
			double factor = 2.0 * (this.dx * (member.locX - newLocX) + this.dz * (member.locZ - newLocZ));
			newLocX += factor * this.dx;
			newLocZ += factor * this.dz;
		}
		member.locX = newLocX;
		member.locY = newLocY;
		member.locZ = newLocZ;
	}

	/**
	 * Gets the horizontal rail logic to go into the direction specified
	 * 
	 * @param direction to go to
	 * @return Horizontal rail logic for that direction
	 */
	public static RailLogicHorizontal get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction)];
	}
}
