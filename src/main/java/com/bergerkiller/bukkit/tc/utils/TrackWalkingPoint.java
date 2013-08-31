package com.bergerkiller.bukkit.tc.utils;

import java.util.NoSuchElementException;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * A Moving point implementation that allows one to 'walk' along rails without
 * restricting to full-block movement, allowing for accurate distance calculations
 * and accurate Minecart positioning information for spawning on rails.
 */
public class TrackWalkingPoint extends TrackMovingPoint {
	/**
	 * The current position of the current rails
	 */
	public Location currentPosition;
	/**
	 * The next position of the next rails
	 */
	public Location nextPosition;
	/**
	 * The direction Vector to move from the current to the next
	 */
	public Vector direction;
	/**
	 * The distance between the current and the next position
	 */
	public double trackDistance = 0.0;

	public TrackWalkingPoint(Location startPos, Block startRail, BlockFace startDirection) {
		super(startRail, startDirection);
		if (startPos == null) {
			this.clearNext();
			return;
		}
		this.currentPosition = startPos.clone();
		this.nextPosition = startPos.clone();
		this.direction = FaceUtil.faceToVector(startDirection).normalize();
		calcRotation();
		// Skip the first block, as to avoid moving 'backwards' one block
		if (super.hasNext()) {
			super.next(true);
		}
	}

	/**
	 * Moves the distance specified, calling {@link #next()} as often as is needed.
	 * Be sure to call a single {@link #next()} prior to doing all movement in general.
	 * The {@link #currentPosition} is updated.
	 * 
	 * @param distance to move
	 * @return True if movement was successful, False if not
	 */
	public boolean move(double distance) {
		// If no next-position information is available - fail here
		// There is no valid direction and track distance either then!
		if (!hasNext()) {
			return false;
		}
		double remaining = distance;
		while (true) {
			// No movement remaining, skip the current block and go to the next
			// Using while instead of if, just in case the next distance stays 0
			while (this.trackDistance == 0.0) {
				this.next();
				// We need to move further, if impossible then we conclude failure
				if (!hasNext()) {
					return false;
				}
			}
			if (remaining > this.trackDistance) {
				// Move past the current block entirely
				remaining -= this.trackDistance;
				currentPosition.add(trackDistance * direction.getX(), trackDistance * direction.getY(), trackDistance * direction.getZ());
				trackDistance = 0.0;
			} else {
				// Move a minor part of the current block - we are successful!
				trackDistance -= remaining;
				currentPosition.add(remaining * direction.getX(), remaining * direction.getY(), remaining * direction.getZ());
				// Final movement - use direction to calculate yaw and pitch
				calcRotation();
				return true;
			}
		}
	}

	@Override
	public void next(boolean allowNext) {
		if (!hasNext()) {
			throw new NoSuchElementException("No next element is available");
		}
		
		// Shift over the current position
		this.currentPosition.setX(this.nextPosition.getX());
		this.currentPosition.setY(this.nextPosition.getY());
		this.currentPosition.setZ(this.nextPosition.getZ());

		// Proceed to calculate the next position
		this.nextPosition.setX((this.next.getX() + 0.5) - (this.nextDirection.getModX() * 0.5));
		this.nextPosition.setY((this.next.getY() + 0.5) - (this.nextDirection.getModY() * 0.5));
		this.nextPosition.setZ((this.next.getZ() + 0.5) - (this.nextDirection.getModZ() * 0.5));

		// Calculate the resulting direction and distance
		this.direction.setX(this.nextPosition.getX() - this.currentPosition.getX());
		this.direction.setY(this.nextPosition.getY() - this.currentPosition.getY());
		this.direction.setZ(this.nextPosition.getZ() - this.currentPosition.getZ());
		this.trackDistance = this.direction.length();
		this.direction.normalize();

		// Load in the new track information for next time
		super.next(allowNext);
	}

	private void calcRotation() {
		currentPosition.setYaw(MathUtil.getLookAtYaw(direction));
		currentPosition.setPitch(MathUtil.getLookAtPitch(direction.getX(), direction.getY(), direction.getZ()));
	}
}
