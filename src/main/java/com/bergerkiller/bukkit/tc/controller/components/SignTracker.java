package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList;

import org.bukkit.block.Block;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Keeps track of the active signs and detector regions from rail information
 */
public abstract class SignTracker {
    protected static final Set<TrackedSign> blockBuffer = new HashSet<TrackedSign>();
    protected final Map<Block, TrackedSign> activeSigns = new LinkedHashMap<Block, TrackedSign>();
    protected final List<DetectorRegion> detectorRegions = new ArrayList<>(0);
    protected final ToggledState needsUpdate = new ToggledState();
    protected final SignSkipTracker signSkipTracker;

    protected SignTracker(IPropertiesHolder owner) {
        this.signSkipTracker = new SignSkipTracker(owner);
    }

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
            int maxResetIterCtr = 100; // happens more than this, infinite loop suspected
            int expectedCount = activeSigns.size();
            Iterator<TrackedSign> iter = activeSigns.values().iterator();
            while (iter.hasNext()) {
                TrackedSign sign = iter.next();
                iter.remove();
                expectedCount--;
                onSignChange(sign, false);

                if (expectedCount != activeSigns.size()) {
                    expectedCount = activeSigns.size();
                    iter = activeSigns.values().iterator();
                    if (--maxResetIterCtr <= 0) {
                        TrainCarts.plugin.log(Level.WARNING, "Number of iteration reset attempts exceeded limit");
                        break;
                    }
                }
            }
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

    protected void updateActiveSigns(Supplier<ModificationTrackedList<TrackedSign>> activeSignListSupplier) {
        int limit = 1000;
        while (!tryUpdateActiveSigns(activeSignListSupplier.get())) {
            // Check for infinite loops, just in case, you know?
            if (--limit == 0) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE, "Reached limit of loops updating active signs");
                break;
            }
        }
    }

    // Tries to update the active sign list, returns false if the list was modified during it
    private boolean tryUpdateActiveSigns(final ModificationTrackedList<TrackedSign> list) {
        // Retrieve the list and modification counter
        final int mod_start = list.getModCount();
        final boolean hadSigns = !activeSigns.isEmpty();

        // Perform all operations, for those that could leak into executing code
        // that could modify it, track the mod counter. If changed, restart from
        // the beginning.

        // When there are no signs, only remove previously detected signs
        if (list.isEmpty()) {
            if (hadSigns) {
                Iterator<TrackedSign> iter = activeSigns.values().iterator();
                while (iter.hasNext()) {
                    onSignChange(iter.next(), false);
                    iter.remove();

                    // If list changed, restart from the beginning
                    if (list.getModCount() != mod_start) {
                        return false;
                    }
                }
            }

            // All good!
            return true;
        }

        // Go by all detected signs and try to add it to the map
        // If this succeeds, fire an 'enter' event
        // This enter event might modify the list, if so, restart from the beginning
        for (TrackedSign newActiveSign : list) {
            if (activeSigns.put(newActiveSign.signBlock, newActiveSign) == null) {
                onSignChange(newActiveSign, true);

                // If list changed, restart from the beginning
                if (list.getModCount() != mod_start) {
                    return false;
                }
            }
        }

        // Check if any previously detected signs are no longer in the active sign list
        if (hadSigns) {
            // Calculate all the signs that are now missing
            blockBuffer.clear();
            blockBuffer.addAll(activeSigns.values());
            blockBuffer.removeAll(list);

            // Remove all the signs that are now inactive
            // This leave event might cause the list to change, if so, restart from the beginning
            for (TrackedSign old : blockBuffer) {
                activeSigns.remove(old.signBlock);
                onSignChange(old, false);

                // If list changed, restart from the beginning
                if (list.getModCount() != mod_start) {
                    return false;
                }
            }
        }

        // Done!
        return true;
    }
}
