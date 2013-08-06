package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class TrackIterator implements Iterator<Block> {
	/*
	 * The 'current' is only to return in functions
	 * The 'next' will replace current and is regenerated
	 */
	private TrackMovingPoint movingPoint;
	private int distance;
	private double cartDistance;
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
		this.reset(startblock, direction);
	}

	/**
	 * Resets this iterator, allowing it to be used again.
	 * 
	 * @param startBlock to set to
	 * @param startDirection to set to
	 * @return this Track Iterator
	 */
	public TrackIterator reset(Block startBlock, BlockFace startDirection) {
		this.coordinates.clear();
		this.distance = 0;
		this.cartDistance = 0.0;
		this.movingPoint = new TrackMovingPoint(startBlock, startDirection);
		return this;
	}

	/**
	 * Gets the distance travelled in full blocks, that is, from block to block
	 * it is incremented by one. It returns how many times a successful {@link #next()}
	 * was executed.
	 * 
	 * @return Block Distance
	 */
	public int getDistance() {
		return this.distance;
	}

	/**
	 * Gets the total amount of distance when travelled by cart.
	 * This is equal as or less than the {@link #getDistance()} method returns.
	 * Unlike getDistance(), getCartDistance() adds less distance for curves.
	 * 
	 * @return Cart Distance
	 */
	public double getCartDistance() {
		return this.cartDistance;
	}

	@Override
	public boolean hasNext() {
		return this.movingPoint.hasNext() && this.distance <= this.maxdistance;
	}

	private void genNextBlock() {
		// If specified, be sure to stay within loaded chunks
		if (this.onlyInLoadedChunks) {
			int x = this.movingPoint.current.getX() + this.movingPoint.currentDirection.getModX();
			int z = this.movingPoint.current.getZ() + this.movingPoint.currentDirection.getModZ();
			if (!this.movingPoint.current.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
				this.movingPoint.next(false);
				return;
			}
		}
		// We are still in loaded chunks, go on!
		this.movingPoint.next();
		if (this.movingPoint.hasNext()) {
			// If already contained, skip it
			if (!this.coordinates.add(new IntVector3(this.movingPoint.nextTrack))) {
				this.movingPoint.clearNext();
			}
		}
	}

	public void stop() {
		this.movingPoint.clearNext();
	}

	public BlockFace currentDirection() {
		return this.movingPoint.currentDirection;
	}

	public Block currentPos() {
		return this.movingPoint.current;
	}

	public Block current() {
		return this.movingPoint.currentTrack;
	}

	public Rails currentRails() {
		return BlockUtil.getRails(this.current());
	}

	public BlockFace peekNextDirection() {
		return this.movingPoint.nextDirection;
	}

	public Block peekNext() {
		return this.movingPoint.nextTrack;
	}

	@Override
	public Block next() {
		if (!this.hasNext()) {
			throw new NoSuchElementException("No next track is available");
		}
		BlockFace oldDirection = this.currentDirection();
		this.genNextBlock();
		BlockFace newDirection = this.currentDirection();
		this.distance++;
		if (oldDirection == newDirection || oldDirection == newDirection.getOppositeFace()) {
			// Took a straight piece
			this.cartDistance += 1.0;
		} else {
			// Took a curve
			this.cartDistance += MathUtil.HALFROOTOFTWO;
		}
		return this.current();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("TrackIterator.remove is not supported");
	}

	/**
	 * Tries to find a specific Block, calling {@link #next()} until no longer possible.
	 * 
	 * @param railsBlock to find
	 * @return True if the railsBlock was found, False if not
	 */
	public boolean tryFind(Block railsBlock) {
		while (hasNext()) {
			if (BlockUtil.equals(next(), railsBlock)) {
				return true;
			}
		}
		return false;
	}

	private boolean canReach(Block rail, Block destination, BlockFace[] faces, BlockFace preferredFace) {
		// First, check the preferred face
		for (BlockFace face : faces) {
			if (face == preferredFace) {
				if (this.reset(rail, face).tryFind(destination)) {
					return true;
				}
				break;
			}
		}
		// Now, check the other faces
		for (BlockFace face : faces) {
			if (face != preferredFace && this.reset(rail, face).tryFind(destination)) {
				return true;
			}
		}
		// No connection was found
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
		final int maxDistance = BlockUtil.getManhattanDistance(startBlock, destination, true) + 2;
		return new TrackIterator(startBlock, direction, maxDistance, false);
	}

	public static boolean canReach(Block rail, BlockFace direction, Block destination) {
		return createFinder(rail, direction, destination).tryFind(destination);
	}

	/**
	 * Checks whether two rails are connected, allowing Minecarts to move between the two
	 * 
	 * @param rail1
	 * @param rail2
	 * @param bothways - True to require a connection both from r1 to r2 and r2 to r1
	 * @return True if a connection exists, False if not
	 */
	public static boolean isConnected(Block rail1, Block rail2, boolean bothways) {
		// Initial conditions
		if (rail1 == null || rail2 == null) {
			return false;
		} else if (BlockUtil.equals(rail1, rail2)) {
			return true;
		}

		// Use rail types to find out the directions to look
		RailType rail1type = RailType.getType(rail1);
		RailType rail2type = RailType.getType(rail2);
		if (rail1type == RailType.NONE || rail2type == RailType.NONE) {
			return false;
		}
		BlockFace[] rail1dirs = rail1type.getPossibleDirections(rail1);
		BlockFace[] rail2dirs = rail2type.getPossibleDirections(rail2);
		if (rail1dirs.length == 0 || rail2dirs.length == 0) {
			return false;
		}

		// Figure out where a Minecart is located on this Rail
		Block pos1 = rail1type.findMinecartPos(rail1);
		Block pos2 = rail1type.findMinecartPos(rail2);

		// Figure out which direction is more likely to lead to the other
		BlockFace dir1 = getPreferredDirection(rail1dirs, pos1, pos2);
		BlockFace dir2 = getPreferredDirection(rail2dirs, pos2, pos1);

		// Now, start looking into the directions
		final int maxDistance = BlockUtil.getManhattanDistance(pos1, pos2, true) + 2;
		TrackIterator iter = new TrackIterator(null, null, maxDistance, false);
		if (bothways) {
			return iter.canReach(rail1, rail2, rail1dirs, dir1) && iter.canReach(rail2, rail1, rail2dirs, dir2);
		} else {
			return iter.canReach(rail1, rail2, rail1dirs, dir1) || iter.canReach(rail2, rail1, rail2dirs, dir2);
		}
	}

	private static BlockFace getPreferredDirection(BlockFace[] directions, Block from, Block to) {
		boolean preferred;
		for (BlockFace dir : directions) {
			if (FaceUtil.isVertical(dir)) {
				preferred = (dir == Util.getVerticalFace(to.getY() > from.getY()));
			} else {
				preferred = (dir == FaceUtil.getDirection(from, to, false));		
			}
			if (preferred) {
				return dir;	
			}
		}
		return directions[0];
	}
}
