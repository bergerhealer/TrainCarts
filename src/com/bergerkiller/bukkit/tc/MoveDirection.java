package com.bergerkiller.bukkit.tc;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * Contains a direction a minecart can move
 */
public class MoveDirection {
	public static MoveDirection[] directions = new MoveDirection[16];
	public final double x1, y1, z1, x2, y2, z2, dx, dy, dz;
	public final Vector[] raw;

	static {
		// Leveled
		setDirection(BlockFace.EAST, false, 0, 0, -1, 0, 0, 1);
		setDirection(BlockFace.WEST, false, 0, 0, -1, 0, 0, 1);
		setDirection(BlockFace.SOUTH, false, -1, 0, 0, 1, 0, 0);
		setDirection(BlockFace.NORTH, false, -1, 0, 0, 1, 0, 0);
		// Sloped
		setDirection(BlockFace.SOUTH, true, -1, -1, 0, 1, 0, 0);
		setDirection(BlockFace.NORTH, true, -1, 0, 0, 1, -1, 0);
		setDirection(BlockFace.EAST, true, 0, 0, -1, 0, -1, 1);
		setDirection(BlockFace.WEST, true, 0, -1, -1, 0, 0, 1);
		// Curved
		setDirection(BlockFace.NORTH_EAST, false, 0, 0, 1, 1, 0, 0);
		setDirection(BlockFace.SOUTH_EAST, false, 0, 0, 1, -1, 0, 0);
		setDirection(BlockFace.SOUTH_WEST, false, 0, 0, -1, -1, 0, 0);
		setDirection(BlockFace.NORTH_WEST, false, 0, 0, -1, 1, 0, 0);
	}

	private MoveDirection(int x1, int y1, int z1, int x2, int y2, int z2) {
		this.x1 = x1;
		this.y1 = y1;
		this.z1 = z1;
		this.x2 = x2;
		this.y2 = y2;
		this.z2 = z2;
		this.dx = this.x2 - this.x1;
		this.dy = this.y2 - this.y1;
		this.dz = this.z2 - this.z1;
		this.raw = new Vector[] {new Vector(x1, y1, z1), new Vector(x2, y2, z2)};
	}

	public static MoveDirection getDirection(BlockFace face, boolean sloped) {
		return directions[createIndex(face, sloped)];
	}

	private static void setDirection(BlockFace face, boolean sloped, int dx1, int dy1, int dz1, int dx2, int dy2, int dz2) {
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
