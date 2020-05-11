package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZoneCache {
    private static final BlockMap<MutexZone> zones = new BlockMap<MutexZone>();
    private static final Map<String, MutexZoneSlot> slotsByName = new HashMap<>();
    private static final List<MutexZoneSlot> slotsList = new ArrayList<>();

    /**
     * Loads the mutex zones in a Chunk by iterating the signs within
     * 
     * @param chunk
     */
    public static void loadChunk(Chunk chunk) {
        for (BlockState state : WorldUtil.getBlockStates(chunk)) {
            if (!(state instanceof Sign)) {
                return;
            }
        }
    }

    public static void addMutexSign(SignActionEvent info) {
        zones.put(info.getBlock(), MutexZone.fromSign(info));
    }

    public static void removeMutexSign(SignActionEvent info) {
        // This causes pain & suffering (chunk unload event - accessing block data doesn't work)
        // zones.remove(info.getWorld(), MutexZone.getPosition(info));

        // Instead, a slow way
        IntVector3 signPos = new IntVector3(info.getBlock());
        Iterator<MutexZone> zones_iter = zones.values().iterator();
        while (zones_iter.hasNext()) {
            MutexZone zone = zones_iter.next();
            if (zone.sign.equals(signPos)) {
                zones_iter.remove();
                removeMutexZone(zone);
            }
        }
    }

    /**
     * Finds a mutex zone at a particular block
     * 
     * @param world
     * @param block
     * @return mutex zone, null if not found
     */
    public static MutexZone find(UUID world, IntVector3 block) {
        for (MutexZone zone : zones.values()) {
            if (zone.containsBlock(world, block)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Checks whether there is a mutex zone nearby a particular block. This checks for a small
     * radius around the mutex zones. Minecarts with their position inside this zone need to watch out
     * and perform speed-ahead checks in order to stop.
     * 
     * @param world
     * @param block
     * @param radius
     * @return True if a mutex zone is nearby
     */
    public static boolean isMutexZoneNearby(UUID world, IntVector3 block, int radius) {
        for (MutexZone zone : zones.values()) {
            if (zone.isNearby(world, block, radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds all mutex zones nearby a position. See {@link #isMutexZoneNearby(UUID, IntVector3, int)}
     * 
     * @param world
     * @param block
     * @param radius
     * @return
     */
    public static List<MutexZone> findNearbyZones(UUID world, IntVector3 block, int radius) {
        List<MutexZone> result = new ArrayList<MutexZone>();
        for (MutexZone zone : zones.values()) {
            if (zone.isNearby(world, block, radius)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Finds or creates a new mutex zone slot by name for a certain zone
     * 
     * @param name name of the slot, null to create an anonymous slot
     * @param zone
     * @return slot
     */
    public static MutexZoneSlot findSlot(String name, MutexZone zone) {
        MutexZoneSlot slot;
        if (name == null) {
            slot = new MutexZoneSlot(null);
        } else {
            slot = slotsByName.computeIfAbsent(name, MutexZoneSlot::new);
        }
        slotsList.add(slot);
        return slot.addZone(zone);
    }

    private static void removeMutexZone(MutexZone zone) {
        zone.slot.removeZone(zone);
        if (!zone.slot.hasZones()) {
            if (!zone.slot.isAnonymous()) {
                slotsByName.remove(zone.slot.getName());
            }
            slotsList.remove(zone.slot);
        }
    }

    /**
     * Refreshes all mutex zone slots, releasing groups that are no longer on it
     */
    public static void refreshAll() {
        // Note: done by index on purpose to avoid concurrent modification exceptions
        // They may occur if a zone loads/unloads as a result of a lever toggle/etc.
        for (int i = 0; i < slotsList.size(); i++) {
            slotsList.get(i).refresh(false);
        }
    }
}
