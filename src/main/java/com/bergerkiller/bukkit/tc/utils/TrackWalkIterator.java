package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/*
 * A wrapper around the track iterator, which walks along the tracks
 * The walk step distance can be set
 */
public class TrackWalkIterator {
	private double stepsize = TrainCarts.cartDistance;
	private Location current, next;
	private final TrackWalkingPoint walker;

	/**
	 * Constructs a new walking iterator
	 * 
	 * @param startRail (NOT position block)
	 * @param direction to start walking into
	 */
	public TrackWalkIterator(Block startRail, BlockFace direction) {
		Block startBlock = RailType.getType(startRail).findMinecartPos(startRail);
		Location startLoc = startBlock == null ? null : startBlock.getLocation().add(0.5, 0.5, 0.5);
		this.walker = new TrackWalkingPoint(startLoc, startRail, direction);
		this.current = this.next = walker.currentPosition == null ? null : walker.currentPosition.clone();
	}

	/**
	 * Constructs a new walking iterator
	 * 
	 * @param start position of the Minecart (NOT the rails!)
	 * @param direction to start walking into
	 */
	public TrackWalkIterator(Location start, BlockFace direction) {
		Block railsBlock = null;
		Block startBlock = start.getBlock();
		for (RailType type : RailType.values()) {
			if ((railsBlock = type.findRail(startBlock)) != null) {
				break;
			}
		}
		this.walker = new TrackWalkingPoint(start, railsBlock, direction);
		this.current = this.next = this.walker.currentPosition.clone();
	}

	public TrackWalkIterator setStep(double size) {
		this.stepsize = size;
		return this;
	}

	private void genNext() {
		this.next = walker.move(stepsize) ? walker.currentPosition.clone() : null;
	}

	public boolean hasNext() {
		return this.next != null;
	}

	public Location current() {
		return this.current;
	}

	public Location peekNext() {
		return this.next;
	}

	public Location next() {
		this.current = this.next;
		this.genNext();
		return this.current;
	}

	public static Location[] walk(Block start, BlockFace direction, int size, double stepsize) {
		TrackWalkIterator iter = new TrackWalkIterator(start, direction);
		Location[] rval = new Location[size];
		for (int i = 0; i < size; i++) {
			if (iter.hasNext()) {
				rval[i] = iter.next();
			} else {
				rval[i] = rval[i - 1];
			}
		}
		return rval;
	}
}
