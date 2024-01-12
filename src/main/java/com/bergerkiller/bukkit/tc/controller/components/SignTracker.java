package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList;

import org.bukkit.block.Block;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Keeps track of the active signs and detector regions from rail information
 */
public abstract class SignTracker {
    private static final ArrayList<ActiveSign> tmpSignBuffer = new ArrayList<>();
    private Set<Object> offlineLoadedSkippedSignKeys = Collections.emptySet();
    private Set<Object> offlineLoadedActiveSignKeys = Collections.emptySet();
    private final Map<Object, ActiveSign> activeSignsByKey = new LinkedHashMap<Object, ActiveSign>();
    private final ImplicitlySharedList<ActiveSign> activeSigns = new ImplicitlySharedList<>();
    protected ImplicitlySharedList<DetectorRegion> detectorRegions = new ImplicitlySharedList<>();
    protected final ToggledState needsUpdate = new ToggledState();
    protected final SignSkipTracker signSkipTracker;

    protected SignTracker(IPropertiesHolder owner) {
        this.signSkipTracker = new SignSkipTracker(owner);
    }

    /**
     * Gets the Owner of this sign tracker. This owner must be part of TrainCarts.
     *
     * @return owner
     */
    public abstract TrainCarts.Provider getOwner();

    /**
     * Gets the tracker responsible for tracking what signs have been skipped
     *
     * @return Sign skip tracker
     */
    public SignSkipTracker getSignSkipTracker() {
        return signSkipTracker;
    }

    /**
     * Gets all actively tracked signs. Can use implicit clone/copy iteration functions to
     * safely iterate the signs without causing concurrent modification exceptions.
     *
     * @return list of signs
     */
    public ImplicitlySharedList<ActiveSign> getActiveTrackedSigns() {
        return activeSigns;
    }

    public Collection<DetectorRegion> getActiveDetectorRegions() {
        return this.detectorRegions;
    }

    public void addOfflineSkippedSignKey(Object signUniqueKey) {
        if (offlineLoadedSkippedSignKeys.isEmpty()) {
            offlineLoadedSkippedSignKeys = new HashSet<>();
        }
        offlineLoadedSkippedSignKeys.add(signUniqueKey);
    }

    protected void addOfflineActiveSignKey(Object signUniqueKey) {
        if (offlineLoadedActiveSignKeys.isEmpty()) {
            offlineLoadedActiveSignKeys = new HashSet<>();
        }
        offlineLoadedActiveSignKeys.add(signUniqueKey);
    }

    protected void clearOfflineActiveSignKeys() {
        offlineLoadedActiveSignKeys = Collections.emptySet();
        offlineLoadedSkippedSignKeys = Collections.emptySet();
    }

    protected void signSkipTrackerFilterSigns(List<ActiveSign> signs) {
        if (!signs.isEmpty()) {
            // If there's signs the train already had activated, update those in the skip tracker
            // first. This avoids a skip rule repeating after a reload
            if (!offlineLoadedActiveSignKeys.isEmpty()) {
                signSkipTracker.loadSigns(signs.stream()
                        .filter(s -> offlineLoadedActiveSignKeys.contains(s.getUniqueKey()))
                        .collect(Collectors.toList()));
            } else if (!offlineLoadedSkippedSignKeys.isEmpty()) {
                // Must initialize so that loaded = true, otherwise things reset later
                signSkipTracker.loadSigns(Collections.emptyList());
            }

            // Sign set as skipped, must be set skipped
            if (!offlineLoadedSkippedSignKeys.isEmpty()) {
                for (ActiveSign sign : signs) {
                    if (offlineLoadedSkippedSignKeys.contains(sign.getUniqueKey())) {
                        signSkipTracker.setSkipped(sign);
                    }
                }
            }
        }

        // Now actually filter the signs list
        signSkipTracker.filterSigns(signs);
    }

    public boolean isSkipped(TrackedSign sign) {
        return signSkipTracker.isSkipped(sign);
    }

    public boolean containsSign(TrackedSign sign) {
        if (sign != null) {
            ActiveSign existing = activeSignsByKey.get(sign.getUniqueKey());
            if (existing == null) {
                return false;
            }
            if (sign == existing.sign) {
                return true;
            }
            if (sign.isRealSign() && existing.sign.isRealSign()) {
                return sign.signBlock.equals(existing.sign.signBlock);
            }
        }
        return false;
    }

    /**
     * Removes an active sign
     *
     * @param sign TrackedSign to remove
     * @return True if the Sign was removed, False if not
     */
    public boolean removeSign(TrackedSign sign) {
        if (sign == null) {
            return false;
        }

        ActiveSign removed = activeSignsByKey.remove(sign.getUniqueKey());
        if (removed != null) {
            activeSigns.remove(removed);
            onSignChange(removed, false);
            return true;
        } else {
            return false;
        }
    }

    public boolean hasSigns() {
        return !this.activeSigns.isEmpty();
    }

    /**
     * Clears all active signs and other Block info, resulting in leave events being fired
     */
    public void clear() {
        if (!activeSignsByKey.isEmpty()) {
            int maxResetIterCtr = 100; // happens more than this, infinite loop suspected
            int expectedCount = activeSignsByKey.size();
            Iterator<ActiveSign> iter = activeSignsByKey.values().iterator();
            while (iter.hasNext()) {
                ActiveSign sign = iter.next();
                iter.remove();
                activeSigns.remove(sign);
                expectedCount--;
                onSignChange(sign, false);

                if (expectedCount != activeSignsByKey.size()) {
                    expectedCount = activeSignsByKey.size();
                    iter = activeSignsByKey.values().iterator();
                    if (--maxResetIterCtr <= 0) {
                        getOwner().getTrainCarts().log(Level.WARNING, "[SignTracker] Number of iteration reset attempts exceeded limit");
                        break;
                    }
                }
            }

            // Just to be sure
            activeSigns.clear();
            activeSignsByKey.clear();
        }
    }

    /**
     * Tells all the Minecarts part of this Minecart Member or Group that something changed
     */
    public void update() {
        needsUpdate.set();
    }

    /**
     * Checks whether the Minecart Member or Group is traveling on top of a given rails block
     *
     * @param railsBlock to check
     * @return True if part of the rails, False if not
     * @deprecated Use {@link MinecartMember#getRailTracker()} or
     *             {@link MinecartGroup#getRailTracker()} for this instead.
     */
    @Deprecated
    public abstract boolean isOnRails(Block railsBlock);

    protected abstract void onSignChange(ActiveSign sign, boolean active);

    protected void updateActiveSigns(Supplier<ModificationTrackedList<ActiveSign>> activeSignListSupplier) {
        int limit = 1000;
        while (!tryUpdateActiveSigns(activeSignListSupplier.get())) {
            // Check for infinite loops, just in case, you know?
            if (--limit == 0) {
                getOwner().getTrainCarts().getLogger().log(Level.SEVERE, "Reached limit of loops updating active signs");
                break;
            }
        }
    }

    // Tries to update the active sign list, returns false if the list was modified during it
    private boolean tryUpdateActiveSigns(final ModificationTrackedList<ActiveSign> list) {
        // Retrieve the list and modification counter
        final int mod_start = list.getModCount();
        final boolean hadSigns = !activeSigns.isEmpty();

        // Perform all operations, for those that could leak into executing code
        // that could modify it, track the mod counter. If changed, restart from
        // the beginning.

        // When there are no signs, only remove previously detected signs
        if (list.isEmpty()) {
            if (hadSigns) {
                Iterator<ActiveSign> iter = activeSignsByKey.values().iterator();
                while (iter.hasNext()) {
                    ActiveSign sign = iter.next();
                    activeSigns.remove(sign);
                    iter.remove();
                    onSignChange(sign, false);

                    // If list changed, restart from the beginning
                    if (list.getModCount() != mod_start) {
                        return false;
                    }
                }
            }

            // All good!
            return true;
        }

        // Mark all current signs as not detected
        activeSigns.forEach(a -> a.detected = false);

        // Go by all detected signs and try to add it to the map
        // If this succeeds, fire an 'enter' event
        // This enter event might modify the list, if so, restart from the beginning
        for (ActiveSign newActiveSign : list) {
            // Try to find an existing sign entry or compute a new one
            // Make sure that when adding a new one, we clone the active sign
            // The active sign might be added to more than one member, and re-using it
            // could seriously break things.
            ActiveSign currActiveSign = activeSignsByKey.computeIfAbsent(newActiveSign.getUniqueKey(),
                    u -> new ActiveSign(newActiveSign.sign, null));
            currActiveSign.detected = true;

            // If a new sign was added, update the list of tracked signs
            if (currActiveSign.enterState == null) {
                currActiveSign.enterState = newActiveSign.enterState;
                activeSigns.add(currActiveSign);

                // Fire enter for new sign (if not reloaded)
                if (!offlineLoadedActiveSignKeys.contains(currActiveSign.getUniqueKey())) {
                    onSignChange(currActiveSign, true);
                }
            } else if (currActiveSign.sign != newActiveSign.sign) {
                // If old and new signs have identical text, don't fire any events
                if (currActiveSign.sign.hasIdenticalText(newActiveSign.sign)) {
                    // Silent update
                    currActiveSign.setSign(newActiveSign.sign);
                    continue;
                }

                // Ask SignAction (if available) whether we should trigger a change here
                SignAction action = currActiveSign.sign.getAction();
                boolean fireEvents = true;
                if (action != null && newActiveSign.sign.getAction() == action) {
                    SignActionEvent event = newActiveSign.sign.createEvent(SignActionType.NONE);
                    fireEvents = action.signTextChanged(event);
                }

                // Fire events of removing the old sign
                if (fireEvents) {
                    onSignChange(currActiveSign, false);
                }

                // Update sign
                currActiveSign.setSign(newActiveSign.sign);

                // Fire enter event (again)
                if (fireEvents) {
                    onSignChange(currActiveSign, true);
                }
            }

            // If list changed, restart from the beginning
            if (list.getModCount() != mod_start) {
                return false;
            }
        }

        // Check if any previously detected signs are no longer in the active sign list
        if (hadSigns) {
            forEachActiveSignSafe(currActiveSign -> {
                if (!currActiveSign.detected) {
                    ActiveSign removed = activeSignsByKey.remove(currActiveSign.getUniqueKey());
                    if (removed != null) {
                        activeSigns.remove(removed);
                    }
                    if (removed == currActiveSign) {
                        onSignChange(currActiveSign, false);
                    }
                }
            });

            // If list changed, restart from the beginning
            if (list.getModCount() != mod_start) {
                return false;
            }
        }

        // Done!
        return true;
    }

    private void forEachActiveSignSafe(Consumer<ActiveSign> action) {
        List<ActiveSign> buffer = tmpSignBuffer;
        if (buffer.isEmpty()) {
            // Can use the buffer
            buffer.addAll(activeSigns);
            try {
                buffer.forEach(action);
            } finally {
                buffer.clear();
            }
        } else {
            // Use clone copy
            try (ImplicitlySharedList<ActiveSign> copy = activeSigns.clone()) {
                copy.forEach(action);
            }
        }
    }

    /*
     * Below methods should not be used, because they only work for real sign blocks.
     * Fake signs added by add-ons are not supported at all, and are ignored.
     */

    /**
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public Collection<Block> getActiveSigns() {
        return getActiveTrackedSigns().stream()
                .map(s -> s.sign)
                .filter(TrackedSign::isRealSign)
                .map(s -> s.signBlock)
                .collect(StreamUtil.toUnmodifiableList());
    }

    /**
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public boolean containsSign(Block signblock) {
        ActiveSign sign = activeSignsByKey.get(signblock);
        return sign != null && sign.sign.isRealSign();
    }

    /**
     * Removes an active sign
     *
     * @param signBlock to remove
     * @return True if the Block was removed, False if not
     * @deprecated Only works with real sign blocks
     */
    @Deprecated
    public boolean removeSign(Block signBlock) {
        ActiveSign removed = activeSignsByKey.remove(signBlock);
        if (removed != null && removed.sign.isRealSign()) {
            activeSigns.remove(removed);
            onSignChange(removed, false);
            return true;
        } else {
            activeSignsByKey.put(signBlock, removed);
            return false;
        }
    }

    /**
     * A sign activated by a train. Tracks the sign itself, and the state
     * of the member at the time of first activating.
     */
    public static final class ActiveSign {
        private TrackedSign sign;
        private Object uniqueKey;
        private RailState enterState;
        private boolean detected;

        public ActiveSign(TrackedSign sign, RailState enterState) {
            this.sign = sign;
            this.uniqueKey = sign.getUniqueKey();
            this.enterState = enterState;
            this.detected = true;
        }

        /**
         * Gets the current tracked sign instance that is active
         *
         * @return tracked sign
         */
        public TrackedSign getSign() {
            return sign;
        }

        private void setSign(TrackedSign sign) {
            this.sign = sign;
            this.uniqueKey = sign.getUniqueKey();
        }

        /**
         * Gets the unique key of the sign. This is what is registered to
         * uniquely identify this sign.
         *
         * @return Sign unique key
         */
        public Object getUniqueKey() {
            return this.uniqueKey;
        }

        /**
         * Gets the state of the member upon first activating this sign
         *
         * @return enter state
         */
        public RailState getEnterState() {
            return enterState;
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartMember.
         * If the member is unloaded or dead, the event is not fired.
         *
         * @param action Action to execute
         * @param member Member involved in the event
         * @see TrackedSign#createEvent(SignActionType)
         */
        public void executeEventForMember(SignActionType action, MinecartMember<?> member) {
            sign.executeEventForMember(action, member, enterState);
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartGroup.
         * If the group is unloaded, the event is not fired.
         *
         * @param action Action to execute
         * @param group Group involved in the event
         * @see TrackedSign#createEvent(SignActionType)
         */
        public void executeEventForGroup(SignActionType action, MinecartGroup group) {
            sign.executeEventForGroup(action, group, enterState);
        }
    }
}
