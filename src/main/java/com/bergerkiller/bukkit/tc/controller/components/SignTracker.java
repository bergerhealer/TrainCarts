package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.rails.util.RailSignCache.TrackedSign;

import org.bukkit.block.Block;

import java.util.*;

/**
 * Keeps track of the active signs and detector regions from rail information
 */
public abstract class SignTracker {
    protected static final Set<TrackedSign> blockBuffer = new HashSet<TrackedSign>();
    protected final Map<Block, TrackedSign> activeSigns = new LinkedHashMap<Block, TrackedSign>();
    protected final List<DetectorRegion> detectorRegions = new ArrayList<>(0);
    protected final ToggledState needsUpdate = new ToggledState();

    public Collection<TrackedSign> getActiveTrackedSigns() {
        return Collections.unmodifiableCollection(activeSigns.values());
    }

    public Collection<Block> getActiveSigns() {
        return Collections.unmodifiableSet(activeSigns.keySet());
    }

    public Collection<DetectorRegion> getActiveDetectorRegions() {
        return this.detectorRegions;
    }

    public boolean containsSign(Block signblock) {
        return signblock != null && activeSigns.containsKey(signblock);
    }

    public boolean hasSigns() {
        return !this.activeSigns.isEmpty();
    }

    /**
     * Clears all active signs and other Block info, resulting in leave events being fired
     */
    public void clear() {
        if (!activeSigns.isEmpty()) {
            for (TrackedSign signBlock : activeSigns.values()) {
                onSignChange(signBlock, false);
            }
            activeSigns.clear();
        }
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
        TrackedSign sign = activeSigns.remove(signBlock);
        if (sign != null) {
            onSignChange(sign, false);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the Minecart Member or Group is traveling on top of a given rails block<br>
     * <br>
     * <b>Deprecated:</b> use {@link MinecartMember#getRailTracker()} or
     * {@link MinecartGroup#getRailTracker()} for this instead.
     *
     * @param railsBlock to check
     * @return True if part of the rails, False if not
     */
    @Deprecated
    public abstract boolean isOnRails(Block railsBlock);

    protected abstract void onSignChange(TrackedSign signblock, boolean active);

    protected void updateActiveSigns(Collection<TrackedSign> newActiveSigns) {
        if (newActiveSigns.isEmpty()) {
            // Only remove old signs
            if (!activeSigns.isEmpty()) {
                for (TrackedSign oldActiveSign : activeSigns.values()) {
                    onSignChange(oldActiveSign, false);
                }
                activeSigns.clear();
            }
        } else {
            final boolean hadSigns = !activeSigns.isEmpty();

            // Add all the new signs
            for (TrackedSign newActiveSign : newActiveSigns) {
                if (activeSigns.put(newActiveSign.signBlock, newActiveSign) == null) {
                    onSignChange(newActiveSign, true);
                }
            }
            if (hadSigns) {
                // Calculate all the signs that are now missing
                blockBuffer.clear();
                blockBuffer.addAll(activeSigns.values());
                blockBuffer.removeAll(newActiveSigns);

                // Remove all the signs that are now inactive
                for (TrackedSign old : blockBuffer) {
                    activeSigns.remove(old.signBlock);
                    onSignChange(old, false);
                }
            }
        }
    }

}
