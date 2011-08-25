package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

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
	
	public static Location[] walk(Block start, BlockFace direction, int size, double stepsize) {
		return new TrackMap(start, direction, size, stepsize).walk(size, stepsize);
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
		Rails rails = Util.getRails(last());
		if (rails == null) return null;
		
		//Get a set of possible directions to go
		BlockFace[] possible = Util.getFaces(rails.getDirection());
		
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
		loc.setYaw(Util.getFaceYaw(dir));
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
		Location[] guide = getPoints();
		Location[] rval = new Location[stepCount];
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
		if (next != null && !Util.isRails(next)) {
			Block tmp = next.getRelative(BlockFace.UP);
			if (!Util.isRails(tmp)) {
				tmp = next.getRelative(BlockFace.DOWN);
				if (!Util.isRails(tmp)) {
					//Failed rails validation, no next track is possible
					return null;
				}
			}
			next = tmp;
		}
		totaldistance += last().getLocation().distance(next.getLocation());
		this.add(next);
		return next;
	}
}