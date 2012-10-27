package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;

public class TrackIterator implements Iterator<Block> {
	
	public static boolean canReach(Block rail, BlockFace direction, Block destination) {
		return canReach(rail, direction, destination, 0);
	}
	public static boolean canReach(Block rail, Block destination) {
		return canReach(rail, destination, 0);
	}
	public static boolean isConnected(Block rail1, Block rail2, boolean bothways) {
		return isConnected(rail1, rail2, bothways, 0);
	}
	public static boolean isConnected(Block rail1, Block rail2, boolean bothways, int stepcount) {
		if (!Util.isRails(rail1)) return false;
		if (!Util.isRails(rail2)) return false;
		if (BlockUtil.equals(rail1, rail2)) return true;
		BlockFace direction = FaceUtil.getDirection(rail1, rail2, false);
		if (bothways) {
			return canReach(rail1, rail2, direction, stepcount) && canReach(rail2, rail1, direction.getOppositeFace(), stepcount);
		} else {
			return canReach(rail1, rail2, direction, stepcount) || canReach(rail2, rail1, direction.getOppositeFace(), stepcount);
		}
	}
	public static boolean canReach(Block rail, Block destination, int stepcount) {
		return canReach(rail, destination, null, stepcount);
	}
	public static boolean canReach(Block rail, Block destination, BlockFace preferredFace, int stepcount) {
		if (stepcount == 0) {
			stepcount = BlockUtil.getManhattanDistance(rail, destination, false);
			if (stepcount < 2) stepcount = 2;
		}
		BlockFace dir;
		if (BlockUtil.isRails(rail)) {
			dir = BlockUtil.getRails(rail).getDirection().getOppositeFace();
		} else if (Util.isPressurePlate(rail.getTypeId())) {
			dir = Util.getPlateDirection(rail).getOppositeFace();
			if (dir == BlockFace.SELF) dir = preferredFace; 
		} else {
			return false;
		}
		BlockFace[] faces = FaceUtil.getFaces(dir);
		if (faces[0] == preferredFace) {
			if (canReach(rail, faces[0], destination, stepcount)) return true;
			if (canReach(rail, faces[1], destination, stepcount)) return true;
		} else {
			if (canReach(rail, faces[1], destination, stepcount)) return true;
			if (canReach(rail, faces[0], destination, stepcount)) return true;
		}
		return false;
	}
	public static boolean canReach(Block rail, BlockFace direction, Block destination, int stepcount) {
		if (BlockUtil.equals(rail, destination)) return true;
		if (!Util.isRails(rail)) return false;
		if (!Util.isRails(destination)) return false;
		if (stepcount == 0) {
			stepcount = BlockUtil.getManhattanDistance(rail, destination, false);
			if (stepcount < 2) stepcount = 2;
		}
		TrackIterator iter = new TrackIterator(rail, direction, stepcount, false);
		while (iter.hasNext()) {
			if (BlockUtil.equals(iter.next(), destination)) return true;
		}
		return false;
	}
	
	public static Block getNextTrack(Block from, BlockFace direction) {
		Block next = from.getRelative(direction);
		if (direction == BlockFace.UP) {
			if (Util.isVerticalRail(next.getTypeId())) {
				return next;
			} else {
				// Maybe a slope to go to?
				BlockFace dir = Util.getVerticalRailDirection(from.getData());
				next = next.getRelative(dir);
				if (BlockUtil.isRails(next) && Util.isSloped(next.getData())) {
					return next;
				}
			}
			return null;
		}
		if (!Util.isRails(next)) {
			next = next.getRelative(BlockFace.UP);
			if (!Util.isRails(next)) {
				next = next.getRelative(0, -2, 0);
				if (!Util.isRails(next)) {
					return null;
				}
			}
		}
		return next;
	}
	
	/*
	 * The 'current' is only to return in functions
	 * The 'next' will replace current and is regenerated
	 */
	private Block current;
	private BlockFace currentdirection;
	private Block next;
	private BlockFace nextdirection;
	private int distance;
	private final int maxdistance;
	private final boolean onlyInLoadedChunks;
	private Set<ChunkCoordinates> coordinates = new HashSet<ChunkCoordinates>();
	
	public TrackIterator(Block startblock, BlockFace direction) {
		this(startblock, direction, false);
	}
	public TrackIterator(Block startblock, BlockFace direction, final boolean onlyInLoadedChunks) {
		this(startblock, direction, 16000, onlyInLoadedChunks);
	}
	public TrackIterator(Block startblock, BlockFace direction, final int maxdistance, final boolean onlyInLoadedChunks) {
		this.maxdistance = maxdistance;
		this.onlyInLoadedChunks = onlyInLoadedChunks;
		this.nextdirection = direction;
		this.next = startblock;
		this.distance = 0;
		//adjust direction if moving into a curve
		Rails rails = BlockUtil.getRails(this.next);
		if (rails != null && rails.isCurve()) {
			BlockFace[] p = FaceUtil.getFaces(rails.getDirection().getOppositeFace());
			if (p[0] != this.nextdirection && p[1] != this.nextdirection) {
				BlockFace from = this.nextdirection.getOppositeFace();
				if (p[0] == from) {
					this.nextdirection = p[1];
				} else if (p[1] == from) {
					this.nextdirection = p[0];
				}
			}
		}
	}
	
	private void genNextBlock() {
		if (!genNext()) this.stop();
	}
	private boolean genNext() {
		if (this.distance < maxdistance && this.current != null && this.currentdirection != null) {
			this.distance++;
		} else {
			return false;
		}
		if (this.onlyInLoadedChunks) {
			//loaded?
			int x = this.current.getX() + this.currentdirection.getModX();
			int z = this.current.getZ() + this.currentdirection.getModZ();
			if (!this.current.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
				return false;
			}
		}
		
		//use current direction and block to get next block
		this.next = getNextTrack(this.current, this.currentdirection);
		if (this.next == null) return false;
		if (!this.coordinates.add(BlockUtil.getCoordinates(this.next))) return false;

		//Next direction?
		Rails rails = BlockUtil.getRails(this.next);
		if (rails == null) {
			//handle non-rails blocks
			int type = this.next.getTypeId();
			if (Util.isPressurePlate(type)) {
				this.nextdirection = this.currentdirection;
			} else if (Util.isVerticalRail(type)) {
				this.nextdirection = BlockFace.UP;
			}
			return true;
		}

		//Get a set of possible directions to go
		BlockFace[] possible = FaceUtil.getFaces(rails.getDirection().getOppositeFace());
		
		//simple forward - always true
		for (BlockFace newdir : possible) {
			if (newdir.equals(this.currentdirection)) {
				this.nextdirection = this.currentdirection;
				return true;
			}
		}
		
		//Get connected faces
		BlockFace dir = this.currentdirection.getOppositeFace();
		if (possible[0].equals(dir)) {
			this.nextdirection = possible[1];
		} else if (possible[1].equals(dir)) {
			this.nextdirection = possible[0];
			//south-west rule
		} else if (possible[0] == BlockFace.WEST || possible[0] == BlockFace.SOUTH) {
			this.nextdirection = possible[0];
		} else {
			this.nextdirection = possible[1];
		}
		return true;
	}
	
	public int getDistance() {
		return this.distance;
	}
	
	public boolean hasNext() {
		return this.next != null && this.nextdirection != null;
	}
	
	public void stop() {
		this.next = null;
		this.nextdirection = null;
	}
	
	public BlockFace currentDirection() {
		return this.currentdirection;
	}
	public Block current() {
		return this.current;
	}
	public Rails currentRails() {
		return BlockUtil.getRails(this.current);
	}

	public BlockFace peekNextDirection() {
		return this.nextdirection;
	}
	public Block peekNext() {
		return this.next;
	}
	public Block next() {
		this.current = this.next;
		this.currentdirection = this.nextdirection;
		this.genNextBlock();
		return this.current;
	}
	
	public void remove() {
		throw new UnsupportedOperationException("TrackIterator.remove is not supported");
	}

}
