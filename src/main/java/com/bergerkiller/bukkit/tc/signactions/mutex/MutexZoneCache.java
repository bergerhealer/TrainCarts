package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorldMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;

public class MutexZoneCache {
    private static final OfflineWorldMap<Map<IntVector3, MutexZone>> zones = new OfflineWorldMap<>();
    private static final Map<String, MutexZoneSlot> slotsByName = new HashMap<>();
    private static final List<MutexZoneSlot> slotsList = new ArrayList<>();

    public static void init(TrainCarts plugin) {
        plugin.getOfflineSigns().registerHandler(MutexSignMetadata.class, new OfflineSignMetadataHandler<MutexSignMetadata>() {

            @Override
            public void onAdded(OfflineSignStore store, OfflineSign sign, MutexSignMetadata metadata) {
                addMutexSign(sign.getWorld(), sign.getPosition(), metadata);
            }

            @Override
            public void onRemoved(OfflineSignStore store, OfflineSign sign, MutexSignMetadata metadata) {
                removeMutexSign(sign.getWorld(), sign.getPosition());
            }

            @Override
            public void onUpdated(OfflineSignStore store, OfflineSign sign, MutexSignMetadata oldValue, MutexSignMetadata newValue) {
                onRemoved(store, sign, oldValue);
                onAdded(store, sign, newValue);
            }

            @Override
            public void onEncode(DataOutputStream stream, OfflineSign sign, MutexSignMetadata value) throws IOException {
                stream.writeUTF(value.name);
                value.start.write(stream);
                value.end.write(stream);
                stream.writeUTF(value.statement);
            }

            @Override
            public MutexSignMetadata onDecode(DataInputStream stream, OfflineSign sign) throws IOException {
                String name = stream.readUTF();
                IntVector3 start = IntVector3.read(stream);
                IntVector3 end = IntVector3.read(stream);
                String statement = stream.readUTF();
                String typeName = sign.getLine(1).toLowerCase(Locale.ENGLISH);
                boolean smart = typeName.startsWith("smartmutex") || typeName.startsWith("smutex");
                return new MutexSignMetadata(name, start, end, statement, smart);
            }
        });
    }

    public static void deinit(TrainCarts plugin) {
        plugin.getOfflineSigns().unregisterHandler(MutexSignMetadata.class);
    }

    private static void addMutexSign(OfflineWorld world, IntVector3 signPosition, MutexSignMetadata metadata) {
        Map<IntVector3, MutexZone> atWorld = zones.computeIfAbsent(world, unused -> new HashMap<>());
        atWorld.put(signPosition, MutexZone.create(world, signPosition, metadata));
    }

    private static void removeMutexSign(OfflineWorld world, IntVector3 signPosition) {
        // This causes pain & suffering (chunk unload event - accessing block data doesn't work)
        // zones.remove(info.getWorld(), MutexZone.getPosition(info));

        // Instead, a slow way
        MutexZone zone = zones.computeIfAbsent(world, unused -> new HashMap<>())
                .remove(signPosition);
        if (zone != null) {
            removeMutexZone(zone);
        }
    }

    /**
     * Finds a mutex zone at a particular block
     *
     * @param block
     * @return mutex zone, null if not found
     */
    public static MutexZone find(OfflineBlock block) {
        for (MutexZone zone : zones.getOrDefault(block.getWorld(), Collections.emptyMap()).values()) {
            if (zone.containsBlock(block.getPosition())) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Finds a mutex zone at a particular block
     *
     * @param world
     * @param block
     * @return mutex zone, null if not found
     */
    public static MutexZone find(OfflineWorld world, IntVector3 block) {
        for (MutexZone zone : zones.getOrDefault(world, Collections.emptyMap()).values()) {
            if (zone.containsBlock(block)) {
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
    public static boolean isMutexZoneNearby(OfflineWorld world, IntVector3 block, int radius) {
        for (MutexZone zone : zones.getOrDefault(world, Collections.emptyMap()).values()) {
            if (zone.isNearby(block, radius)) {
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
    public static List<MutexZone> findNearbyZones(OfflineWorld world, IntVector3 block, int radius) {
        List<MutexZone> result = new ArrayList<MutexZone>();
        for (MutexZone zone : zones.getOrDefault(world, Collections.emptyMap()).values()) {
            if (zone.isNearby(block, radius)) {
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
        if (name == null) {
            throw new IllegalArgumentException("Name is null");
        }

        MutexZoneSlot slot;
        if (name.isEmpty()) {
            slot = new MutexZoneSlot("");
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
