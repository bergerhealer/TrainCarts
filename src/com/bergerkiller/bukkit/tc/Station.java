package com.bergerkiller.bukkit.tc;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

/**
 * Represents the Station sign information
 */
public class Station {
	private final double length;
	private final long delay;
	private final BlockFace instruction;
	private final Direction nextDirection;
	private final MinecartMember centerCart;
	private final boolean valid;

	public Station(SignActionEvent info) {
		this.delay = ParseUtil.parseTime(info.getLine(2));
		this.nextDirection = Direction.parse(info.getLine(3));
		this.centerCart = info.isCartSign() ? info.getMember() : info.getGroup().middle();

		//First, get the direction of the tracks above
		BlockFace dir = info.getRailDirection();
		//which directions to move, or brake?
		if (dir == BlockFace.WEST) {
			boolean west = info.isPowered(BlockFace.WEST);
			boolean east = info.isPowered(BlockFace.EAST);
			if (west && !east) {
				this.instruction = BlockFace.WEST;
			} else if (east && !west) {
				this.instruction = BlockFace.EAST;
			} else if (info.isPowered()) {
				this.instruction = BlockFace.SELF;
			} else {
				this.instruction = null;
			}
		} else if (dir == BlockFace.SOUTH) {
			boolean north = info.isPowered(BlockFace.NORTH);
			boolean south = info.isPowered(BlockFace.SOUTH);
			if (north && !south) {
				this.instruction = BlockFace.NORTH;
			} else if (south && !north) {
				this.instruction = BlockFace.SOUTH;
			} else if (info.isPowered()) {
				this.instruction = BlockFace.SELF;
			} else {
				this.instruction = null;
			}
		} else {
			this.length = 0.0;
			this.instruction = null;
			this.valid = false;
			return;
		}

		//Get initial station length, delay and direction
		double length = ParseUtil.parseDouble(info.getLine(1).substring(7), 0.0);
		if (length == 0.0 && this.instruction != null) {
			//manually calculate the length
			//use the amount of straight blocks
			BlockFace[] toCheck;
			if (this.instruction == BlockFace.SELF) {
				toCheck = FaceUtil.getFaces(dir);
			} else {
				toCheck = new BlockFace[] {this.instruction};
			}

			for (BlockFace face : toCheck) {
				int tlength = 0;
				//get the type of rail required
				BlockFace checkface = face;
				if (checkface == BlockFace.NORTH)
					checkface = BlockFace.SOUTH;
				if (checkface == BlockFace.EAST)
					checkface = BlockFace.WEST;

				Block b = info.getRails();
				int maxlength = 20;
				while (true) {
					//Next until invalid
					b = b.getRelative(face);
					Rails rr = BlockUtil.getRails(b);
					if (rr == null || rr.getDirection() != checkface)
						break;
					tlength++;

					//prevent inf. loop or long processing
					maxlength--;
					if (maxlength <= 0) break;
				}
				//Update the length
				if (length == 0 || tlength < length) length = tlength;
				if (length == 0) {
					length++;
				}
			}
		}
		this.length = length;
		this.valid = true;
	}

	/**
	 * Gets the length of the station
	 * 
	 * @return station length
	 */
	public double getLength() {
		return this.length;
	}

	/**
	 * Gets whether this station has a delay set
	 * 
	 * @return True if a delay is set, False if not
	 */
	public boolean hasDelay() {
		return this.delay > 0;
	}

	/**
	 * Gets the delay between action and launch (in milliseconds)
	 * 
	 * @return action delay
	 */
	public long getDelay() {
		return this.delay;
	}

	/**
	 * Checks if this Station is valid for use
	 * 
	 * @return True if valid, False if not
	 */
	public boolean isValid() {
		return this.valid;
	}

	/**
	 * Gets the instruction this station has right now<br>
	 * - This is SELF when it has to center the train<br>
	 * - This is the direction to launch to if it has to launch<br>
	 * - This is null if the station should do nothing and release the train
	 * 
	 * @return
	 */
	public BlockFace getInstruction() {
		return this.instruction;
	}

	/**
	 * Gets the direction to launch to after waiting
	 * 
	 * @return post wait launch direction
	 */
	public Direction getNextDirection() {
		return this.nextDirection;
	}

	/**
	 * Gets the minecart that has to be centred above the sign
	 * 
	 * @return center minecart
	 */
	public MinecartMember getCenterCart() {
		return this.centerCart;
	}
}
