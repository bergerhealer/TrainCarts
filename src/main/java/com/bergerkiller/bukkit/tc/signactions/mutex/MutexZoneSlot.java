package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.signactions.mutex.railslot.MutexRailSlot;
import com.bergerkiller.bukkit.tc.signactions.mutex.railslot.MutexRailSlotMap;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.events.MutexZoneConflictEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.statements.Statement;

/**
 * A slot where a group can be put that was granted access to move through.
 * Named mutex zones can share the same (named) slot
 */
public class MutexZoneSlot {
    private static final int TICK_DELAY_CLEAR_AUTOMATIC = 6; // Tick delay until a group is fully cleared from a mutex (and lever toggles up)
    private final String name;
    private final List<EnteredGroup> entered = new ArrayList<>(2);
    private List<MutexZone> zones;
    private List<String> statements;
    private int tickLastHardEntered = 0;

    protected MutexZoneSlot(String name) {
        this.name = name;
        this.zones = Collections.emptyList();
        this.statements = Collections.emptyList();
    }

    public String getName() {
        return this.name;
    }

    public String getNameWithoutWorldUUID() {
        if (!zones.isEmpty()) {
            String uuid_str = zones.get(0).signBlock.getWorldUUID().toString() + "_";
            if (name.startsWith(uuid_str)) {
                return name.substring(uuid_str.length());
            }
        }
        return name;
    }

    public boolean isAnonymous() {
        return this.name.isEmpty();
    }

    protected MutexZoneSlot addZone(MutexZone zone) {
        if (this.zones.isEmpty()) {
            this.zones = Collections.singletonList(zone);
        } else {
            this.zones = new ArrayList<MutexZone>(this.zones);
            this.zones.add(zone);
        }
        this.refreshStatements();
        return this;
    }

    public void removeZone(MutexZone zone) {
        if (this.zones.size() == 1 && this.zones.get(0) == zone) {
            this.zones = Collections.emptyList();
            this.statements = Collections.emptyList();
        } else if (this.zones.size() > 1) {
            this.zones.remove(zone);
            this.refreshStatements();
        }
    }

    public List<MutexZone> getZones() {
        return this.zones;
    }

    public boolean hasZones() {
        return !this.zones.isEmpty();
    }

    /**
     * Gets a List of all entered groups that exist
     *
     * @return List of entered groups
     */
    public List<EnteredGroup> getEnteredGroups() {
        return entered;
    }

    /**
     * Gets a full list of statement rules for this mutex zone. Each sign
     * can add statements to this list as they join the mutex zone name.
     * 
     * @return statements
     */
    public List<String> getStatements() {
        return this.statements;
    }

    private void refreshStatements() {
        this.statements = this.zones.stream()
                .sorted((z0, z1) -> z0.signBlock.getPosition().compareTo(z1.signBlock.getPosition()))
                .map(z -> z.statement)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Called every tick to refresh mutex zones that have a group inside.
     * If a group leaves a zone, this eventually releases that group again.
     */
    public void onTick() {
        if (!entered.isEmpty()) {
            ListIterator<EnteredGroup> iter = entered.listIterator();
            boolean hasHardEnteredGroup = false;
            boolean trainsHaveLeft = false;
            while (iter.hasNext()) {
                EnteredGroup enteredGroup = iter.next();
                if (!enteredGroup.refresh(this, newGroup -> {
                    // Unloaded entered group loaded in
                    iter.set(newGroup);
                    swapEnteredGroup(enteredGroup, newGroup);
                })) {
                    iter.remove();
                    swapDeactivatedEnteredGroups(enteredGroup, null);
                    trainsHaveLeft = true;
                } else if (enteredGroup.hardEnter) {
                    hasHardEnteredGroup = true;
                }
            }
            if (trainsHaveLeft && !hasHardEnteredGroup) {
                this.setLevers(false);
            }
        }
    }

    /**
     * Unloads all MinecartGroup representations stored in this mutex zone slot
     * that are for the group specified.
     *
     * @param group Group that unloaded
     */
    public void unload(MinecartGroup group) {
        for (ListIterator<EnteredGroup> iter = this.entered.listIterator(); iter.hasNext();) {
            EnteredGroup entered = iter.next();
            if (entered.isGroup(group)) {
                EnteredGroup unloaded = entered.unload();
                if (entered != unloaded) {
                    iter.set(unloaded);
                    swapDeactivatedEnteredGroups(entered, unloaded);
                }
                break;
            }
        }
    }

    /**
     * Looks up the EnteredGroup of a MinecartGroup, if it had
     * (tried to) enter in the recent past.
     *
     * @param group
     * @return entered group
     */
    public LoadedEnteredGroup findEntered(MinecartGroup group) {
        for (EnteredGroup entered : this.entered) {
            if (entered instanceof LoadedEnteredGroup && entered.isGroup(group)) {
                return (LoadedEnteredGroup) entered;
            }
        }
        return null;
    }

    /**
     * Starts tracking the group for this zone. Must call
     * {@link LoadedEnteredGroup#enter(MutexZoneSlotType, IntVector3, boolean)}
     * afterwards, which might result in the group being untracked again when rejected.
     *
     * @param group MinecartGroup to track
     * @param distanceToMutex Distance from the front of the group to this mutex zone
     * @return LoadedEnteredGroup of this group
     */
    public LoadedEnteredGroup track(MinecartGroup group, double distanceToMutex) {
        int nowTicks = group.getObstacleTracker().getTickCounter();

        // Verify using statements whether the group is even considered
        {
            List<String> statements = this.getStatements();
            if (!statements.isEmpty()) {
                SignActionEvent signEvent = null;
                if (this.zones.size() == 1) {
                    Block signBlock = this.zones.get(0).getSignBlock();
                    if (signBlock != null && MaterialUtil.ISSIGN.get(signBlock)) {
                        RailLookup.TrackedSign trackedSign = RailLookup.TrackedSign.forRealSign(
                                signBlock, this.zones.get(0).isSignFrontText(), null);
                        signEvent = new SignActionEvent(trackedSign, group);
                        signEvent.setAction(SignActionType.GROUP_ENTER);
                    }
                }
                if (!Statement.hasMultiple(group, this.getStatements(), signEvent)) {
                    // If previously tracked, release the train
                    boolean wasGroupHardEntered = false;
                    boolean hasHardEnteredGroup = false;
                    for (Iterator<EnteredGroup> iter = this.entered.iterator(); iter.hasNext();) {
                        EnteredGroup enteredGroup = iter.next();
                        if (enteredGroup.isGroup(group)) {
                            iter.remove();
                            swapDeactivatedEnteredGroups(enteredGroup, null);
                            wasGroupHardEntered = enteredGroup.hardEnter;
                        } else if (enteredGroup.hardEnter) {
                            hasHardEnteredGroup = true;
                        }
                    }
                    if (wasGroupHardEntered && !hasHardEnteredGroup) {
                        this.setLevers(false);
                    }

                    // Ignored!
                    return new IgnoredEnteredGroup(this, group, distanceToMutex, nowTicks);
                }
            }
        }

        // Find existing
        for (EnteredGroup enteredGroup : this.entered) {
            if (enteredGroup.isGroup(group)) {
                // Promote a non-loaded entered group to a loaded one
                LoadedEnteredGroup loadedEnteredGroup = enteredGroup.load(this, group);
                if (enteredGroup != loadedEnteredGroup) {
                    swapEnteredGroup(enteredGroup, loadedEnteredGroup);
                }

                // Return the live-entered group. Track its information.
                loadedEnteredGroup.deactivateByOtherGroups();
                loadedEnteredGroup.probeTick = nowTicks;
                if (loadedEnteredGroup.active) {
                    loadedEnteredGroup.distanceToMutex = Math.min(loadedEnteredGroup.distanceToMutex, distanceToMutex);
                } else {
                    loadedEnteredGroup.active = true;
                    loadedEnteredGroup.distanceToMutex = distanceToMutex;
                }
                return loadedEnteredGroup;
            }
        }

        LoadedEnteredGroup enteredGroup = new LoadedEnteredGroup(this, group, distanceToMutex, nowTicks, nowTicks);
        this.entered.add(enteredGroup);
        return enteredGroup;
    }

    private void setLevers(boolean down) {
        for (MutexZone zone : this.zones) {
            zone.setLevers(down);
        }
    }

    /**
     * Gets the group that currently is inside this zone. If not null, it
     * means no other group can enter it.
     *
     * @return current groups that activated this mutex zone
     */
    public List<MinecartGroup> getCurrentGroups() {
        if (this.entered.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<MinecartGroup> result = new ArrayList<>(this.entered.size());
            for (EnteredGroup enteredGroup : this.entered) {
                if (enteredGroup.active && enteredGroup.hardEnter && enteredGroup instanceof LoadedEnteredGroup) {
                    result.add(((LoadedEnteredGroup) enteredGroup).group);
                }
            }
            return result;
        }
    }

    /**
     * Gets the group that is expected to enter this mutex zone very soon
     *
     * @return prospective groups
     */
    public List<MinecartGroup> getProspectiveGroups() {
        if (this.entered.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<MinecartGroup> result = new ArrayList<>(this.entered.size());
            for (EnteredGroup enteredGroup : this.entered) {
                if (enteredGroup.active && enteredGroup instanceof LoadedEnteredGroup) {
                    result.add(((LoadedEnteredGroup) enteredGroup).group);
                }
            }
            return result;
        }
    }

    private void swapEnteredGroup(EnteredGroup toReplace, EnteredGroup replacement) {
        swapEnteredGroup(entered, toReplace, replacement);
        swapDeactivatedEnteredGroups(toReplace, replacement);
    }

    private void swapDeactivatedEnteredGroups(EnteredGroup toReplace, EnteredGroup replacement) {
        for (EnteredGroup group : entered) {
            swapEnteredGroup(group.groupsDeactivatingMe, toReplace, replacement);
            swapEnteredGroup(group.otherGroupsToDeactivate, toReplace, replacement);
        }
    }

    private static void swapEnteredGroup(List<EnteredGroup> groups, EnteredGroup toReplace, EnteredGroup replacement) {
        int index = groups.indexOf(toReplace);
        if (index != -1) {
            if (replacement != null) {
                groups.set(index, replacement);
            } else {
                groups.remove(index);
            }
        }
    }

    /**
     * The result of trying to enter a mutex
     */
    public enum EnterResult {
        /** The group does not match conditions to enter/be seen by the mutex zone */
        IGNORED(false, false),
        /** The mutex zone was entered so far */
        SUCCESS(false, false),
        /** A path in the mutex just changed and both trains are inside at the same time! */
        CONFLICT(false, true),
        /** A previous conflict is still ongoing */
        CONFLICT_ONGOING(false, true),
        /** The mutex zone is occupied and the train cannot enter it */
        OCCUPIED(true, false),
        /**
         * The mutex zone is occupied and the train cannot enter it. But the slot does
         * want to receive all rail blocks that lie within the zone.
         * This is important to resolve the order in which multiple trains gain access.
         */
        OCCUPIED_DISCOVER(true, false);

        private final boolean occupied;
        private final boolean conflict;

        EnterResult(boolean occupied, boolean conflict) {
            this.occupied = occupied;
            this.conflict = conflict;
        }

        /**
         * The result indicates that the rail is occupied, and the train should stop
         * moving.
         *
         * @return True if the result indicates occupied track
         */
        public boolean isOccupied() {
            return occupied;
        }

        /**
         * The result indicates a hard conflict occurred. This happens when two trains
         * are inside a (smart) mutex zone at the same time. This can occur when predicted
         * paths change suddenly (smart mutex) or when a train is placed/spawned inside
         * the mutex zone.
         *
         * @return True if a hard conflict occurred
         */
        public boolean isConflict() {
            return conflict;
        }
    }

    /**
     * Tracks the information of a single train that has entered a mutex zone
     */
    public static abstract class EnteredGroup {
        /** Whether the group has actually entered the mutex zone (true) or is about to (false) */
        public boolean hardEnter = false;
        /** Whether the group is scheduled to go into the mutex zone next, or is already */
        public boolean active = true;
        /** Distance from the front of the train to where this slot was first encountered */
        public double distanceToMutex;
        /** The rail coordinates locked by the group that have positions within the mutex */
        protected final MutexRailSlotMap occupiedRails;
        /**
         * Other entered groups that should be de-activated if this entered group is given green light
         * to move again. Automatically cleared when this group itself is deactivated anyway.
         */
        protected final ArrayList<EnteredGroup> otherGroupsToDeactivate;
        /**
         * Inverse of otherGroupsToDeactivate
         */
        protected final ArrayList<EnteredGroup> groupsDeactivatingMe;
        protected IntVector3 groupsDeactivatingMeConflictRail;

        public EnteredGroup(double distanceToMutex) {
            this.occupiedRails = new MutexRailSlotMap();
            this.distanceToMutex = distanceToMutex;
            this.otherGroupsToDeactivate = new ArrayList<>(2);
            this.groupsDeactivatingMe = new ArrayList<>(2);
            this.groupsDeactivatingMeConflictRail = null;
        }

        protected EnteredGroup(EnteredGroup copy) {
            this.hardEnter = copy.hardEnter;
            this.active = copy.active;
            this.distanceToMutex = copy.distanceToMutex;
            this.occupiedRails = copy.occupiedRails;
            this.otherGroupsToDeactivate = copy.otherGroupsToDeactivate;
            this.groupsDeactivatingMe = copy.groupsDeactivatingMe;
            this.groupsDeactivatingMeConflictRail = copy.groupsDeactivatingMeConflictRail;
        }

        public abstract boolean isGroup(MinecartGroup group);

        public abstract String getTrainName();

        /**
         * Loads this entered group using the group information specified. If this entered
         * group is already a loaded entered group, returns this.
         *
         * @param slot MutexZoneSlot owner this group is inside of
         * @param group Group
         * @return This EnteredGroup promoted to a LoadedEnteredGroup
         */
        public abstract LoadedEnteredGroup load(MutexZoneSlot slot, MinecartGroup group);

        /**
         * Attemps to unload this entered group, if it is a loaded entered group
         *
         * @return Unloaded entered group
         */
        public abstract UnloadedEnteredGroup unload();

        /**
         * Gets the age of this entered group. This is for how many ticks this group has
         * been inside or waiting for the mutex.
         *
         * @return Age in ticks
         */
        public abstract int age();

        protected abstract boolean containsVerify(IntVector3 rail);

        protected abstract boolean refresh(MutexZoneSlot slot, Consumer<EnteredGroup> swap);
    }

    /**
     * Entered Group of a loaded MinecartGroup. This is a potentially moving train.
     */
    public static class LoadedEnteredGroup extends EnteredGroup {
        /** The MutexZoneSlot this group is inside of */
        private final MutexZoneSlot slot;
        /** The MinecartGroup represented */
        public final MinecartGroup group;
        /** Tracks the tick when this entered group was created. Resolves hard-hard conflicts */
        protected final int creationTick;
        /**
         * Tick timestamp when the group last 'found' the mutex zone, updating its state
         * This tick timestamp is also stored inside occupiedRails to check whether rails are ahead of
         * the group
         */
        protected int probeTick;
        /** Tracks the tick when this entered group last failed to enter the mutex, and returned OCCUPIED */
        public int occupiedTick;
        /** If a mutex conflict occurred, stores the event details of the conflict */
        private MutexZoneConflictEvent conflict = null;

        public LoadedEnteredGroup(MutexZoneSlot slot, MinecartGroup group, double distanceToMutex, int creationTick, int nowTicks) {
            super(distanceToMutex);
            this.slot = slot;
            this.group = group;
            this.creationTick = creationTick;
            this.probeTick = nowTicks;
            this.occupiedTick = nowTicks; // Not set
        }

        public LoadedEnteredGroup(MutexZoneSlot slot, MinecartGroup group, UnloadedEnteredGroup unloadedGroup) {
            super(unloadedGroup);
            int nowTicks = group.getObstacleTracker().getTickCounter();
            this.slot = slot;
            this.group = group;
            this.creationTick = nowTicks - unloadedGroup.age();
            this.probeTick = nowTicks;
            this.occupiedTick = nowTicks; // Not set
            this.occupiedRails.keepAlive(nowTicks); // Keep them around for this tick
        }

        @Override
        public boolean isGroup(MinecartGroup group) {
            return this.group == group;
        }

        @Override
        public String getTrainName() {
            return group.getProperties().getTrainName();
        }

        @Override
        public LoadedEnteredGroup load(MutexZoneSlot slot, MinecartGroup group) {
            return this;
        }

        @Override
        public UnloadedEnteredGroup unload() {
            return new UnloadedEnteredGroup(this);
        }

        @Override
        public int age() {
            return getObstacleTickCounter() - this.creationTick;
        }

        /**
         * Gets the {@link CommonUtil#getServerTicks()} timestamp when this entered group
         * was last probed by a Train. Used for keep-alive.
         *
         * @return Server tick timestamp this entered group was last probed
         */
        public int serverTickLastProbed() {
            return CommonUtil.getServerTicks() + probeTick - getObstacleTickCounter();
        }

        /**
         * Gets whether this group has fully entered the mutex. This is the case for non-smart
         * mutexes which lock everything.
         *
         * @return True if fully occupied
         */
        public boolean isOccupiedFully() {
            return occupiedRails.isFullyLocked();
        }

        /**
         * Gets the last (attempted) path taking through mutex zones that use this
         * mutex zone slot. If not entered, the last element stores the conflicting
         * rail block.
         *
         * @return last path taken.
         */
        public List<MutexRailSlot> getLastPath() {
            return occupiedRails.getLastPath();
        }

        /**
         * If last {@link LoadedEnteredGroup#enter(MutexZoneSlotType, IntVector3, boolean)} result was
         * {@link EnterResult#CONFLICT}, then this method returns the details
         * about that conflict.
         *
         * @return conflict event details
         */
        public MutexZoneConflictEvent getConflict() {
            return conflict;
        }

        /**
         * Tries to enter this mutex zone slot as this train.
         *
         * @param type What type of mutex zone is entered. Changes slot behavior.
         * @param hard Whether this is a hard-enter (train wants to enter the zone)
         * @param railBlock The rail block the group tries to enter currently. Is registered.
         * @return Enter Result
         */
        public EnterResult enter(MutexZoneSlotType type, IntVector3 railBlock, boolean hard) {
            // If true, returns SUCCESS_DELAY instead of SUCCESS to avoid trouble
            // We need one full tick to decide what train is allowed to go next
            // When this is the case, no train can ever hard-enter the mutex
            EnterResult successResult = EnterResult.SUCCESS;
            if (this.wasOccupiedLastTick()) {
                successResult = (this.conflict != null) ? EnterResult.CONFLICT_ONGOING
                        : EnterResult.OCCUPIED_DISCOVER;
            }

            {
                boolean wasFullyLocked = occupiedRails.isFullyLocked();

                // Make sure to register the rail at all times so we have this information
                // This is important when resolving the order of restoring trains when a train
                // leaves the mutex zone, and the zone contains smart mutexes.
                boolean addedNewSlot = this.occupiedRails.add(type, railBlock, this.probeTick);

                // If already occupied fully a previous tick/previous update, and this was not
                // cancelled by deactivate(), then we can skip all the expensive logic down below.
                // The train is in, it's going to stay that way.
                if (wasFullyLocked && hard == this.hardEnter && this.conflict == null) {
                    return successResult;
                }

                if (type == MutexZoneSlotType.SMART) {
                    // If already occupied previously at the same hardness level, ignore all below logic
                    // We can safely occupy it again without changing any of the logic.
                    if (!addedNewSlot && hard == this.hardEnter && this.conflict == null) {
                        return successResult;
                    }
                }
            }

            // Remove all soft-entered groups that share rails in common (or if null, any and all)
            // If we find another group that already hard-entered the mutex, cancel.
            for (EnteredGroup enteredGroup : slot.entered) {
                if (enteredGroup == this) {
                    continue;
                }
                if (!enteredGroup.active) {
                    // If this inactive group has been waiting longer than this group, and it's path
                    // intersects with a block this group uses, cancel. This other group has priority
                    // in this case.

                    // We do want two trains that can cross at the same time to cross, rather than a
                    // slower approach which follows true enter order. For this, we track how long ago
                    // the mutex gave green light to enter. If this was recently, then we ignore
                    // these checks temporarily.
                    if (enteredGroup.age() > this.age() &&
                            slot.tickLastHardEntered < (this.serverTickLastProbed() + 5) &&
                            (this.creationTick == this.probeTick || this.wasOccupiedLastTick()) &&
                            enteredGroup.containsVerify(railBlock)
                    ) {
                        this.hardEnter = false;
                        this.deactivate(railBlock);
                        return EnterResult.OCCUPIED;
                    }

                    continue;
                }
                if (!enteredGroup.containsVerify(railBlock)) {
                    continue;
                }

                // If the entered group contains this entered group to be de-activated, de-activate ourselves first.
                if (hard) {
                    // If hard-entering and previous group entered softly, revoke the previous soft slot
                    if (!enteredGroup.hardEnter && this.age() > enteredGroup.age()) {
                        deactivateOtherGroup(enteredGroup, railBlock);
                        continue;
                    }

                    // If we have an existing group that hard-entered the mutex before, then we've got a problem.
                    // The trajectory on the rails likely changed so there's now a collision
                    // We can't do much about it, but locking the train now it's inside the mutex would be
                    // a bad idea, too. Therefore, update the rails, but do nothing more.
                    // If we hard-entered, but this entered group was only just tracked, disallow the hard enter.
                    boolean hadConflict = (this.conflict != null);
                    if (hadConflict || this.creationTick == this.probeTick || !this.wasOccupiedLastTick()) {
                        // If this group or the other one isn't loaded, we can't make a conflict event just yet
                        // until the other group loads too.
                        if (enteredGroup instanceof LoadedEnteredGroup) {
                            this.conflict = new MutexZoneConflictEvent(
                                    this.group,
                                    ((LoadedEnteredGroup) enteredGroup).group,
                                    slot, railBlock);
                            this.occupiedTick = this.probeTick;
                        } else {
                            return hadConflict ? EnterResult.CONFLICT_ONGOING : EnterResult.CONFLICT;
                        }
                        return hadConflict ? EnterResult.CONFLICT_ONGOING : EnterResult.CONFLICT;
                    }
                }

                // This train is in violation and loses its entered slot privileges.
                // Depending on what type of train is occupying the zone, slow the train down
                // completely (HARD) or approach the zone carefully (SOFT)
                this.hardEnter = false;
                this.deactivate(railBlock);
                return EnterResult.OCCUPIED;
            }

            // Clear to go - update the existing group or add a new one
            if (hard && successResult == EnterResult.SUCCESS && !this.hardEnter) {
                this.hardEnter = true;
                slot.tickLastHardEntered = CommonUtil.getServerTicks();
                slot.setLevers(true);
            }

            // If it's okay again, reset any conflict groups we detected before
            // Also reset previous rails, so it actually checks those rails a second time
            if (successResult == EnterResult.SUCCESS && this.conflict != null) {
                this.conflict = null;
                this.occupiedRails.clearOldRails(probeTick);
            }

            return successResult;
        }

        private boolean wasOccupiedLastTick() {
            return (this.probeTick - this.occupiedTick) <= 1;
        }

        private void deactivate(IntVector3 conflictRail) {
            this.active = false;
            this.occupiedRails.clearConflict(conflictRail);
            this.occupiedTick = this.probeTick;
            if (!this.otherGroupsToDeactivate.isEmpty()) {
                for (EnteredGroup group : this.otherGroupsToDeactivate) {
                    group.groupsDeactivatingMe.remove(this);
                    if (group.groupsDeactivatingMe.isEmpty()) {
                        group.groupsDeactivatingMeConflictRail = null;
                    }
                }
                this.otherGroupsToDeactivate.clear();
            }
        }

        private void deactivateByOtherGroups() {
            if (!this.groupsDeactivatingMe.isEmpty()) {
                for (EnteredGroup g : this.groupsDeactivatingMe) {
                    g.otherGroupsToDeactivate.remove(this);
                }
                this.groupsDeactivatingMe.clear();
                this.deactivate(this.groupsDeactivatingMeConflictRail);
                this.groupsDeactivatingMeConflictRail = null;
            }
        }

        private void deactivateOtherGroup(EnteredGroup otherGroup, IntVector3 conflictRail) {
            if (!this.otherGroupsToDeactivate.contains(otherGroup)) {
                this.otherGroupsToDeactivate.add(otherGroup);
                otherGroup.groupsDeactivatingMe.add(this);
                otherGroup.groupsDeactivatingMeConflictRail = conflictRail;
            }
        }

        @Override
        protected boolean containsVerify(IntVector3 rail) {
            int nowTicks = probeTick;
            return occupiedRails.isFullyLockedVerify(group, nowTicks) ||
                    occupiedRails.isSmartLockedVerify(group, nowTicks, rail);
        }

        @Override
        protected boolean refresh(MutexZoneSlot slot, Consumer<EnteredGroup> swap) {
            // If group unloads or is deleted weirdly, clean it up right away
            if (group.isUnloaded() || !MinecartGroupStore.getGroups().contains(group)) {
                return false;
            }
            // If checked recently keep it around for now
            int nowTicks = getObstacleTickCounter();
            if ((nowTicks - probeTick) < TICK_DELAY_CLEAR_AUTOMATIC) {
                return true;
            }
            // Remove rail blocks that are no longer occupied by this group
            // If all rails are gone, that means the train is no longer using the slot at all
            if (!occupiedRails.verifyHasRailsUsedByGroup(group)) {
                return false;
            }
            // Still active
            probeTick = nowTicks;
            return true;
        }

        private int getObstacleTickCounter() {
            return group.getObstacleTracker().getTickCounter();
        }
    }

    /**
     * A group that has entered or is approaching a mutex zone, but has unloaded.
     * Is frozen in time until the group loads again or is removed.
     */
    public static class UnloadedEnteredGroup extends EnteredGroup {
        /** The name of the unloaded train this group is for */
        public final String trainName;
        /** Server timestamp when the original entered group was created */
        public final int creationServerTime;

        private UnloadedEnteredGroup(String trainName, double distanceToMutex, int age) {
            super(distanceToMutex);
            this.trainName = trainName;
            this.creationServerTime = CommonUtil.getServerTicks() - age;
        }

        public UnloadedEnteredGroup(LoadedEnteredGroup loadedGroup) {
            super(loadedGroup);
            this.trainName = loadedGroup.group.getProperties().getTrainName();
            this.creationServerTime = CommonUtil.getServerTicks() - loadedGroup.age();
        }

        public void save(TrainCarts plugin, OfflineDataBlock root) {
            OfflineDataBlock enteredGroupData;
            try {
                enteredGroupData = root.addChild("entered-group", stream -> {
                    stream.writeUTF(trainName);
                    stream.writeDouble(distanceToMutex);
                    stream.writeInt(age());

                    // List the group train names of otherGroupsToDeactivate / groupsDeactivatingMe
                    Util.writeVariableLengthInt(stream, otherGroupsToDeactivate.size());
                    for (EnteredGroup otherGroup : otherGroupsToDeactivate) {
                        stream.writeUTF(otherGroup.getTrainName());
                    }
                    Util.writeVariableLengthInt(stream, groupsDeactivatingMe.size());
                    for (EnteredGroup otherGroup : groupsDeactivatingMe) {
                        stream.writeUTF(otherGroup.getTrainName());
                    }
                    // Conflict rail, if set
                    stream.writeBoolean(groupsDeactivatingMeConflictRail != null);
                    if (groupsDeactivatingMeConflictRail != null) {
                        groupsDeactivatingMeConflictRail.write(stream);
                    }
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save mutex entered group data of train " + trainName, t);
                return;
            }

            // Save rails slot map as a child of the entered group
            try {
                occupiedRails.save(enteredGroupData);
            } catch (Throwable t) {
                // Undo addChild
                root.children.remove(root.children.size() - 1);
                plugin.getLogger().log(Level.SEVERE, "Failed to save mutex entered group rail slot data of train " + trainName, t);
                return;
            }
        }

        private static UnloadedEnteredGroupData loadData(TrainCarts plugin, OfflineDataBlock enteredGroupData) throws IOException {
            final UnloadedEnteredGroup group;
            final List<String> otherGroupsToDeactivateNames;
            final List<String> groupsDeactivatingMeNames;
            try (DataInputStream stream = enteredGroupData.readData()) {
                String trainName = stream.readUTF();
                double distanceToMutex = stream.readDouble();
                int age = stream.readInt();
                otherGroupsToDeactivateNames = readListOfStrings(stream);
                groupsDeactivatingMeNames = readListOfStrings(stream);
                IntVector3 groupsDeactivatingMeConflictRail = null;
                if (stream.readBoolean()) {
                    groupsDeactivatingMeConflictRail = IntVector3.read(stream);
                }

                // Create unloaded group with this data
                group = new UnloadedEnteredGroup(trainName, distanceToMutex, age);
                group.groupsDeactivatingMeConflictRail = groupsDeactivatingMeConflictRail;
            }

            // Load rails slot map as a child of the entered group
            group.occupiedRails.load(enteredGroupData);

            return new UnloadedEnteredGroupData(group, otherGroupsToDeactivateNames, groupsDeactivatingMeNames);
        }

        public static List<UnloadedEnteredGroup> loadAll(TrainCarts plugin, OfflineDataBlock mutexZoneSlotData) {
            List<OfflineDataBlock> enteredGroupDataList = mutexZoneSlotData.findChildren("entered-group");
            if (enteredGroupDataList.isEmpty()) {
                return Collections.emptyList();
            }

            List<UnloadedEnteredGroupData> unloadedGroupDataList = new ArrayList<>(enteredGroupDataList.size());
            for (OfflineDataBlock enteredGroupData : enteredGroupDataList) {
                try {
                    unloadedGroupDataList.add(loadData(plugin, enteredGroupData));
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load mutex entered group data", t);
                }
            }

            // Now populate otherGroupsToDeactivateNames / groupsDeactivatingMeNames
            List<UnloadedEnteredGroup> unloadedGroups = new ArrayList<>(unloadedGroupDataList.size());
            for (UnloadedEnteredGroupData unloadedGroupData : unloadedGroupDataList) {
                unloadedGroups.add(unloadedGroupData.load(unloadedGroupDataList));
            }
            return Collections.unmodifiableList(unloadedGroups);
        }

        private static List<String> readListOfStrings(DataInputStream stream) throws IOException {
            int count = Util.readVariableLengthInt(stream);
            if (count <= 0) {
                return Collections.emptyList();
            } else {
                List<String> result = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    result.add(stream.readUTF());
                }
                return Collections.unmodifiableList(result);
            }
        }

        @Override
        public boolean isGroup(MinecartGroup group) {
            return trainName.equals(group.getProperties().getTrainName());
        }

        @Override
        public String getTrainName() {
            return trainName;
        }

        @Override
        public LoadedEnteredGroup load(MutexZoneSlot slot, MinecartGroup group) {
            return new LoadedEnteredGroup(slot, group, this);
        }

        @Override
        public UnloadedEnteredGroup unload() {
            return this;
        }

        @Override
        public int age() {
            return CommonUtil.getServerTicks() - creationServerTime;
        }

        @Override
        protected boolean containsVerify(IntVector3 rail) {
            return occupiedRails.isFullyLocked() || occupiedRails.isSmartLocked(rail);
        }

        @Override
        protected boolean refresh(MutexZoneSlot slot, Consumer<EnteredGroup> swap) {
            // If train does not exist, remove this entered group
            TrainProperties trainsProps = TrainPropertiesStore.get(trainName);
            if (trainsProps == null) {
                return false;
            }

            // If loaded, restore this unloaded group into a loaded one
            // This allows the entered group to be cleaned up if the group doesn't
            // activate it.
            MinecartGroup group = trainsProps.getHolder();
            if (group != null) {
                swap.accept(this.load(slot, group));
            }

            return true;
        }
    }

    private static class UnloadedEnteredGroupData {
        public final UnloadedEnteredGroup group;
        public final List<String> otherGroupsToDeactivateNames;
        public final List<String> groupsDeactivatingMeNames;

        public UnloadedEnteredGroupData(
                UnloadedEnteredGroup group,
                List<String> otherGroupsToDeactivateNames,
                List<String> groupsDeactivatingMeNames
        ) {
            this.group = group;
            this.otherGroupsToDeactivateNames = otherGroupsToDeactivateNames;
            this.groupsDeactivatingMeNames = groupsDeactivatingMeNames;
        }

        public UnloadedEnteredGroup load(List<UnloadedEnteredGroupData> otherEnteredGroups) {
            loadEnteredGroupList(otherGroupsToDeactivateNames, otherEnteredGroups, group.otherGroupsToDeactivate);
            loadEnteredGroupList(groupsDeactivatingMeNames, otherEnteredGroups, group.groupsDeactivatingMe);
            return group;
        }

        private static void loadEnteredGroupList(
                List<String> groupNames,
                List<UnloadedEnteredGroupData> otherEnteredGroups,
                List<EnteredGroup> groupsTarget
        ) {
            groupsTarget.clear();
            for (String name : groupNames) {
                for (UnloadedEnteredGroupData data : otherEnteredGroups) {
                    if (name.equals(data.group.getTrainName())) {
                        groupsTarget.add(data.group);
                        break;
                    }
                }
            }
        }
    }

    private final class IgnoredEnteredGroup extends LoadedEnteredGroup {

        public IgnoredEnteredGroup(MutexZoneSlot slot, MinecartGroup group, double distanceToMutex, int nowTicks) {
            super(slot, group, distanceToMutex, nowTicks, nowTicks);
        }

        @Override
        public EnterResult enter(MutexZoneSlotType type, IntVector3 railBlock, boolean hard) {
            return EnterResult.IGNORED;
        }
    }
}
