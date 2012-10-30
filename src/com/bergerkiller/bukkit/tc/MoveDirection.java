package com.bergerkiller.bukkit.tc;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Contains a direction a minecart can move
 */
public class MoveDirection {
	public static MoveDirection[] directions = new MoveDirection[16];
	public final double x1, y1, z1, x2, y2, z2, dx, dy, dz;
	public final boolean isAlongX, isAlongY, isAlongZ, isCurved;
	public final Vector[] raw;

	static {
		// Leveled
		setDirection(BlockFace.EAST, false, 0, 0, -0.5, 0, 0, 0.5);
		setDirection(BlockFace.WEST, false, 0, 0, -0.5, 0, 0, 0.5);
		setDirection(BlockFace.SOUTH, false, -0.5, 0, 0, 0.5, 0, 0);
		setDirection(BlockFace.NORTH, false, -0.5, 0, 0, 0.5, 0, 0);
		// Sloped
		setDirection(BlockFace.SOUTH, true, -0.5, -1.0, 0, 0.5, 0, 0);
		setDirection(BlockFace.NORTH, true, -0.5, 0, 0, 0.5, -1.0, 0);
		setDirection(BlockFace.EAST, true, 0, 0, -0.5, 0, -1.0, 0.5);
		setDirection(BlockFace.WEST, true, 0, -1.0, -0.5, 0, 0, 0.5);
		// Curved
		setDirection(BlockFace.NORTH_EAST, false, 0, 0, 0.5, 0.5, 0, 0);
		setDirection(BlockFace.SOUTH_EAST, false, 0, 0, 0.5, -0.5, 0, 0);
		setDirection(BlockFace.SOUTH_WEST, false, 0, 0, -0.5, -0.5, 0, 0);
		setDirection(BlockFace.NORTH_WEST, false, 0, 0, -0.5, 0.5, 0, 0);
	}

	private MoveDirection(double x1, double y1, double z1, double x2, double y2, double z2) {
		// Normalization factor
		this.x1 = x1;
		this.y1 = y1;
		this.z1 = z1;
		this.x2 = x2;
		this.y2 = y2;
		this.z2 = z2;
		this.dx = this.x2 - this.x1;
		this.dy = this.y2 - this.y1;
		this.dz = this.z2 - this.z1;
		this.isAlongX = this.dx == 0.0;
		this.isAlongY = this.dy == 0.0;
		this.isAlongZ = this.dz == 0.0;
		this.isCurved = !this.isAlongX && !this.isAlongZ;
		this.raw = new Vector[] {new Vector(this.x1, this.y1, this.z1), new Vector(this.x2, this.y2, this.z2)};
	}

	/**
	 * Applies this direction to a velocity vector, normalizing the old velocity
	 * 
	 * @param velocity to apply this direction to
	 */
	public void applyVelocity(Vector velocity) {
		//TODO: Allow move direction UP and DOWN to function as well
		boolean invert = (velocity.getX() * dx + velocity.getZ() * dz) < 0.0;
		double railFactor = MathUtil.normalize(dx, dz, velocity.getX(), velocity.getZ());
		velocity.setX(railFactor * Util.invert(dx, invert));
		velocity.setZ(railFactor * Util.invert(dz, invert));
	}

	public static MoveDirection getDirection(BlockFace face, boolean sloped) {
		return directions[createIndex(face, sloped)];
	}

	private static void setDirection(BlockFace face, boolean sloped, double dx1, double dy1, double dz1, double dx2, double dy2, double dz2) {
		directions[createIndex(face, sloped)] = new MoveDirection(dx1, dy1, dz1, dx2, dy2, dz2);
	}

	private static int createIndex(BlockFace face, boolean sloped) {
		int idx = FaceUtil.faceToNotch(face);
		if (sloped) {
			idx += 8;
		}
		return idx;
	}
}
