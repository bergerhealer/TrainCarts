package com.bergerkiller.bukkit.tc.storage;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import net.minecraft.server.ChunkCoordinates;

public abstract class OfflineSign {

	public OfflineSign(ChunkCoordinates location) {
		this.location = location;
	}

	private final ChunkCoordinates location;
	private boolean isRemoved = false;

	public ChunkCoordinates getLocation() {
		return this.location;
	}

	public boolean isLoaded(World world) {
		return world != null && world.isChunkLoaded(this.location.x >> 4, this.location.z >> 4);
	}

	public boolean isRemoved() {
		return this.isRemoved;
	}

	public void loadChunks(World world) {
		WorldUtil.loadChunks(world, this.location.x >> 4, this.location.z >> 4, 3);
	}

	public void remove(Block signBlock) {
		if (!this.isRemoved) {
			this.isRemoved = true;
			this.onRemove(signBlock);
		}
	}

	/**
	 * Gets the sign event from this offline Sign
	 * @param world the sign is in
	 * @return the sign, or null if the sign isn't loaded or missing
	 */
	public SignActionEvent getSignEvent(World world) {
		if (this.isLoaded(world)) {
			Block signblock = BlockUtil.getBlock(world, this.location);
			if (MaterialUtil.ISSIGN.get(signblock)) {
				SignActionEvent event = new SignActionEvent(signblock);
				if (this.validate(event)) {
					return event;
				}
			}
			this.remove(signblock);
			return null;
		}
		return null;
	}

	/**
	 * Validates a sign, if returned False, onRemove is called
	 * @param event around the sign
	 * @return True if it is allowed, False if not
	 */
	public abstract boolean validate(SignActionEvent event);

	/**
	 * Called when this sign is no longer present in the world and has to be removed
	 * @param signBlock of the sign that is missing
	 */
	public abstract void onRemove(Block signBlock);
}
