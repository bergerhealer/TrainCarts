package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;

public class TrackIterator implements Iterator<Block> {
	public static boolean isConnected(Block rail1, Block rail2, boolean bothways) {
		// Initial conditions
		if (rail1 == null || rail2 == null) {
			return false;
		} else if (BlockUtil.equals(rail1, rail2)) {
			return true;
		}
		// Use types to find out the directions to look
		int rail1type = rail1.getTypeId();
		int rail2type = rail2.getTypeId();
		if (!Util.ISTCRAIL.get(rail1type) || !Util.ISTCRAIL.get(rail2type)) {
			return false;
		}
		BlockFace dir1, dir2;
		if (Util.ISVERTRAIL.get(rail1type)) {
			dir1 = Util.getVerticalFace(rail2.getY() > rail1.getY());
		} else {
			dir1 = FaceUtil.getDirection(rail1, rail2, false);
		}
		if (Util.ISVERTRAIL.get(rail2type)) {
			dir2 = Util.getVerticalFace(rail1.getY() > rail2.getY());
		} else {
			dir2 = FaceUtil.getDirection(rail2, rail1, false);
		}
		// Find rails
		if (bothways) {
			return canReach(rail1, rail2, dir1) && canReach(rail2, rail1, dir2);
		} else {
			return canReach(rail1, rail2, dir1) || canReach(rail2, rail1, dir2);
		}
	}

	private static boolean canReach(Block rail, Block destination, BlockFace preferredFace) {
		// Initial conditions
		if (rail == null || destination == null || !Util.ISTCRAIL.get(destination)) {
			return false;
		} else if (BlockUtil.equals(rail, destination)) {
			return true;
		}
		BlockFace dir;
		int railType = rail.getTypeId();
		if (MaterialUtil.ISRAILS.get(railType)) {
			dir = BlockUtil.getRails(rail).getDirection().getOppositeFace();
		} else if (Util.ISVERTRAIL.get(railType)) {
			dir = BlockFace.UP;
		} else if (MaterialUtil.ISPRESSUREPLATE.get(railType)) {
			dir = Util.getPlateDirection(rail).getOppositeFace();
			if (dir == BlockFace.SELF) dir = preferredFace; 
		} else {
			return false;
		}
		BlockFace[] faces = FaceUtil.getFaces(dir);
		if (faces[0] == preferredFace) {
			if (canReach(rail, faces[0], destination)) return true;
			if (canReach(rail, faces[1], destination)) return true;
		} else {
			if (canReach(rail, faces[1], destination)) return true;
			if (canReach(rail, faces[0], destination)) return true;
		}
		return false;
	}
	public static boolean canReach(Block rail, BlockFace direction, Block destination) {
		TrackIterator iter = createFinder(rail, direction, destination);
		while (iter.hasNext()) {
			if (BlockUtil.equals(iter.next(), destination)) return true;
		}
		return false;
	}

	/**
	 * Creates a track iterator which is meant to find a destination block from a starting block
	 * 
	 * @param startBlock to start iterating from
	 * @param direction to start iterating to
	 * @param destination to try to find
	 * @return Track Iterator to find the destination
	 */
	public static TrackIterator createFinder(Block startBlock, BlockFace direction, Block destination) {
		final int maxDistance = Math.max(BlockUtil.getManhattanDistance(startBlock, destination, true), 2);
		return new TrackIterator(startBlock, direction, maxDistance, false);
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
	private Set<IntVector3> coordinates = new HashSet<IntVector3>();

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
		if (rails != null) {
			final BlockFace railsDirection = rails.getDirection();
			if (rails.isCurve()) {
				// Make sure curve directions are properly adjusted
				BlockFace[] p = FaceUtil.getFaces(railsDirection.getOppositeFace());
				if (p[0] != this.nextdirection && p[1] != this.nextdirection) {
					BlockFace from = this.nextdirection.getOppositeFace();
					if (p[0] == from) {
						this.nextdirection = p[1];
					} else if (p[1] == from) {
						this.nextdirection = p[0];
					}
				}
			} else if (rails.isOnSlope() && direction == railsDirection && Util.isVerticalAbove(startblock, direction)) {
				// Vertical rail above - check
				this.nextdirection = BlockFace.UP;
			}
		}
	}

	/**
	 * Generates the next block and direction from the current block and direction
	 * 
	 * @return True if a next block was found, False if not
	 */
	private boolean genNext() {
		this.next = this.current.getRelative(this.currentdirection);

		// Vertical rail logic
		if (this.currentdirection == BlockFace.UP || this.currentdirection == BlockFace.DOWN) {
			// Continuing on to the next vertical rail
			if (Util.ISVERTRAIL.get(this.next)) {
				this.nextdirection = this.currentdirection;
				return true;
			}

			// Continuing on to a possible slope above?
			if (this.currentdirection == BlockFace.UP) {
				BlockFace dir = Util.getVerticalRailDirection(this.current.getData());
				Block nextSlope = this.next.getRelative(dir);
				if (MaterialUtil.ISRAILS.get(nextSlope) && Util.isSloped(nextSlope.getData())) {
					this.next = nextSlope;
					this.nextdirection = BlockUtil.getRails(this.next).getDirection();
					return true;
				}
			}
		}

		// Look at the current level, below and above to find rails
		if (!Util.ISTCRAIL.get(this.next)) {
			if (!Util.ISTCRAIL.get(this.next = this.next.getRelative(BlockFace.UP))) {
				if (!Util.ISTCRAIL.get(this.next = this.next.getRelative(0, -2, 0))) {
					return false;
				}
			}
		}

		// Found normal rails here
		Rails rails = BlockUtil.getRails(this.next);
		if (rails == null) {
			// handle non-rails blocks
			int type = this.next.getTypeId();
			// Pressure plate
			if (MaterialUtil.ISPRESSUREPLATE.get(type)) {
				this.nextdirection = this.currentdirection;
				return true;
			}
			// Vertical rail
			if (Util.ISVERTRAIL.get(type)) {
				this.nextdirection = Util.getVerticalFace(this.next.getY() > this.current.getY());
				return true;
			}
		} else {
			final BlockFace railsDirection = rails.getDirection();
			// Special slope logic
			if (rails.isOnSlope()) {
				// Moving down a vertical rail onto a slope - fix direction
				if (this.currentdirection == BlockFace.DOWN) {
					this.nextdirection = railsDirection.getOppositeFace();
					return true;
				}

				// If vertical rail above, change next direction to up
				if (Util.isVerticalAbove(this.next, this.currentdirection)) {
					this.nextdirection = BlockFace.UP;
					return true;
				}
			}

			// Get a set of possible directions to go to
			BlockFace[] possible = FaceUtil.getFaces(railsDirection.getOppositeFace());

			// simple forward - always true
			for (BlockFace newdir : possible) {
				if (newdir == this.currentdirection) {
					this.nextdirection = this.currentdirection;
					return true;
				}
			}

			// Get connected faces
			BlockFace dir = this.currentdirection.getOppositeFace();
			if (possible[0].equals(dir)) {
				this.nextdirection = possible[1];
			} else if (possible[1].equals(dir)) {
				this.nextdirection = possible[0];
				// south-east rule
			} else if (possible[0] == BlockFace.SOUTH || possible[0] == BlockFace.EAST) {
				this.nextdirection = possible[0];
			} else {
				this.nextdirection = possible[1];
			}
			return true;
		}
		return false;
	}
	
	public int getDistance() {
		return this.distance;
	}
	
	public boolean hasNext() {
		return this.next != null && this.nextdirection != null;
	}

	private boolean genNextBlock() {
		if (this.distance >= this.maxdistance || this.current == null && this.currentdirection == null) {
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
		if (genNext() && this.coordinates.add(new IntVector3(this.next))) {
			this.distance++;
			return true;
		}
		return false;
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
		if (!this.genNextBlock()) {
			this.stop();
		}
		return this.current;
	}
	
	public void remove() {
		throw new UnsupportedOperationException("TrackIterator.remove is not supported");
	}
}
