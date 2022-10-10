package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.WorldRailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.statements.Statement;

/**
 * A slot where a group can be put that was granted access to move through.
 * Named mutex zones can share the same (named) slot
 */
public class MutexZoneSlot {
    private static final int TICK_DELAY_CLEAR_AUTOMATIC = 6; // clear delay when no trains are waiting for it
    private static final int TICK_DELAY_CLEAR_WAITING = 5; // clear delay when trains are waiting for it
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

    public boolean hasZones() {
        return !this.zones.isEmpty();
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
     * 
     * @param trainWaiting Whether this is called while a train is waiting for the mutex
     * @param now Current server tick timestamp
     */
    public void refresh(boolean trainWaiting, Timestamp now) {
        if (!entered.isEmpty()) {
            Iterator<EnteredGroup> iter = entered.iterator();
            boolean hasHardEnteredGroup = false;
            boolean trainsHaveLeft = false;
            while (iter.hasNext()) {
                EnteredGroup enteredGroup = iter.next();
                if (!this.refresh(enteredGroup, trainWaiting, now)) {
                    iter.remove();
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

    private boolean refresh(EnteredGroup enteredGroup, boolean trainWaiting, Timestamp now) {
        if (enteredGroup.group.isUnloaded() || !MinecartGroupStore.getGroups().contains(enteredGroup.group)) {
            return false;
        }

        if ((now.ticks - enteredGroup.time.ticks) >= (trainWaiting ? TICK_DELAY_CLEAR_WAITING : TICK_DELAY_CLEAR_AUTOMATIC)) {
            // Check whether the group is still occupying this mutex zone
            // Do so by iterating all the rails (positiosn!) of that train
            for (TrackedRail rail : enteredGroup.group.getRailTracker().getRailInformation()) {
                if (this.containsBlock(rail.minecartBlock)) {
                    enteredGroup.time = now;
                    return true;
                }
            }

            // It is not. clear it.
            return false;
        }

        return true;
    }

    /**
     * Looks up the EnteredGroup of a MinecartGroup, if it had
     * (tried to) enter in the recent past.
     *
     * @param group
     * @return entered group
     */
    public EnteredGroup findEntered(MinecartGroup group) {
        for (EnteredGroup entered : this.entered) {
            if (entered.group == group) {
                return entered;
            }
        }
        return null;
    }

    /**
     * Starts tracking the group for this zone. Must call {@link EnteredGroup#enterRail(boolean, IntVector3)}
     * or {@link EnteredGroup#enterZone(boolean)} afterwards, which might result in the group
     * being untracked again when rejected.
     *
     * @param group MinecartGroup to track
     * @param distanceToMutex Distance from the front of the group to this mutex zone
     * @return EnteredGroup of this group
     */
    public EnteredGroup track(MinecartGroup group, double distanceToMutex) {
        // Verify using statements whether the group is even considered
        {
            List<String> statements = this.getStatements();
            if (!statements.isEmpty()) {
                SignActionEvent signEvent = null;
                if (this.zones.size() == 1) {
                    Block signBlock = this.zones.get(0).getSignBlock();
                    if (signBlock != null && MaterialUtil.ISSIGN.get(signBlock)) {
                        signEvent = new SignActionEvent(signBlock, group);
                        signEvent.setAction(SignActionType.GROUP_ENTER);
                    }
                }
                if (!Statement.hasMultiple(group, this.getStatements(), signEvent)) {
                    // If previously tracked, release the train
                    boolean wasGroupHardEntered = false;
                    boolean hasHardEnteredGroup = false;
                    for (Iterator<EnteredGroup> iter = this.entered.iterator(); iter.hasNext();) {
                        EnteredGroup entered = iter.next();
                        if (entered.group == group) {
                            iter.remove();
                            wasGroupHardEntered = entered.hardEnter;
                        } else if (entered.hardEnter) {
                            hasHardEnteredGroup = true;
                        }
                    }
                    if (wasGroupHardEntered && !hasHardEnteredGroup) {
                        this.setLevers(false);
                    }

                    // Ignored!
                    return new IgnoredEnteredGroup(group, distanceToMutex);
                }
            }
        }

        // Find existing
        for (EnteredGroup enteredGroup : this.entered) {
            if (enteredGroup.group == group) {
                enteredGroup.time = Timestamp.now();
                enteredGroup.conflictPath = null;
                if (enteredGroup.active) {
                    enteredGroup.distanceToMutex = Math.min(enteredGroup.distanceToMutex, distanceToMutex);
                } else {
                    enteredGroup.active = true;
                    enteredGroup.activeTick = enteredGroup.time.ticks;
                    enteredGroup.distanceToMutex = distanceToMutex;
                }
                return enteredGroup;
            }
        }

        EnteredGroup enteredGroup = new EnteredGroup(group, distanceToMutex);
        this.entered.add(enteredGroup);
        return enteredGroup;
    }

    private void setLevers(boolean down) {
        for (MutexZone zone : this.zones) {
            zone.setLevers(down);
        }
    }

    private boolean containsBlock(Block block) {
        for (MutexZone zone : this.zones) {
            if (zone.signBlock.getLoadedWorld() == block.getWorld() && zone.containsBlock(block)) {
                return true;
            }
        }
        return false;
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
                if (enteredGroup.active && enteredGroup.hardEnter) {
                    result.add(enteredGroup.group);
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
                if (enteredGroup.active) {
                    result.add(enteredGroup.group);
                }
            }
            return result;
        }
    }

    /**
     * The result of trying to enter a mutex
     */
    public static enum EnterResult {
        /** The group does not match conditions to enter/be seen by the mutex zone */
        IGNORED,
        /** The mutex zone was entered so far */
        SUCCESS,
        /** The mutex zone is occupied and the train cannot enter it */
        OCCUPIED
    }

    /**
     * Tracks the information of a single train that has entered a mutex zone
     */
    public class EnteredGroup {
        /** The MinecartGroup represented */
        public final MinecartGroup group;
        /** Whether the group has actually entered the mutex zone (true) or is about to (false) */
        public boolean hardEnter = false;
        /** Whether the group is scheduled to go into the mutex zone next, or is already */
        public boolean active = true;
        /** Distance from the front of the train to where this slot was first encountered */
        public double distanceToMutex;
        /**
         * Tick timestamp when the group last 'found' the mutex zone, updating its state
         * This tick timestamp is also stored inside occupiedRails to check whether rails are ahead of
         * the group
         */
        public Timestamp time;
        /** Tracks the tick where this entered group was created. Resolves hard-hard conflicts */
        public final int creationTick;
        /** Tracks the tick where this entered group was activated. Used to detect just-activated (new) groups. */
        public int activeTick;
        /** If used, the rail coordinates locked by the group (smart mutex) */
        private Map<IntVector3, Timestamp> occupiedRails = Collections.emptyMap();
        /** Whether the occupied rails need first-time initialization to store rails */
        private boolean occupiedRailsNeedsInit = true;
        /** If waiting for another train to clear the mutex, a conflict rail block/path */
        public ConflictPath conflictPath = null;

        public EnteredGroup(MinecartGroup group, double distanceToMutex) {
            this.group = group;
            this.time = Timestamp.now();
            this.activeTick = this.creationTick = this.time.ticks;
            this.distanceToMutex = distanceToMutex;
        }

        private void deactivate(IntVector3 conflictRail) {
            this.active = false;
            this.activeTick = -1;
            if (this.occupiedRails != null && conflictRail != null) {
                if (!this.occupiedRails.isEmpty()) {
                    this.occupiedRails.remove(conflictRail);
                }
                this.conflictPath = new ConflictPath(this.occupiedRails.keySet(), conflictRail);
            }
            this.occupiedRails = Collections.emptyMap();
            this.occupiedRailsNeedsInit = true;
        }

        private boolean isJustActivated() {
            return this.activeTick == this.time.ticks;
        }

        public Collection<IntVector3> getRailBlocks() {
            return occupiedRails == null ? Collections.emptyList() : occupiedRails.keySet();
        }

        private boolean containsRail(IntVector3 coordinates) {
            Map<IntVector3, Timestamp> rails = this.occupiedRails;
            if (rails == null) {
                // Not a smart mutex, train occupies all rails.
                return true;
            }

            Timestamp timeOccupied = rails.get(coordinates);
            if (timeOccupied == null) {
                // Not visited this rail
                return false;
            }

            if (timeOccupied.equals(this.time)) {
                // Rail is at or ahead of this train, no need to check
                return true;
            }
            if (group.isEmpty() || group.isUnloaded()) {
                // Check group is even still there, or it might get stuck on this
                //TODO: Make clean this up, if this happens at all...
                return false;
            }

            // Verify that this particular rail block is still actually used by this group
            // This detects when the tail of the train leaves particular track
            // We can use the rail lookup cache to figure this out really efficiently, because
            // members register themselves on the rail piece they occupy.
            WorldRailLookup railLookup = group.head().railLookup();
            for (RailLookup.CachedRailPiece railPiece : railLookup.lookupCachedRailPieces(
                    railLookup.getOfflineWorld().getBlockAt(coordinates))
            ) {
                for (MinecartMember<?> member : railPiece.cachedMembers()) {
                    if (member.isUnloaded() || member.getEntity().isRemoved()) {
                        continue; // Skip
                    }
                    if (member.getGroup() == group) {
                        return true;
                    }
                }
            }

            // Omit the rails, no longer occupied
            rails.remove(coordinates);
            return false;
        }

        /**
         * Tries to enter this mutex zone slot entirely, without tracking occupancy of individual rails
         *
         * @param hard Whether this is a hard-enter (train wants to enter the zone)
         * @return Enter Result
         */
        public EnterResult enterZone(boolean hard) {
            return enterRail(hard, null);
        }

        /**
         * Tries to enter a single rail within this mutex zone. This is used for 'smart' mutexes.
         *
         * @param hard Whether this is a hard-enter (train wants to enter the zone)
         * @param railBlock The rail block the group tries to enter. Null for non-smart mutex zones.
         * @return Enter Result
         */
        public EnterResult enterRail(boolean hard, IntVector3 railBlock) {
            // Initialize the tracked rail blocks the first time it happens
            // Subsequent times, ignore if the rail block was already successfully 'entered'
            if (railBlock != null) {
                if (this.occupiedRailsNeedsInit) {
                    this.occupiedRailsNeedsInit = false;
                    this.occupiedRails = new LinkedHashMap<>();
                    this.occupiedRails.put(railBlock, this.time);
                } else if (this.occupiedRails.put(railBlock, this.time) != null && hard == this.hardEnter) {
                    return EnterResult.SUCCESS;
                }
            } else {
                this.occupiedRails = null; // Mark as non-smart mutex
            }

            // Remove all soft-entered groups that share rails in common (or if null, any and all)
            // If we find another group that already hard-entered the mutex, cancel.
            for (EnteredGroup enteredGroup : MutexZoneSlot.this.entered) {
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
                    if (this.isJustActivated() &&
                        enteredGroup.creationTick < this.creationTick &&
                        tickLastHardEntered < (this.time.ticks + 5) &&
                        enteredGroup.containsRail(railBlock)
                    ) {
                        this.deactivate(railBlock);
                        return EnterResult.OCCUPIED;
                    }

                    continue;
                }
                if (!enteredGroup.containsRail(railBlock)) {
                    continue;
                }

                // If hard-entered, revoke the previous soft slot
                if (hard && !enteredGroup.hardEnter && this.creationTick < enteredGroup.creationTick) {
                    enteredGroup.deactivate(railBlock);
                    continue;
                }

                // If we have an existing group that hard-entered the mutex before, then we've got a problem.
                // The trajectory on the rails likely changed so there's now a collision
                // We can't do much about it, but locking the train now it's inside the mutex would be
                // a bad idea, too. Therefore, update the rails, but do nothing more.
                // If we hard-entered, but this entered group was only just tracked, disallow the hard enter.
                if (this.hardEnter && !this.isJustActivated()) {
                    System.out.println("Train " + group.getProperties().getTrainName() + " is colliding with " +
                              enteredGroup.group.getProperties().getTrainName() + " after a sudden path change!");
                    System.out.println("Problem occurred at rail: " + railBlock);
                    break;
                }

                // This train is in violation and loses its entered slot privileges.
                // Depending on what type of train is occupying the zone, slow the train down
                // completely (HARD) or approach the zone carefully (SOFT)
                this.hardEnter = false;
                this.deactivate(railBlock);
                return EnterResult.OCCUPIED;
            }

            // Clear to go - update the existing group or add a new one
            if (hard && !this.hardEnter) {
                this.hardEnter = true;
                tickLastHardEntered = time.ticks;
                setLevers(true);
            }
            return EnterResult.SUCCESS;
        }
    }

    private final class IgnoredEnteredGroup extends EnteredGroup {

        public IgnoredEnteredGroup(MinecartGroup group, double distanceToMutex) {
            super(group, distanceToMutex);
        }

        @Override
        public EnterResult enterZone(boolean hard) {
            return EnterResult.IGNORED;
        }

        @Override
        public EnterResult enterRail(boolean hard, IntVector3 railBlock) {
            return EnterResult.IGNORED;
        }
    }

    /**
     * Stores a current tick timestamp. Used as value in the occupied rails mapping
     * to track what rails are ahead of the train (updated last tick)
     */
    public static final class Timestamp {
        public final int ticks;

        public static Timestamp now() {
            return new Timestamp();
        }

        private Timestamp() {
            this.ticks = CommonUtil.getServerTicks();
        }

        @Override
        public int hashCode() {
            return ticks;
        }

        @Override
        public boolean equals(Object o) {
            return ((Timestamp) o).ticks == ticks;
        }
    }

    public static final class ConflictPath {
        public final Set<IntVector3> path;
        public final IntVector3 conflict;

        public ConflictPath(Set<IntVector3> path, IntVector3 conflict) {
            this.path = path;
            this.conflict = conflict;
        }
    }
}
