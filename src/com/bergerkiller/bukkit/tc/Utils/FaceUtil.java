package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Util;


public class FaceUtil {
	public static final BlockFace[] axis = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.EAST};
	
	public static BlockFace combine(BlockFace from, BlockFace to) {
		if (from == BlockFace.NORTH) {
			if (to == BlockFace.WEST) {
				return BlockFace.NORTH_WEST;
			} else if (to == BlockFace.EAST) {
				return BlockFace.NORTH_EAST;
			}
		} else 	if (from == BlockFace.EAST) {
			if (to == BlockFace.NORTH) {
				return BlockFace.NORTH_EAST;
			} else if (to == BlockFace.SOUTH) {
				return BlockFace.SOUTH_EAST;
			}
		} else 	if (from == BlockFace.SOUTH) {
			if (to == BlockFace.WEST) {
				return BlockFace.SOUTH_WEST;
			} else if (to == BlockFace.EAST) {
				return BlockFace.SOUTH_EAST;
			}
		} else 	if (from == BlockFace.WEST) {
			if (to == BlockFace.NORTH) {
				return BlockFace.NORTH_WEST;
			} else if (to == BlockFace.SOUTH) {
				return BlockFace.SOUTH_WEST;
			}
		}
		return from;
	}
	public static BlockFace offset(BlockFace main, BlockFace offset) {
		if (offset == BlockFace.EAST) {
			switch (main) {
			case NORTH : return BlockFace.EAST;
			case EAST : return BlockFace.SOUTH;
			case SOUTH : return BlockFace.WEST;
			case WEST : return BlockFace.NORTH;
			}
		} else if (offset == BlockFace.WEST) {
			switch (main) {
			case NORTH : return BlockFace.WEST;
			case EAST : return BlockFace.NORTH;
			case SOUTH : return BlockFace.EAST;
			case WEST : return BlockFace.SOUTH;
			}
		} else if (offset == BlockFace.SOUTH) {
			return main.getOppositeFace();
		}
		return main;
	}
	
	public static BlockFace[] getFaces(BlockFace main) {
		BlockFace[] possible = new BlockFace[2];
		if (main == BlockFace.NORTH || main == BlockFace.SOUTH) {
			possible[0] = BlockFace.NORTH;
			possible[1] = BlockFace.SOUTH;
		} else if (main == BlockFace.EAST || main == BlockFace.WEST) {
			possible[0] = BlockFace.EAST;
			possible[1] = BlockFace.WEST;
		} else if (main == BlockFace.SOUTH_EAST) {
			possible[0] = BlockFace.NORTH;
			possible[1] = BlockFace.WEST;
		} else if (main == BlockFace.SOUTH_WEST) {
			possible[0] = BlockFace.NORTH;
			possible[1] = BlockFace.EAST;
		} else if (main == BlockFace.NORTH_EAST) {
			possible[0] = BlockFace.SOUTH;
			possible[1] = BlockFace.WEST;
		} else if (main == BlockFace.NORTH_WEST) {
			possible[0] = BlockFace.SOUTH;
			possible[1] = BlockFace.EAST;
		} else if (main == BlockFace.UP || main == BlockFace.DOWN) {
			possible[0] = BlockFace.UP;
			possible[1] = BlockFace.DOWN;
		} else {
			possible[0] = BlockFace.SELF;
			possible[1] = BlockFace.SELF;
		}
		return possible;
	}
	public static BlockFace rotate(BlockFace from, int notchCount) {
		return yawToFace(faceToYaw(from) + notchCount * 45);
	}
	
	public static BlockFace[] getAttached() {
		return getAttached(false);
	}
	public static BlockFace[] getAttached(boolean addDown) {
		if (addDown) {
			return new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, 
					BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
		} else {
			return new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, 
					BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP};
		}
	}
	
	public static float getRailsYaw(BlockFace direction) {
		switch (direction) {
		case NORTH : return 0;
		case EAST : return 90;
		case WEST : return -90;
		case SOUTH : return 180;
		case SOUTH_WEST : return 135;
		case NORTH_WEST : return 45;
		case NORTH_EAST : return 135;
		case SOUTH_EAST : return 45;
		default : return 0;
		}
	}
	
	public static Vector faceToVector(BlockFace face, double length) {
		return faceToVector(face).multiply(length);
	}
	public static Vector faceToVector(BlockFace face) {
		return new Vector(face.getModX(), face.getModY(), face.getModZ());
	}
		
	public static float faceToYaw(BlockFace face) {
		switch (face) {
		case NORTH : return 0;
		case EAST : return 90;
		case SOUTH : return 180;	
		case WEST : return -90;
		case SOUTH_WEST : return -135;
		case NORTH_WEST : return -45;
		case NORTH_EAST : return 45;
		case SOUTH_EAST : return 135;
		}
		return 0;
	}	
	public static BlockFace yawToFace (float yaw) {
		return yawToFace(yaw, true);
	}
	public static BlockFace yawToFace(float yaw, boolean useSubCardinalDirections) {
		yaw = Util.normalAngle(yaw);
		if (useSubCardinalDirections) {
			switch ((int) yaw) {
			case 0 : return BlockFace.NORTH;
			case 45 : return BlockFace.NORTH_EAST;
			case 90 : return BlockFace.EAST;
			case 135 : return BlockFace.SOUTH_EAST;
			case 180 : return BlockFace.SOUTH;
			case -135 : return BlockFace.SOUTH_WEST;
			case -90 : return BlockFace.WEST;
			case -45 : return BlockFace.NORTH_WEST;
			}
			//Let's apply angle differences
			if (yaw >= -22.5 && yaw < 22.5) {
				return BlockFace.NORTH;
			} else if (yaw >= 22.5 && yaw < 67.5) {
				return BlockFace.NORTH_EAST;
			} else if (yaw >= 67.5 && yaw < 112.5) {
				return BlockFace.EAST;
			} else if (yaw >= 112.5 && yaw < 157.5) {
				return BlockFace.SOUTH_EAST;
			} else if (yaw >= -67.5 && yaw < -22.5) {
				return BlockFace.NORTH_WEST;
			} else if (yaw >= -112.5 && yaw < -67.5) {
				return BlockFace.WEST;
			} else if (yaw >= -157.5 && yaw < -112.5) {
				return BlockFace.SOUTH_WEST;
			} else {
				return BlockFace.SOUTH;
			}
		} else {
			switch ((int) yaw) {
			case 0 : return BlockFace.NORTH;
			case 90 : return BlockFace.EAST;
			case 180 : return BlockFace.SOUTH;
			case -90 : return BlockFace.WEST;
			}
			//Let's apply angle differences
			if (yaw >= -45 && yaw < 45) {
				return BlockFace.NORTH;
			} else if (yaw >= 45 && yaw < 135) {
				return BlockFace.EAST;
			} else if (yaw >= -135 && yaw < -45) {
				return BlockFace.WEST;
			} else {
				return BlockFace.SOUTH;
			}
		}
	}
	
}
