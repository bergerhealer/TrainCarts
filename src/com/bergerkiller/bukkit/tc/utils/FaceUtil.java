package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Util;

public class FaceUtil {
	public static final BlockFace[] axis = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.EAST};
	public static final BlockFace[] attachedFaces = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, 
		BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP};
	public static final BlockFace[] attachedFacesDown = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, 
		BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
	
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
		switch (main) {
		case NORTH :
		case SOUTH : return new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH};
		case EAST :
		case WEST : return new BlockFace[] {BlockFace.EAST, BlockFace.WEST};
		case SOUTH_EAST : return new BlockFace[] {BlockFace.SOUTH, BlockFace.EAST};
		case SOUTH_WEST : return new BlockFace[] {BlockFace.SOUTH, BlockFace.WEST};	
		case NORTH_EAST : return new BlockFace[] {BlockFace.NORTH, BlockFace.EAST};
		case NORTH_WEST : return new BlockFace[] {BlockFace.NORTH, BlockFace.WEST};
		case UP :
		case DOWN : return new BlockFace[] {BlockFace.DOWN, BlockFace.UP};
		default : return new BlockFace[] {BlockFace.SELF, BlockFace.SELF};
		}
	}
	public static BlockFace rotate(BlockFace from, int notchCount) {
		return yawToFace(faceToYaw(from) + notchCount * 45);
	}
	
	public static BlockFace getRailsCartDirection(final BlockFace raildirection) {
		switch (raildirection) {
		case NORTH_EAST :
		case SOUTH_WEST : return BlockFace.NORTH_WEST;
		case NORTH_WEST :
		case SOUTH_EAST : return BlockFace.SOUTH_WEST;
		default : return raildirection;
		}
	}
	
//	public static float getRailsYaw(final BlockFace direction) {
//		switch (direction) {
//		case NORTH : return 0;
//		case EAST : return 90;
//		case WEST : return -90;
//		case SOUTH : return 180;
//		case SOUTH_WEST : return 135;
//		case NORTH_WEST : return 45;
//		case NORTH_EAST : return 135;
//		case SOUTH_EAST : return 45;
//		default : return 0;
//		}
//	}
	public static boolean isSubCardinal(final BlockFace face) {
		switch (face) {
		case NORTH_EAST : return true;
		case SOUTH_EAST : return true;
		case SOUTH_WEST : return true;
		case NORTH_WEST : return true;
		default : return false;
		}
	}
	public static boolean hasSubDifference(final BlockFace face1, final BlockFace face2) {
		if (face1 == face2) return true;
		switch (face1) {
		case NORTH : return face2 == BlockFace.NORTH_EAST || face2 == BlockFace.NORTH_WEST;
		case NORTH_EAST : return face2 == BlockFace.NORTH || face2 == BlockFace.EAST;
		case EAST : return face2 == BlockFace.NORTH_EAST || face2 == BlockFace.SOUTH_EAST;
		case SOUTH_EAST : return face2 == BlockFace.EAST || face2 == BlockFace.SOUTH;
		case SOUTH : return face2 == BlockFace.SOUTH_EAST || face2 == BlockFace.SOUTH_WEST;
		case SOUTH_WEST : return face2 == BlockFace.SOUTH || face2 == BlockFace.WEST;
		case WEST : return face2 == BlockFace.SOUTH_WEST || face2 == BlockFace.NORTH_WEST;
		case NORTH_WEST : return face2 == BlockFace.WEST || face2 == BlockFace.NORTH;
		default : return false;
		}
	}
	
	public static Vector faceToVector(BlockFace face, double length) {
		return faceToVector(face).multiply(length);
	}
	public static Vector faceToVector(BlockFace face) {
		return new Vector(face.getModX(), face.getModY(), face.getModZ());
	}
	
	public static BlockFace getDirection(Block from, Block to, boolean useSubCardinalDirections) {
		return getDirection(to.getX() - from.getX(), to.getZ() - from.getZ(), useSubCardinalDirections);
	}
	public static BlockFace getDirection(Vector movement) {
		return getDirection(movement, true);
	}
	public static BlockFace getDirection(Vector movement, boolean useSubCardinalDirections) {
		return getDirection(movement.getX(), movement.getZ(), useSubCardinalDirections);
	}
	public static BlockFace getDirection(final double dx, final double dz, boolean useSubCardinalDirections) {
		if (useSubCardinalDirections) {
			if (dz < 0) {
				if (dx < 0) {
					if (dx * 2 < dz) {
						return 2 * dz < dx ? BlockFace.NORTH_EAST : BlockFace.NORTH;
					} else {
						return BlockFace.EAST;
					}
				} else {
					if (dz * -2 > dx) {
						return -2 * dx < dz ? BlockFace.SOUTH_EAST : BlockFace.EAST;
					} else {
						return BlockFace.SOUTH;
					}
				}
			} else {
				if (dx < 0) {
					if (-2 * dz < dx) {
						return -2 * dx > dz ? BlockFace.NORTH_WEST : BlockFace.WEST;
					} else {
						return BlockFace.NORTH;
					}
				} else {
					if (2 * dx > dz) {
						return 2 * dz > dx ? BlockFace.SOUTH_WEST : BlockFace.SOUTH;
					} else {
						return BlockFace.WEST;
					}
				}
			}
		} else {
			if (dz < 0) {
				if (dx < 0) {
					return dx < dz ? BlockFace.NORTH : BlockFace.EAST;
				} else {
					return dx < -dz ? BlockFace.EAST : BlockFace.SOUTH;
				}
			} else {
				if (dx < 0) {
					return dx < -dz ? BlockFace.NORTH : BlockFace.WEST;
				} else {
					return dx < dz ? BlockFace.WEST : BlockFace.SOUTH;
				}
			}
		}
	}
	
	public static int getFaceYawDifference(BlockFace face1, BlockFace face2) {
		int angle = faceToYaw(face1) - faceToYaw(face2);
        while (angle <= -180) angle += 360;
        while (angle > 180) angle -= 360;
        return Math.abs(angle);
	}
	
	public static double cos(final BlockFace face) {
		switch (face) {
		case NORTH_WEST :
		case NORTH_EAST : return Util.halfRootOfTwo;
		case SOUTH_WEST :
		case SOUTH_EAST : return -Util.halfRootOfTwo;
		case SOUTH : return -1;
		case NORTH : return 1;
		default : return 0;
		}
	}
	public static double sin(final BlockFace face) {
		switch (face) {
		case SOUTH_EAST :
		case NORTH_EAST : return Util.halfRootOfTwo;
		case NORTH_WEST :
		case SOUTH_WEST : return -Util.halfRootOfTwo;
		case EAST : return 1;
		case WEST : return -1;
		default : return 0;
		}
	}
	
	public static int faceToYaw(final BlockFace face) {
		switch (face) {
		case NORTH : return 0;
		case EAST : return 90;
		case SOUTH : return 180;	
		case WEST : return -90;
		case SOUTH_WEST : return -135;
		case NORTH_WEST : return -45;
		case NORTH_EAST : return 45;
		case SOUTH_EAST : return 135;
		default : return 0;
		}
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