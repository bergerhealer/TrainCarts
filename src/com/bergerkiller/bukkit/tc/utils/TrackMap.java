package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

public class TrackMap extends ArrayList<Block> {
	private static final long serialVersionUID = 1L;
	private final TrackIterator iterator;	

	public TrackMap(Block start, BlockFace direction) {
		this(new TrackIterator(start, direction));
	}
	public TrackMap(Block start, BlockFace direction, int size) {
		this(new TrackIterator(start, direction, size, false));
		this.add(start);
		for (int i = 0;i < size;i++) this.next();
	}
	public TrackMap(Block start, BlockFace direction, int size, double stepsize) {
		this(new TrackIterator(start, direction, size, false));
		stepsize *= size;
		while (this.getDistance() < stepsize && this.hasNext()) {
			this.next();
		}
	}
	public TrackMap(final TrackIterator iterator) {
		this.iterator = iterator;
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

	public Block last() {
		return last(0);
	}
	public Block last(int index) {
		index = size() - index - 1;
		if (index < 0) return null;
		return get(index);
	}
	public int getDistance() {
		return this.iterator.getDistance();
	}
	
	public Block getBlock() {
		return this.iterator.current();
	}
	public BlockFace getDirection() {
		return this.iterator.currentDirection();
	}
	public BlockFace getDirection(Block from) {
		return getDirection(this.indexOf(from));
	}
	public BlockFace getDirection(int index) {
		if (index > this.size() - 2) return this.getDirection();
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
						prev = MathUtil.stage(prev, guide[guideindex], towalk / tmpnext);
						break;
					}
				}
				rval[i] = prev;
			}
		}
		return rval;
	}
	
	public TrackIterator iterator() {
		return this.iterator;
	}
	public boolean hasNext() {
		return this.iterator.hasNext();
	}
	public Block next() {
		Block next = this.iterator.next();
		if (next != null) this.add(next);
		return next;
	}
		
	public Block[] getAttachedSignBlocks() {
		return BlockUtil.getSignsAttached(this.getBlock());
	}
		
}