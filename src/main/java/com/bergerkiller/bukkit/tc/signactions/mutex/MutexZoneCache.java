package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorldMap;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;
import com.bergerkiller.bukkit.tc.rails.RailLookup;

public class MutexZoneCache {
    private static final OfflineWorldMap<MutexZoneCacheWorld> cachesByWorld = new OfflineWorldMap<>();
    private static final Map<String, MutexZoneSlot> slotsByName = new HashMap<>();
    private static final List<MutexZoneSlot> slotsList = new ArrayList<>();

    public static void init(TrainCarts plugin) {
        plugin.getOfflineSigns().registerHandler(MutexSignMetadata.class, new OfflineSignMetadataHandler<MutexSignMetadata>() {

            @Override
            public void onAdded(OfflineSignStore store, OfflineSign sign, MutexSignMetadata metadata) {
                addMutexSign(sign.getWorld(), sign.getPosition(), sign.isFrontText(), metadata);
            }

            @Override
            public void onRemoved(OfflineSignStore store, OfflineSign sign, MutexSignMetadata metadata) {
                removeMutexSign(sign.getWorld(), sign.getPosition(), sign.isFrontText());
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

            @Override
            public boolean isUnloadedWorldsIgnored() {
                // This is important for loading in mutex zone slot metadata. All this information MUST be loaded!
                // Otherwise, if at startup a particular world isn't loaded, state data gets lost.
                return false;
            }
        });
    }

    /**
     * Saves all trains that have entered mutex zone slots at this time, encoding them as
     * a DataBlock which can later be loaded again using {@link #loadState(TrainCarts, DataBlock)}
     *
     * @param plugin TrainCarts plugin
     * @param root Root DataBlock to write the mutex zone state data to
     */
    public static void saveState(TrainCarts plugin, DataBlock root) {
        final DataBlock stateData = root.addChild("mutex-zones-state");

        // Save pathing mutexes
        for (MutexZoneCacheWorld world : cachesByWorld.values()) {
            world.byPathingKey.values().forEach(p -> p.writeTo(stateData));
        }

        // Save all mutex zone slots' entered groups
        for (MutexZoneSlot slot : slotsList) {
            if (slot.getEnteredGroups().isEmpty()) {
                continue;
            }
            if (slot.isAnonymous() && !slot.hasZones()) {
                continue; // This should never happen
            }

            // Write out the slot metadata itself
            DataBlock slotData;
            try {
                slotData = stateData.addChildOrAbort("mutex-zone-slot", stream -> {
                    // Mode:
                    // - 0 = slot mapped to a name. Only writes out name.
                    // - 1 = slot mapped to an ordinary (smart) mutex. Writes mutex details.
                    // - 2 = slot mapped to a pathing mutex. Writes pathing key details.
                    if (!slot.isAnonymous()) {
                        Util.writeVariableLengthInt(stream, 0);
                        stream.writeUTF(slot.getName());
                    } else {
                        MutexZone zone = slot.getZones().get(0);
                        if (zone instanceof MutexZonePath) {
                            Util.writeVariableLengthInt(stream, 2);

                            MutexZonePath pathMutex = (MutexZonePath) zone;
                            StreamUtil.writeUUID(stream, pathMutex.signBlock.getWorldUUID());
                            if (!pathMutex.key.writeTo(plugin, stream)) {
                                throw new DataBlock.AbortChildException();
                            }
                        } else {
                            Util.writeVariableLengthInt(stream, 1);
                            OfflineBlock.writeTo(stream, zone.signBlock);
                            stream.writeBoolean(zone.signFront);
                        }
                    }
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save mutex zone slot '" + slot.getName() + "'", t);
                continue;
            }

            // Save entered groups of this slot
            for (MutexZoneSlot.EnteredGroup group : slot.getEnteredGroups()) {
                group.unload().save(plugin, slotData);
            }
        }
    }

    /**
     * Initializes all the entered groups that were inside mutex zone slots at the time of
     * last server shutdown. Discards entered groups for mutex zone slots that don't exist.
     *
     * @param plugin TrainCarts plugin
     * @param root Root DataBlock optionally containing entered group data
     */
    public static void loadState(TrainCarts plugin, DataBlock root) {
        root.findChild("mutex-zones-state").ifPresent(stateData -> {
            // Load pathing mutexes
            for (MutexZonePath pathMutex : MutexZonePath.readAll(plugin, stateData)) {
                System.out.println("Load path mutex: " + pathMutex);
                forWorld(pathMutex.signBlock.getWorld()).add(pathMutex);
            }

            // Load mutex slots
            for (DataBlock slotData : stateData.findChildren("mutex-zone-slot")) {
                final MutexZoneSlot slot;
                try (DataInputStream stream = slotData.readData()) {
                    // Mode:
                    // - 0 = slot mapped to a name. Only writes out name.
                    // - 1 = slot mapped to an ordinary (smart) mutex. Writes mutex details.
                    // - 2 = slot mapped to a pathing mutex. Writes pathing key details.
                    int mode = Util.readVariableLengthInt(stream);
                    if (mode == 0) {
                        // By name
                        slot = slotsByName.get(stream.readUTF());
                        if (slot == null) {
                            continue;
                        }
                    } else if (mode == 1) {
                        // Anonymous normal (Smart) Mutex at a sign
                        OfflineBlock mutexSignBlock = OfflineBlock.readFrom(stream);
                        boolean mutexSignFront = stream.readBoolean();
                        MutexZoneCacheWorld cacheWorld = cachesByWorld.get(mutexSignBlock.getWorld());
                        if (cacheWorld == null) {
                            continue; // No mutex zones?
                        }
                        MutexZone zone = cacheWorld.findBySign(mutexSignBlock.getPosition(), mutexSignFront);
                        if (zone == null) {
                            continue; // Removed?
                        }
                        slot = zone.slot;
                    } else if (mode == 2) {
                        // Anonymous pathing mutex for a single train at a pathing mutex sign
                        OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
                        MutexZoneCacheWorld.PathingSignKey key = MutexZoneCacheWorld.PathingSignKey.readFrom(plugin, stream);
                        MutexZonePath pathMutex = forWorld(world).byPathingKey.get(key);
                        if (pathMutex == null) {
                            continue; // Removed?
                        }
                        slot = pathMutex.slot;
                    } else {
                        // Unknown
                        continue;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to read data of mutex zone slot", t);
                    continue;
                }

                // Initialize the entered groups of this slot
                slot.getEnteredGroups().clear();
                slot.getEnteredGroups().addAll(MutexZoneSlot.UnloadedEnteredGroup.loadAll(plugin, slotData));
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

    public static MutexZonePath getOrCreatePathingMutex(
            RailLookup.TrackedSign sign,
            MinecartGroup group,
            IntVector3 initialBlock,
            UnaryOperator<MutexZonePath.OptionsBuilder> optionsBuilder
    ) {
        return forWorld(OfflineWorld.of(sign.sign.getWorld()))
                .getOrCreatePathingMutex(sign, group, initialBlock, optionsBuilder);
    }

    private static void addMutexSign(OfflineWorld world, IntVector3 signPosition, boolean isFrontText, MutexSignMetadata metadata) {
        forWorld(world).add(MutexZone.createCuboid(world, signPosition, isFrontText, metadata));
    }

    private static void removeMutexSign(OfflineWorld world, IntVector3 signPosition, boolean frontText) {
        // This causes pain & suffering (chunk unload event - accessing block data doesn't work)
        // zones.remove(info.getWorld(), MutexZone.getPosition(info));

        // Instead, a slow way
        MutexZone zone = forWorld(world).removeAtSign(signPosition, frontText);
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
     * Adds all mutex zones nearby a position. See {@link #isMutexZoneNearby(OfflineWorld, IntVector3, int)}
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
     * @param name name of the slot, empty to create a new anonymous slot
     * @param zone
     * @return slot
     */
    public static synchronized MutexZoneSlot findSlot(String name, MutexZone zone) {
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

    private static synchronized void removeMutexZone(MutexZone zone) {
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
    public static synchronized void refreshAll() {
        // Note: done by index on purpose to avoid concurrent modification exceptions
        // They may occur if a zone loads/unloads as a result of a lever toggle/etc.
        if (!slotsList.isEmpty()) {
            for (int i = 0; i < slotsList.size(); i++) {
                slotsList.get(i).onTick();
            }
        }

        // Remove expired pathing zones
        cachesByWorld.values().forEach(MutexZoneCacheWorld::onTick);
    }

    /**
     * Calls {@link MutexZoneSlot#unload(MinecartGroup)} for all slots
     *
     * @param group MinecartGroup that unloaded
     */
    public static synchronized void unloadGroupInSlots(MinecartGroup group) {
        slotsList.forEach(s -> s.unload(group));
    }
}
