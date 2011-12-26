package com.bergerkiller.bukkit.tc.Utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Minecart;
import org.bukkit.material.Attachable;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Rails;
import org.bukkit.material.Directional;

public class BlockUtil {
    
    /*
     * Prevents the need to read the lighting when using getState()
     * Can be a little bit faster :)
     */
    public static MaterialData getData(Block b) {
    	return b.getType().getNewData(b.getData());
    }
    public static int getBlockSteps(Location b1, Location b2, boolean checkY) {
    	int d = Math.abs(b1.getBlockX() - b2.getBlockX());
    	d += Math.abs(b1.getBlockZ() - b2.getBlockZ());
    	if (checkY) d +=  Math.abs(b1.getBlockY() - b2.getBlockY());
    	return d;
    }
    public static int getBlockSteps(Block b1, Block b2, boolean checkY) {
    	int d = Math.abs(b1.getX() - b2.getX());
    	d += Math.abs(b1.getZ() - b2.getZ());
    	if (checkY) d +=  Math.abs(b1.getY() - b2.getY());
    	return d;
    }
    
    public static boolean equals(Block block1, Block block2) {
    	return block1.getX() == block2.getX() && block1.getZ() == block2.getZ()
    			&& block1.getY() == block2.getY() && block1.getWorld() == block2.getWorld();    	
    }
        
    public static Block[] getRelative(Block main, BlockFace... faces) {
    	if (main == null) return new Block[0];
    	Block[] rval = new Block[faces.length];
    	for (int i = 0; i < rval.length; i++) {
    		rval[i] = main.getRelative(faces[i]);
    	}
    	return rval;
    }
    public static Block getAttachedBlock(Block b) {
    	MaterialData m = getData(b);
    	BlockFace face = BlockFace.DOWN;
    	if (m instanceof Attachable) {
    		face = ((Attachable) m).getAttachedFace();
    	}
    	return b.getRelative(face);
    }
    public static void setLever(Block lever, boolean down) {
    	if (lever.getType() == Material.LEVER) {
			byte data = lever.getData();
	        int newData;
	        if (down) {
	        	newData = data | 0x8;
	        } else {
	        	newData = data & 0x7;
	        }
	        if (newData != data) {
	            lever.setData((byte) newData, true);
	        }
    	}
    }
    public static void setRails(Block rails, BlockFace from, BlockFace to) {
    	setRails(rails, FaceUtil.combine(from, to).getOppositeFace());
    }
    public static void setRails(Block rails, BlockFace direction) {
    	Material type = rails.getType();
    	if (type == Material.RAILS) {
    		if (direction == BlockFace.NORTH) {
    			direction = BlockFace.SOUTH;
    		} else if (direction == BlockFace.EAST) {
    			direction = BlockFace.WEST;
    		}
    		byte olddata = rails.getData();
    		Rails r = (Rails) type.getNewData(olddata);
    		r.setDirection(direction, r.isOnSlope());
    		byte newdata = r.getData();
    		if (olddata != newdata) {
        		rails.setData(newdata);
    		}
    	}
    }
    
    public static boolean isRails(Material type) {
    	 return type == Material.RAILS || type == Material.POWERED_RAIL || type == Material.DETECTOR_RAIL;
    }
    public static boolean isRails(int type) {
    	 return type == Material.RAILS.getId() || type == Material.POWERED_RAIL.getId() || type == Material.DETECTOR_RAIL.getId();
    }
    public static boolean isRails(Block b) {
    	if (b == null) return false;
    	return isRails(b.getTypeId());
    }
	public static Block getRailsBlock(Minecart m) {
		return getRailsBlock(m.getLocation());
	}
	public static Block getRailsBlock(Location from) {
		Block b = from.getBlock();
		if (isRails(b)) {
			return b;
		} else {
			b = b.getRelative(BlockFace.DOWN);
			if (isRails(b)) {
				return b;
			} else {
				return null;
			}
		}
	}
	public static Rails getRails(Block railsblock) {
		if (railsblock == null) return null;
		return getRails(getData(railsblock));
	}
	public static Rails getRails(MaterialData data) {
		if (data != null && data instanceof Rails) {
			return (Rails) data;
		}
		return null;
	}
	public static Rails getRails(Minecart m) {	
		return getRails(m.getLocation());
	}
	public static Rails getRails(Location loc) {
		return getRails(getRailsBlock(loc));
	}
	public static float getRailsYaw(Rails rails) {
		if (rails != null) {
			return FaceUtil.getRailsYaw(rails.getDirection());
		}
		return 0;
	}
	public static boolean isSign(Material material) {
		return material == Material.WALL_SIGN || material == Material.SIGN_POST;
	}
    public static boolean isSign(Block b) {
    	if (b == null) return false;
    	return isSign(b.getType());
    }
	public static Sign getSign(Block signblock) {
		if (isSign(signblock)) {
			return (Sign) signblock.getState();
		}
		return null;
	}
	public static BlockFace getFacing(Block b) {
		MaterialData data = getData(b);
		if (data != null && data instanceof Directional) {
			return ((Directional) data).getFacing();
		} else {
			return BlockFace.NORTH;
		}
	}
		
}
