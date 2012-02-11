package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

/*
 * A wrapper around the track iterator, which walks along the tracks
 * The walk step distance can be set
 */
public class TrackWalkIterator {
	
	private Location current;
	private Location next;
	private Vector direction;
	private double remainingdistance;
	private double stepsize = TrainCarts.cartDistance;
	private final TrackIterator iter;
	
	public TrackWalkIterator(Block start, BlockFace direction) {
		this(start.getLocation().add(0.5, 0, 0.5), direction);
	}
	public TrackWalkIterator(Location start) {
		this(start, FaceUtil.yawToFace(start.getYaw()));
	}
	public TrackWalkIterator(Location start, BlockFace direction) {
		this(start, direction, false);
	}
	public TrackWalkIterator(Location start, BlockFace direction, boolean onlyIfLoaded) {
		this.iter = new TrackIterator(start.getBlock(), direction, onlyIfLoaded);
		this.iter.next();
		this.next = start;
		if (this.iter.hasNext()) {
			this.genDirection();
			this.genNext();
		}
	}
	
	public TrackWalkIterator setStep(double size) {
		this.stepsize = size;
		return this;
	}
	
	private void genDirection() {
		Block old = this.iter.current();
		Block next = this.iter.next();
		this.direction = new Vector(next.getX() - old.getX(), next.getY() - old.getY(), next.getZ() - old.getZ());
		this.remainingdistance = 1.0;
	}
		
	private void genNext() {
		if (this.current == null) return;
		this.next = this.current.clone();
		double d = this.stepsize;
		while (d > 0 && this.iter.hasNext()) {
			if (d > this.remainingdistance) {
				//move past this block
				d -= this.remainingdistance;
				this.next.add(this.direction.multiply(this.remainingdistance));
				this.genDirection();
			} else {
				//move a bit past this block
				this.next.add(this.direction.clone().multiply(d));
				this.remainingdistance -= d;
				BlockFace dir = FaceUtil.getDirection(this.direction.getX(), this.direction.getZ(), false);
				this.next.setYaw(FaceUtil.faceToYaw(dir));
				//success
				return;
			}
		}
		//failed
		this.next = null;
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
		
}
