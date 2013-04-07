package com.bergerkiller.bukkit.tc;

import java.util.LinkedHashSet;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

public enum Direction {
	NORTH("n", "north"), EAST("e", "east"), SOUTH("s", "south"), 
	WEST("w", "west"), LEFT("l", "left"), RIGHT("r", "right"), 
	FORWARD("f", "front", "forward", "forwards", "continue"), 
	BACKWARD("b", "back", "backward", "backwards", "reverse"), 
	UP("u", "up", "upwards", "above"),
	DOWN("d", "down", "downwards", "below"),
	NONE("", "n", "none");

	private final String[] aliases;
	private Direction(String... aliases) {
		this.aliases = aliases;
	}

	public BlockFace getDirection(BlockFace signfacing) {
		return getDirection(signfacing, signfacing.getOppositeFace());
	}

	public BlockFace getDirection(BlockFace signfacing, BlockFace cartdirection) {
		switch (this) {
		case NORTH : return BlockFace.NORTH;
		case EAST : return BlockFace.EAST;
		case SOUTH : return BlockFace.SOUTH;
		case WEST : return BlockFace.WEST;
		case DOWN : return BlockFace.DOWN;
		case UP : return BlockFace.UP;
		case LEFT : return FaceUtil.rotate(signfacing, 2);
		case RIGHT : return FaceUtil.rotate(signfacing, -2);
		case FORWARD : return cartdirection;
		case BACKWARD : return cartdirection.getOppositeFace();
		default : return cartdirection;
		}
	}

	public boolean match(char character) {
		for (String alias : this.aliases) {
			if (alias.length() == 1 && alias.charAt(0) == character) {
				return true;
			}
		}
		return false;
	}

	public boolean match(String text) {
		for (String alias : this.aliases) {
			if (alias.equalsIgnoreCase(text)) return true;
		}
		return false;
	}

	public static Direction parse(char character) {
		for (Direction dir : values()) {
			if (dir.match(character)) return dir;
		}
		return NONE;
	}

	public static Direction parse(String text) {
		for (Direction dir : values()) {
			if (dir.match(text)) return dir;
		}
		return NONE;
	}

	public static Direction fromFace(BlockFace face) {
		switch (face) {
			case NORTH : return NORTH;
			case EAST : return EAST;
			case SOUTH : return SOUTH;
			case WEST : return WEST;
			case UP : return UP;
			case DOWN : return DOWN;
			case SELF : return FORWARD;
			default : return NONE;
		}
	}

	public static Direction[] parseAll(String text) {
		if (text.equalsIgnoreCase("all") || text.equals("*")) {
			Direction[] dirs = new Direction[FaceUtil.BLOCK_SIDES.length];
			for (int i = 0; i < dirs.length; i++) {
				dirs[i] = fromFace(FaceUtil.BLOCK_SIDES[i]);
			}
			return dirs;
		} else {
			LinkedHashSet<Direction> faces = new LinkedHashSet<Direction>();
			Direction dir = Direction.parse(text);
			if (dir == Direction.NONE) {
				for (char c : text.toCharArray()) {
					dir = Direction.parse(c);
					if (dir == Direction.NONE) {
						return new Direction[0];
					} else {
						faces.add(dir);
					}
				}
			} else {
				faces.add(dir);
			}
			return faces.toArray(new Direction[0]);
		}
	}

	public static BlockFace[] parseAll(String text, BlockFace absoluteDirection) {
		Direction[] dirs = parseAll(text);
		BlockFace[] faces = new BlockFace[dirs.length];
		for (int i = 0; i < faces.length; i++) {
			faces[i] = dirs[i].getDirection(absoluteDirection);
		}
		return faces;
	}
}
