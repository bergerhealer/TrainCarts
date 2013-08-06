package com.bergerkiller.bukkit.tc;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.actions.BlockActionSetLevers;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

/**
 * Represents the Station sign information
 */
public class Station {
	private final SignActionEvent info;
	private final double length;
	private final long delay;
	private final BlockFace instruction;
	private final Direction nextDirection;
	private final MinecartMember<?> centerCart;
	private final boolean valid;
	private final BlockFace railDirection;
	private final Block railsBlock;

	public Station(SignActionEvent info) {
		this.info = info;
		this.delay = ParseUtil.parseTime(info.getLine(2));
		this.nextDirection = Direction.parse(info.getLine(3));
		this.centerCart = info.isCartSign() ? info.getMember() : info.getGroup().middle();
		this.railsBlock = info.getRails();

		// Vertical or horizontal rail logic
		this.railDirection = info.getRailDirection();
		if (FaceUtil.isVertical(this.railDirection)) {
			// Up, down or center based on redstone power
			boolean up = info.isPowered(BlockFace.UP);
			boolean down = info.isPowered(BlockFace.DOWN);
			if (up && !down) {
				this.instruction = BlockFace.UP;
			} else if (!up && down) {
				this.instruction = BlockFace.DOWN;
			} else if (info.isPowered()) {
				this.instruction = BlockFace.SELF;
			} else {
				this.instruction = null;
			}
		} else {
			if (FaceUtil.isSubCardinal(this.railDirection) && FaceUtil.isSubCardinal(info.getFacing())) {				
				// Sub-cardinal checks: Both directions have two possible powered sides
				final BlockFace[] faces = FaceUtil.getFaces(this.railDirection);
				boolean pow1 = info.isPowered(faces[0]) || info.isPowered(faces[1].getOppositeFace());
				boolean pow2 = info.isPowered(faces[1]) || info.isPowered(faces[0].getOppositeFace());
				if (pow1 && !pow2) {
					this.instruction = FaceUtil.combine(faces[0], faces[1].getOppositeFace());
				} else if (!pow1 && pow2) {
					this.instruction = FaceUtil.combine(faces[0].getOppositeFace(), faces[1]);
				} else if (info.isPowered()) {
					this.instruction = BlockFace.SELF;
				} else {
					this.instruction = null;
				}
			} else {
				// Which directions to move, or brake?
				if (FaceUtil.isAlongX(this.railDirection)) {
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
				} else if (FaceUtil.isAlongZ(this.railDirection)) {
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
			}
		}

		// Get initial station length, delay and direction
		double length = ParseUtil.parseDouble(info.getLine(1).substring(7), 0.0);
		if (length == 0.0 && this.instruction != null) {
			// Manually calculate the length
			// Use the amount of straight blocks
			if (FaceUtil.isVertical(this.railDirection)) {
				length = this.calcVerticalLength();
			} else if (FaceUtil.isSubCardinal(this.railDirection)) {
				length = this.calcDiagonalLength();
			} else {
				length = this.calcHorizontalLength();
			}
			if (length == 0.0) {
				length++;
			}
		}
		this.length = length;
		this.valid = true;
	}

	private double calcDiagonalLength() {
		double length = 0.0;
		// Count the amount of zig-zagging curved tracks
		final BlockFace[] toCheck;
		if (this.instruction == BlockFace.SELF) {
			toCheck = new BlockFace[] {FaceUtil.rotate(this.railDirection, -2), FaceUtil.rotate(this.railDirection, 2)};
		} else {
			toCheck = new BlockFace[] {this.instruction};
		}
		for (BlockFace direction : toCheck) {
			double tlength = 0.0;
			// Find out the starting offset
			final BlockFace[] dirs = FaceUtil.getFaces(direction);
			BlockFace[] railDirs = FaceUtil.getFaces(this.railDirection);
			BlockFace railDirection = this.railDirection;

			Block b = this.railsBlock;
			for (int i = 0; i < 20; i++) {
				// Invert the direction
				railDirection = railDirection.getOppositeFace();
				railDirs[0] = railDirs[0].getOppositeFace();
				railDirs[1] = railDirs[1].getOppositeFace();
				// Obtain the new offset
				final BlockFace offset;
				if (LogicUtil.contains(railDirs[0], dirs)) {
					offset = railDirs[0];
				} else {
					offset = railDirs[1];
				}
				// Check if the new block is the expected curve direction
				b = b.getRelative(offset);
				Rails rr = BlockUtil.getRails(b);
				if (rr == null || rr.getDirection() != railDirection) {
					break;
				}
				tlength += MathUtil.HALFROOTOFTWO;
			}

			// Update the length
			if (tlength > length) {
				length = tlength;
			}
		}
		return length;
	}

	private double calcVerticalLength() {
		double length = 0.0;
		// Count the amount of vertical tracks
		final BlockFace[] toCheck;
		if (this.instruction == BlockFace.SELF) {
			toCheck = new BlockFace[] {BlockFace.DOWN, BlockFace.UP};
		} else {
			toCheck = new BlockFace[] {this.instruction};
		}
		for (BlockFace face : toCheck) {
			int tlength = 0;
			// Get the type of rail required
			Block b = this.railsBlock;
			for (int i = 0; i < 20; i++) {
				// Next until invalid
				b = b.getRelative(face);
				if (!Util.ISVERTRAIL.get(b)) {
					break;
				}
				tlength++;
			}
			// Update the length
			if (tlength > length) {
				length = tlength;
			}
		}
		return length;
	}

	private double calcHorizontalLength() {
		double length = 0.0;
		// Count the amount of horizontal tracks
		final BlockFace[] toCheck;
		if (this.instruction == BlockFace.SELF) {
			toCheck = FaceUtil.getFaces(this.railDirection);
		} else {
			toCheck = new BlockFace[] {this.instruction};
		}
		for (BlockFace face : toCheck) {
			int tlength = 0;
			// Get the type of rail required
			BlockFace checkface = FaceUtil.toRailsDirection(face);

			Block b = this.railsBlock;
			for (int i = 0; i < 20; i++) {
				// Next until invalid
				b = b.getRelative(face);
				Rails rr = BlockUtil.getRails(b);
				if (rr == null || rr.getDirection() != checkface) {
					break;
				}
				tlength++;
			}
			// Update the length
			if (tlength > length) {
				length = tlength;
			}
		}
		return length;
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
	public MinecartMember<?> getCenterCart() {
		return this.centerCart;
	}

	/**
	 * Waits a train for a specific amount of time.
	 * This causes the train to play the station sound, refill the fuel
	 * and toggle the station levers on.
	 * 
	 * @param delay to wait, use 0 for no delay, MAX_VALUE to wait forever.
	 */
	public void waitTrain(long delay) {
		ActionTrackerGroup actions = info.getGroup().getActions();
		if (TrainCarts.playSoundAtStation) {
			actions.addActionSizzle();
		}
		if (TrainCarts.refillAtStations) {
			actions.addActionRefill();
		}
		actions.addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
		if (delay == Long.MAX_VALUE) {
			actions.addActionWaitForever();
		} else if (delay > 0) {
			actions.addActionWait(delay);
		}
	}

	/**
	 * Orders the train to center above this Station
	 */
	public void centerTrain() {
		CartToStationInfo stationInfo = getCartToStationInfo(getCenterCart());
		if (stationInfo.cartDir != null) {
			// Launch the center cart into the direction of the station
			getCenterCart().getActions().addActionLaunch(stationInfo.cartDir, stationInfo.distance, 0.0);
		} else {
			// Alternative: get as close as possible (may fail)
			getCenterCart().getActions().addActionLaunch(info.getCenterLocation(), 0);
		}
	}

	/**
	 * Launches the train so that the middle or front cart is the distance away from this station
	 * 
	 * @param direction to launch into
	 * @param distance to launch
	 */
	public void launchTo(BlockFace direction, double distance) {
		// Apply distance correction from center cart to station
		CartToStationInfo stationInfo = getCartToStationInfo(getCenterCart());
		double newDistance = distance;
		BlockFace newDirection = direction;
		// Adjust the direction and distance
		if (stationInfo.centerDir == direction) {
			// Adjust the direction and distance
			newDistance += stationInfo.distance;
			newDirection = stationInfo.cartDir;
		}
		getCenterCart().getActions().addActionLaunch(newDirection, newDistance, TrainCarts.launchForce);
	}

	private CartToStationInfo getCartToStationInfo(MinecartMember<?> member) {
		CartToStationInfo info = new CartToStationInfo();
		info.cartBlock = member.getBlock();
		BlockFace[] possible = RailType.getType(info.cartBlock).getPossibleDirections(info.cartBlock);
		TrackIterator iter = new TrackIterator(null, null, (int) member.getGroup().length(), true);
		info.distance = Integer.MAX_VALUE;
		for (BlockFace dir : possible) {
			iter.reset(info.cartBlock, dir);
			if (iter.tryFind(this.info.getRails()) && iter.getDistance() < info.distance) {
				info.distance = iter.getDistance();
				info.cartDir = dir;
				info.centerDir = iter.currentDirection();
			}
		}
		// Adjust the distance based on member-block position
		if (info.cartDir != null) {
			// Adjust for the small offset of the cart from the original block
			CommonMinecart<?> entity = member.getEntity();
			double subX = entity.loc.getX() - (entity.loc.x.getFloor() + 0.5);
			double subY = entity.loc.getY() - (entity.loc.y.getFloor() + 0.5);
			double subZ = entity.loc.getZ() - (entity.loc.z.getFloor() + 0.5);
			double sub = info.cartDir.getModX() * subX + info.cartDir.getModY() * subY + info.cartDir.getModZ() * subZ;

			// Launch the center cart into the direction of the station
			info.distance -=  sub + 0.5;
		}
		return info;
	}

	private static class CartToStationInfo {
		public Block cartBlock;
		public BlockFace cartDir;
		public BlockFace centerDir;
		public double distance;
	}
}
