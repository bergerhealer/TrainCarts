package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicGround;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

/**
 * Stores rail information of a Minecart Member
 */
public class RailTracker {
	private final MinecartMember<?> owner;
	public IntVector3 blockPos = new IntVector3(0, 0, 0);
	private RailType lastRailType, railType;
	private Block lastBlock, block;
	private RailLogic lastRailLogic, railLogic;
	private boolean railLogicSnapshotted = false;

	public RailTracker(MinecartMember<?> owner) {
		this.owner = owner;
		this.lastRailType = this.railType = RailType.NONE;
		this.lastRailLogic = this.railLogic = RailLogicGround.INSTANCE;
	}

	/**
	 * Refreshes the basic information with the information from the owner
	 */
	public void onAttached() {
		this.blockPos = owner.getEntity().loc.block();
		this.lastBlock = this.block = this.blockPos.toBlock(owner.getEntity().getWorld());
		this.refreshBlock();
		this.lastBlock = this.block;
		this.lastRailType = this.railType;
		this.lastRailLogic = this.railLogic;
	}

	/**
	 * Obtains a new track iterator iterating the tracks from this point towards the direction
	 * the Minecart is moving.
	 * 
	 * @return forward track iterator
	 */
	public TrackIterator getTrackIterator() {
		return new TrackIterator(this.block, this.owner.getDirectionTo());
	}

	/**
	 * Gets the rail type of the current tick
	 * 
	 * @return current rail type
	 */
	public RailType getRailType() {
		return railType;
	}

	/**
	 * Gets the rail type from the previous tick
	 * 
	 * @return previous rail type
	 */
	public RailType getLastRailType() {
		return lastRailType;
	}

	/**
	 * Gets the block of the current tick
	 * 
	 * @return current block
	 */
	public Block getBlock() {
		return block;
	}

	/**
	 * Gets the block from the previous tick
	 * 
	 * @return previous block
	 */
	public Block getLastBlock() {
		return lastBlock;
	}

	/**
	 * Gets the rail logic of the current tick
	 * 
	 * @return current rail logic
	 */
	public RailLogic getRailLogic() {
		if (railLogicSnapshotted) {
			return this.railLogic;
		} else {
			return this.railType.getLogic(this.owner, this.block);
		}
	}

	/**
	 * Gets the rail logic from the previous tick
	 * 
	 * @return previous rail logic
	 */
	public RailLogic getLastLogic() {
		return lastRailLogic;
	}

	/**
	 * Checks whether the current rails block has changed
	 * 
	 * @return True if the block changed, False if not
	 */
	public boolean hasBlockChanged() {
		return blockPos.x != lastBlock.getX() || blockPos.y != lastBlock.getY() || blockPos.z != lastBlock.getZ();
	}

	/**
	 * Stops using the Rail Logic snapshot for the next run
	 */
	public void setLiveRailLogic() {
		this.railLogicSnapshotted = false;
	}

	/**
	 * Creates a snapshot of the Rail Logic for the entire next run
	 */
	public void snapshotRailLogic() {
		this.railLogic = this.railType.getLogic(this.owner, this.block);
		if (this.railLogic instanceof RailLogicVertical) {
			this.railType = RailType.VERTICAL;
		}
		this.railLogicSnapshotted = true;
	}

	/**
	 * Refreshes the block using Minecart position information
	 */
	public void refreshBlock() {
		// Store the last rail information
		this.lastBlock = this.block;
		this.lastRailType = this.railType;
		this.lastRailLogic = this.railLogic;

		// Obtain the current, live block information
		final CommonMinecart<?> entity = owner.getEntity();
		final World world = entity.getWorld();
		this.blockPos = entity.loc.block();

		// Gather rail information
		owner.vertToSlope = false;

		// Find the rail - first step
		this.railType = RailType.NONE;
		for (RailType type : RailType.values()) {
			IntVector3 pos = type.findRail(owner, world, this.blockPos);
			if (pos != null) {
				this.railType = type;
				this.blockPos = pos;
				break;
			}
		}
		if (this.hasBlockChanged()) {
			this.block = this.blockPos.toBlock(world);
		}
	}
}
