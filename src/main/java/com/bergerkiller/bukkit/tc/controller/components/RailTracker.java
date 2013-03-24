package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.RailType;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.railphysics.RailLogic;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicGround;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicVertical;

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
			return RailLogic.get(this.owner);
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
		this.lastRailType = this.railType;
		this.lastRailLogic = this.railLogic;
		this.railLogic = RailLogic.get(this.owner);
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

		// Obtain the current, live block information
		final CommonMinecart<?> entity = owner.getEntity();
		final World world = entity.getWorld();
		this.blockPos = entity.loc.block();

		// Gather rail information
		IntVector3 below = this.blockPos.subtract(0, 1, 0);
		owner.vertToSlope = false;

		// Find the rail - first step
		int railtype = WorldUtil.getBlockTypeId(world, below);
		if (MaterialUtil.ISRAILS.get(railtype) || MaterialUtil.ISPRESSUREPLATE.get(railtype)) {
			this.blockPos = below;
		} else if (Util.ISVERTRAIL.get(railtype) && this.lastRailType != RailType.VERTICAL) {
			this.blockPos = below;
		} else {
			railtype = WorldUtil.getBlockTypeId(world, this.blockPos);
		}
		this.updateBlock(railtype);

		// Slope UP -> Vertical
		if (this.railType == RailType.VERTICAL && this.lastRailLogic.isSloped()) {
			if (this.lastRailLogic.getDirection() == owner.getDirection().getOppositeFace()) {
				entity.loc.setY((double) blockPos.y + 0.95);
			}
		}

		// Vertical -> Slope UP
		if (this.railType == RailType.NONE && owner.getEntity().vel.getY() > 0) {
			final IntVector3 nextPos = blockPos.add(this.lastRailLogic.getDirection());
			Block next = nextPos.toBlock(world);
			Rails rails = BlockUtil.getRails(next);
			if (rails != null && rails.isOnSlope()) {
				if (rails.getDirection() == this.lastRailLogic.getDirection()) {
					// Move the minecart to the slope
					this.blockPos = nextPos;
					this.updateBlock();
					entity.loc.xz.set(this.blockPos.x + 0.5, this.blockPos.z + 0.5);
					entity.loc.xz.subtract(this.lastRailLogic.getDirection(), 0.49);
					// Y offset
					final double transOffset = 0.01; // How high above the slope to teleport to
					entity.loc.setY(this.blockPos.y + transOffset);
				}
			}
		}
	}

	private void updateBlock() {
		updateBlock(WorldUtil.getBlockTypeId(owner.getEntity().getWorld(), this.blockPos));
	}

	private void updateBlock(int railtype) {
		final World world = owner.getEntity().getWorld();
		if (this.hasBlockChanged()) {
			this.block = this.blockPos.toBlock(world);
		}
		int raildata = WorldUtil.getBlockData(world, this.blockPos);
		this.railType = RailType.get(railtype, raildata);
	}
}
