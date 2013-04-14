package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;

/**
 * Keeps track of the active rails, signs and detector regions
 */
public abstract class BlockTracker {
	protected static final Set<Block> blockBuffer = new HashSet<Block>();
	protected final Set<Block> activeSigns = new LinkedHashSet<Block>();
	protected final List<DetectorRegion> detectorRegions = new ArrayList<DetectorRegion>(0);
	protected final ToggledState needsUpdate = new ToggledState();

	public Collection<Block> getActiveSigns() {
		return Collections.unmodifiableSet(activeSigns);
	}

	public Collection<DetectorRegion> getActiveDetectorRegions() {
		return this.detectorRegions;
	}

	public boolean containsSign(Block signblock) {
		return signblock != null && activeSigns.contains(signblock);
	}

	public boolean hasSigns() {
		return !this.activeSigns.isEmpty();
	}

	/**
	 * Clears all active signs and other Block info, resulting in leave events being fired
	 */
	public void clear() {
		if (!activeSigns.isEmpty()) {
			for (Block signBlock : activeSigns) {
				onSignChange(signBlock, false);
			}
			activeSigns.clear();
		}
	}

	/**
	 * Tells detector regions (and signs?) that the tracker owner has unloaded
	 */
	public void unload() {
	}

	/**
	 * Tells all the Minecarts part of this Minecart Member or Group that something changed
	 */
	public void update() {
		needsUpdate.set();
	}

	/**
	 * Removes an active sign
	 * 
	 * @param signBlock to remove
	 * @return True if the Block was removed, False if not
	 */
	public boolean removeSign(Block signBlock) {
		if (activeSigns.remove(signBlock)) {
			onSignChange(signBlock, false);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Checks whether the Minecart Member or Group is traveling on top of a given rails block
	 * 
	 * @param railsBlock to check
	 * @return True if part of the rails, False if not
	 */
	public abstract boolean isOnRails(Block railsBlock);

	protected abstract void onSignChange(Block signblock, boolean active);

	protected void updateActiveSigns(Collection<Block> newActiveSigns) {
		if (newActiveSigns.isEmpty()) {
			// Only remove old signs
			if (!activeSigns.isEmpty()) {
				for (Block oldActiveSign : activeSigns) {
					onSignChange(oldActiveSign, false);
				}
				activeSigns.clear();
			}
		} else {
			final boolean hadSigns = !activeSigns.isEmpty();

			// Add all the new signs
			for (Block newActiveSign : newActiveSigns) {
				if (activeSigns.add(newActiveSign)) {
					onSignChange(newActiveSign, true);
				}
			}
			if (hadSigns) {
				// Calculate all the signs that are now missing
				blockBuffer.clear();
				blockBuffer.addAll(activeSigns);
				blockBuffer.removeAll(newActiveSigns);

				// Remove all the signs that are now inactive
				activeSigns.removeAll(blockBuffer);
				for (Block oldActiveSign : blockBuffer) {
					onSignChange(oldActiveSign, false);
				}
			}
		}
	}
}
