package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.FaceUtil;

public class TrackMap extends ArrayList<Block> {
	private static final long serialVersionUID = 1L;
	private BlockFace direction;
	private double totaldistance = 0;
	public TrackMap(Block start, BlockFace direction) {
		this.direction = direction;
		this.add(start);
	}
	public TrackMap(Block start, BlockFace direction, int size) {
		this.direction = direction;
		this.add(start);
		for (int i = 0;i < size;i++) this.next();
	}
	public TrackMap(Block start, BlockFace direction, int size, double stepsize) {
		this.direction = direction;
		this.add(start);
		stepsize *= size;
		while (totaldistance < stepsize) {
			if (this.next() == null) break;
		}
	}
	public boolean find(Block rail, int maxstepcount) {
		Block next;
		for (;maxstepcount > 0; --maxstepcount) {
			next = this.next();
			if (next == null) return false;
			if (BlockUtil.equals(rail, next)) return true;
		}
		return false;
	}
	
	public static Location[] walk(Block start, BlockFace direction, int size, double stepsize) {
		return new TrackMap(start, direction, size, stepsize).walk(size, stepsize);
	}
	public static boolean isConnectedWith(Block from, Block to, BlockFace direction) {
		return isConnectedWith(from, to, direction, 0);
	}
	public static boolean isConnectedWith(Block from, Block to, BlockFace direction, int stepcount) {
		if (!BlockUtil.isRails(from)) return false;
		if (!BlockUtil.isRails(to)) return false;
		if (BlockUtil.equals(from, to)) return true;
		if (stepcount == 0) {
			stepcount = BlockUtil.getBlockSteps(from, to, false);
			if (stepcount < 2) stepcount = 2;
		}
		TrackMap map = new TrackMap(from, direction);
		return map.find(to, stepcount);
	}	
	public static boolean isConnected(Block rail1, Block rail2, boolean bothways) {
		return isConnected(rail1, rail2, 0, bothways);
	}
	public static boolean isConnected(Block rail1, Block rail2, int stepcount, boolean bothways) {
		if (!BlockUtil.isRails(rail1)) return false;
		if (!BlockUtil.isRails(rail2)) return false;
		if (BlockUtil.equals(rail1, rail2)) return true;
		if (stepcount == 0) {
			stepcount = BlockUtil.getBlockSteps(rail1, rail2, false);
			if (stepcount < 2) stepcount = 2;
		}
		float yaw = Util.getLookAtYaw(rail1, rail2);
		BlockFace direction = FaceUtil.yawToFace(yaw, false);
		TrackMap map1 = new TrackMap(rail1, direction);
		TrackMap map2 = new TrackMap(rail2, direction.getOppositeFace());
		if (bothways) {
			return map1.find(rail2, stepcount) && map2.find(rail1, stepcount);
		} else {
			return map1.find(rail2, stepcount) || map2.find(rail1, stepcount);
		}
	}
	
	public Block last() {
		return last(0);
	}
	public Block last(int index) {
		index = size() - index - 1;
		if (index < 0) return null;
		return get(index);
	}
		
	public double getTotalDistance() {
		return totaldistance;
	}
	public BlockFace getNextDirection() {
		Rails rails = BlockUtil.getRails(last());
		if (rails == null) return null;
		
		//Get a set of possible directions to go
		BlockFace[] possible = FaceUtil.getFaces(rails.getDirection());
		
		//simple forward - always true
		for (BlockFace newdir : possible) {
			if (newdir.equals(direction)) {
				return newdir;
			}
		}
		
		//Get connected faces
		BlockFace dir = direction.getOppositeFace();
		if (possible[0].equals(dir)) {
			return possible[1];
		} else if (possible[1].equals(dir)) {
			return possible[0];
		} else {
			//Apply the South/west rule
			if (possible[0] == BlockFace.WEST || possible[0] == BlockFace.SOUTH) {
				return possible[0];
			} else {
				return possible[1];
			}
		}
	}
	
	public BlockFace getDirection() {
		return this.direction;
	}
	public BlockFace getDirection(Block from) {
		return getDirection(this.indexOf(from));
	}
	public BlockFace getDirection(int index) {
		if (index > this.size() - 2) return this.direction;
		if (index < 0) return BlockFace.SELF;
		Location from = this.get(index).getLocation();
		Location to = this.get(index + 1).getLocation();
		int motX = to.getBlockX() - from.getBlockX();
		if (motX > 0) {
			return BlockFace.SOUTH;
		} else if (motX < 0) {
			return BlockFace.NORTH;
		} else {
			int motZ = to.getBlockZ() - from.getBlockZ();
			if (motZ > 0) {
				return BlockFace.WEST;
			} else if (motZ < 0) {
				return BlockFace.EAST;
			} else {
				return BlockFace.SELF;
			}
		}
	}
	
	public Location getPoint(int index) {
		Location loc = this.get(index).getLocation();
		BlockFace dir = getDirection(index);
		loc.setYaw(FaceUtil.faceToYaw(dir) + 90);
		//loc.setYaw(Util.getRailsYaw(Util.getRails(this.get(index))));
		return loc;
	}
	public Location[] getPoints() {
		Location[] rval = new Location[this.size()];
		for (int i = 0;i < rval.length;i++) {
			rval[i] = getPoint(i);
		}
		return rval;
	}
	
	public Location[] walk(int stepCount, double stepSize) {
		if (stepCount == 0) return new Location[0];
		Location[] guide = getPoints();
		Location[] rval = new Location[stepCount];
		if (guide.length == 0) return new Location[0];
		rval[0] = guide[0];
		int guideindex = 1;
		for (int i = 1;i < stepCount;i++) {
			Location prev = rval[i - 1];
			double towalk = stepSize;
			

			if (guideindex > guide.length - 1) {
				//End of the tracks, no movement possible
				rval[i] = rval[i - 1];
			} else {
				//Subtract whole block distances (switch to next guideline)
				while (true) {
					double tmpnext = Double.MAX_VALUE;
					if (guideindex < guide.length - 1) {
						tmpnext = prev.distance(guide[guideindex]);
					}
					if (tmpnext <= towalk) {
						towalk -= tmpnext;
						prev = guide[guideindex];
						guideindex++;
					} else {
						//Use a staged point to add the remaining distance
						prev = Util.stage(prev, guide[guideindex], towalk / tmpnext);
						break;
					}
				}
				rval[i] = prev;
			}
		}
		return rval;
	}
	
	public Block next() {
		//Get the direction for the next piece of track
		BlockFace dir = getNextDirection();
		if (dir == null) return null; 
		direction = dir;
		
		//Get the Rails block at this direction (up/down?)
		Block next = last().getRelative(direction);
		if (next != null && !BlockUtil.isRails(next)) {
			Block tmp = next.getRelative(BlockFace.UP);
			if (!BlockUtil.isRails(tmp)) {
				tmp = next.getRelative(BlockFace.DOWN);
				if (!BlockUtil.isRails(tmp)) {
					//Failed rails validation, no next track is possible
					return null;
				}
			}
			next = tmp;
		}
		//prevent loops by checking for double every 20 tiles
    if (this.size() % 20 == 0){
      if (this.contains(next)){
        return null;
      }
    }
		totaldistance += last().getLocation().distance(next.getLocation());
		this.add(next);
		return next;
	}
	
	public Block getSignBlock() {
		Block b = last();
		if (b == null) return null;
		b = b.getRelative(0, -2, 0);
		if (BlockUtil.isSign(b)) return b;
		return null;
	}
	public Sign getSign() {
		return BlockUtil.getSign(getSignBlock());
    }
		
}