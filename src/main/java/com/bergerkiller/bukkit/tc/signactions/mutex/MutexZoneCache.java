package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
    private static final OfflineWorldMap<MutexZoneCacheWorld> cachesByWorld = new OfflineWorldMap<>();
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
                MutexZoneSlotType type = (typeName.startsWith("smartmutex") || typeName.startsWith("smutex"))
                        ? MutexZoneSlotType.SMART : MutexZoneSlotType.NORMAL;
                return new MutexSignMetadata(type, name, start, end, statement);
            }
        });
    }

    /**
     * Gets mutex zone information of a certain world
     *
     * @param world Offline World
     * @return Mutex zones of the World
     */
    public static MutexZoneCacheWorld forWorld(OfflineWorld world) {
        return cachesByWorld.computeIfAbsent(world, MutexZoneCacheWorld::new);
    }

    public static void deinit(TrainCarts plugin) {
        plugin.getOfflineSigns().unregisterHandler(MutexSignMetadata.class);
    }

    private static void addMutexSign(OfflineWorld world, IntVector3 signPosition, MutexSignMetadata metadata) {
        forWorld(world).add(MutexZone.create(world, signPosition, metadata));
    }

    private static void removeMutexSign(OfflineWorld world, IntVector3 signPosition) {
        // This causes pain & suffering (chunk unload event - accessing block data doesn't work)
        // zones.remove(info.getWorld(), MutexZone.getPosition(info));

        // Instead, a slow way
        MutexZone zone = forWorld(world).removeAtSign(signPosition);
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
        return forWorld(block.getWorld()).find(block.getPosition());
    }

    /**
     * Finds a mutex zone at a particular block
     *
     * @param world
     * @param block
     * @return mutex zone, null if not found
     */
    public static MutexZone find(OfflineWorld world, IntVector3 block) {
        return forWorld(world).find(block);
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
        return forWorld(world).isMutexZoneNearby(block, radius);
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
        return forWorld(world).findNearbyZones(block, radius);
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
            slotsList.add(slot);
        } else {
            slot = slotsByName.computeIfAbsent(name, n -> {
                MutexZoneSlot newSlot = new MutexZoneSlot(n);
                slotsList.add(newSlot);
                return newSlot;
            });
        }
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
        if (!slotsList.isEmpty()) {
            for (int i = 0; i < slotsList.size(); i++) {
                slotsList.get(i).onTick();
            }
        }
    }
}
