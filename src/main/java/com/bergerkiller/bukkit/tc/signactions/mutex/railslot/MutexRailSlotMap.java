package com.bergerkiller.bukkit.tc.signactions.mutex.railslot;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.WorldRailLookup;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlotType;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the rail blocks within a mutex zone that have been occupied by a train.
 * Includes logic to clean this up again once a train is no longer using it.
 * Each rail block coordinate is a single slot where occupancy is checked.
 * Supports both full (normal mutex) and smart mutex checks, where full denies
 * others to enter it if any one rail is occupied.
 */
public class MutexRailSlotMap {
    private static final Map<IntVector3, MutexRailSlot> INITIAL_RAILS = Collections.emptyMap();
    /**
     * Rails gathered in realtime. Will store the conflicting rails if the mutex
     * could not be entered last time. Is reset every tick / enter attempt.
     */
    private final LinkedHashMap<IntVector3, MutexRailSlot> railsLive = new LinkedHashMap<>();
    /** Those rail slots added to rails which are normal mutex slots ('full' locking) */
    private final ArrayList<MutexRailSlot> railsFull = new ArrayList<>();
    /** Currently occupied rails */
    private Map<IntVector3, MutexRailSlot> rails = INITIAL_RAILS;
    /** Last conflicting rail block */
    private MutexRailSlot conflict = null;

    /**
     * Gets the last rails that were visited to enter a mutex zone slot, successful or not.
     * When successful, it contains all the rails that are locked. When unsuccessful,
     * it shows the path that was attempted with the last slot containing the conflicting
     * rail block.
     *
     * @return last visited path
     */
    public List<MutexRailSlot> getLastPath() {
        ArrayList<MutexRailSlot> result = new ArrayList<>(railsLive.values());
        if (conflict != null) {
            result.add(conflict);
        }
        return result;
    }

    public void clearConflict(IntVector3 conflictRail) {
        MutexRailSlot prevConflict = this.conflict;
        rails = INITIAL_RAILS;
        railsFull.clear();
        conflict = railsLive.remove(conflictRail);
        if (conflict == null) {
            conflict = prevConflict;
        }
    }

    /**
     * Removes all stored rail blocks older than the nowTicks specified
     *
     * @param nowTicks
     */
    public void clearOldRails(int nowTicks) {
        for (Iterator<MutexRailSlot> iter = this.rails.values().iterator(); iter.hasNext();) {
            MutexRailSlot slot = iter.next();
            if (slot.ticksLastProbed() < nowTicks) {
                onSlotRemoved(slot);
                iter.remove();
            }
        }
    }

    /**
     * Keeps all existing added rails alive. This prevents rails being removed
     * the tick after a train goes from unloaded to loaded.
     *
     * @param nowTicks Current tick timestamp of the obstacle tracker
     */
    public void keepAlive(int nowTicks) {
        railsLive.values().forEach(slot -> slot.probe(nowTicks));
    }

    public boolean add(MutexZoneSlotType type, IntVector3 railBlock, int nowTicks) {
        Map<IntVector3, MutexRailSlot> currRails = this.rails;
        if (currRails == INITIAL_RAILS) {
            rails = currRails = railsLive;
            currRails.clear();
            conflict = null;
        }
        MutexRailSlot slot = currRails.computeIfAbsent(railBlock, MutexRailSlot::new);
        boolean added = slot.isNew();
        boolean wasFullLocking = slot.isFullLocking();
        slot.probe(type, nowTicks);
        if (!wasFullLocking && slot.isFullLocking()) {
            railsFull.add(slot);
        }
        return added;
    }

    @SuppressWarnings("unused")
    public boolean remove(IntVector3 railBlock) {
        Map<IntVector3, MutexRailSlot> rails = this.rails;
        if (rails.isEmpty()) {
            return false;
        }
        MutexRailSlot slot = rails.remove(railBlock);
        if (slot == null) {
            return false;
        }
        onSlotRemoved(slot);
        return true;
    }

    /**
     * Gets whether any of the rail slots mapped require locking the full mutex slot
     *
     * @return True if fully locked
     */
    public boolean isFullyLocked() {
        return !railsFull.isEmpty();
    }

    public boolean isFullyLockedVerify(MinecartGroup group, int nowTicks) {
        List<MutexRailSlot> railsFull = this.railsFull;
        if (!railsFull.isEmpty()) {
            for (Iterator<MutexRailSlot> iter = this.railsFull.iterator(); iter.hasNext();) {
                MutexRailSlot slot = iter.next();
                if (nowTicks == slot.ticksLastProbed() || isRailUsedByGroup(slot.rail(), group)) {
                    return true;
                } else {
                    // No longer used, release this particular rail block. Might release the entire mutex.
                    railsLive.remove(slot.rail());
                    iter.remove();
                }
            }
        }
        return false;
    }

    public boolean isSmartLocked(IntVector3 rail) {
        return rails.containsKey(rail);
    }

    public boolean isSmartLockedVerify(MinecartGroup group, int nowTicks, IntVector3 rail) {
        MutexRailSlot slot = rails.get(rail);
        if (slot == null) {
            return false; // Not stored
        } else if (nowTicks == slot.ticksLastProbed()) {
            return true; // Updated this tick, no need to verify
        }

        // Verify that, at this rail, it really does still store the group
        if (group.isEmpty() || group.isUnloaded()) {
            // Check group is even still there, or it might get stuck on this
            //TODO: Make clean this up, if this happens at all...
            return false;
        }

        // Verify that this particular rail block is still actually used by this group
        // This detects when the tail of the train leaves particular track
        // We can use the rail lookup cache to figure this out really efficiently, because
        // members register themselves on the rail piece they occupy.
        if (isRailUsedByGroup(rail, group)) {
            return true;
        }

        // Omit the rails, no longer occupied
        onSlotRemoved(slot);
        rails.remove(rail);
        return false;
    }

    public boolean verifyHasRailsUsedByGroup(MinecartGroup group) {
        Iterator<MutexRailSlot> iter = rails.values().iterator();
        while (iter.hasNext()) {
            MutexRailSlot slot = iter.next();
            if (isRailUsedByGroup(slot.rail(), group)) {
                // Note: no use clearing other rails. If somebody cares, it'll be
                //       cleaned up automatically anyway.
                return true;
            } else {
                onSlotRemoved(slot);
                iter.remove();
            }
        }
        return false;
    }

    private void onSlotRemoved(MutexRailSlot slot) {
        if (slot.isFullLocking()) {
            railsFull.remove(slot);
        }
    }

    /**
     * Checks whether a particular rail block is used by a MinecartGroup
     *
     * @param rail Rail block coordinates
     * @param group Group to find
     * @return True if the group is currently using/on top of the rail block specified
     */
    private static boolean isRailUsedByGroup(IntVector3 rail, MinecartGroup group) {
        if (group.isEmpty() || group.isUnloaded()) {
            return false;
        }

        WorldRailLookup railLookup = group.head().railLookup();
        for (RailLookup.CachedRailPiece railPiece : railLookup.lookupCachedRailPieces(
                railLookup.getOfflineWorld().getBlockAt(rail))
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
        return false;
    }

    public void save(OfflineDataBlock root) throws IOException {
        root.addChild("rail-slots", stream -> {
            // Are rails set to initial or not?
            stream.writeBoolean(rails == INITIAL_RAILS);

            // Write out all rail slots in railsLive
            // Even if rails is empty, this might be non-empty for getLastPath()
            Util.writeVariableLengthInt(stream, railsLive.size());
            for (MutexRailSlot slot : railsLive.values()) {
                slot.writeTo(stream);
            }

            // Write out a conflict, if set
            stream.writeBoolean(conflict != null);
            if (conflict != null) {
                conflict.writeTo(stream);
            }
        });
    }

    public void load(OfflineDataBlock root) throws IOException {
        try (DataInputStream stream = root.findChildOrThrow("rail-slots").readData()) {
            boolean isSetToInitial = stream.readBoolean();
            int numRailSlots = Util.readVariableLengthInt(stream);

            // Load in the rail slots
            railsLive.clear();
            railsFull.clear();
            MutexZoneSlotType[] types = MutexZoneSlotType.values();
            for (int num = 0; num < numRailSlots; num++) {
                MutexRailSlot slot = MutexRailSlot.read(stream);
                railsLive.put(slot.rail(), slot);
                if (slot.isFullLocking()) {
                    railsFull.add(slot);
                }
            }
            rails = isSetToInitial ? INITIAL_RAILS : railsLive;

            // Load in a conflict rail, if it exists
            if (stream.readBoolean()) {
                conflict = MutexRailSlot.read(stream);

                // If an identical slot exists in mapping, use that reference instead
                MutexRailSlot existing = railsLive.get(conflict.rail());
                if (existing != null && existing.type() == conflict.type()) {
                    conflict = existing;
                }
            } else {
                conflict = null;
            }
        }
    }
}
