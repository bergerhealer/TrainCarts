package com.bergerkiller.bukkit.tc.offline.sign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.signactions.detector.DetectorSign;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSignManager;

/**
 * Imports offline sign data from legacy formats that used to
 * be stored by TrainCarts in separate files. Since the new system
 * requires the line content of the signs to be known, this importer
 * is dynamic. Once the world where saved data is stored is loaded,
 * the required chunks are loaded and sign details read. After that,
 * the legacy data is discarded.
 */
class OfflineSignLegacyImporter {
    private final OfflineSignStore store;
    private final TrainCarts plugin;
    private final Map<String, WorldLegacyData> byWorldName = new HashMap<>();
    private final File spawnSignsFile;
    private final File detectorSignsFile;

    public OfflineSignLegacyImporter(OfflineSignStore store, TrainCarts plugin) {
        this.store = store;
        this.plugin = plugin;
        this.spawnSignsFile = plugin.getDataFile("spawnsigns.dat");
        this.detectorSignsFile = plugin.getDataFile("detectorsigns.dat");
    }

    public void enable() {
        // Load metadata from disk
        load();

        // For all worlds already loaded, import legacy sign data we have sitting around
        {
            boolean imported = false;
            for (World world : Bukkit.getWorlds()) {
                WorldLegacyData legacyData = byWorldName.remove(world.getName());
                if (legacyData != null) {
                    if (!imported) {
                        imported = true;
                        this.plugin.getLogger().log(Level.WARNING, "Importing legacy sign metadata...");
                    }
                    legacyData.importData(this.store, world);
                }
            }
            if (imported) {
                this.plugin.getLogger().log(Level.WARNING, "Legacy sign metadata imported!");
                save();
            }
        }

        // If there is still more metadata to convert, install a world load listener for this
        // This will perform this migration automatically once a world is loaded
        if (!byWorldName.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onWorldLoad(WorldLoadEvent event) {
                    WorldLegacyData legacyData = byWorldName.remove(event.getWorld().getName());
                    if (legacyData != null) {
                        plugin.getLogger().log(Level.WARNING, "Importing legacy sign metadata for world " +
                                event.getWorld().getName() + "...");
                        legacyData.importData(store, event.getWorld());
                        save();
                        plugin.getLogger().log(Level.WARNING, "Legacy sign metadata for world " + 
                                event.getWorld().getName() + " imported!");
                    }
                }
            }, plugin);
        }
    }

    /**
     * Loads legacy sign metadata from disk
     */
    private void load() {
        if (this.spawnSignsFile.exists()) {
            // Spawn signs and their last-spawn counter logic
            new DataReader(this.spawnSignsFile) {
                public void read(DataInputStream stream) throws IOException {
                    int count = stream.readInt();
                    for (; count > 0; --count) {
                        LegacySpawnSignData spawnSign = LegacySpawnSignData.read(stream);
                        dataOnWorld(spawnSign.signWorldName).spawnSigns.add(spawnSign);
                    }
                }
            }.read();
        }
        if (this.detectorSignsFile.exists()) {
            // Detector sign pairs. Regions are not bound to signs, and are
            // therefore stored in it's own dedicated file. (detectorregions.dat)
            // The region is tied to the pair by UUID
            new DataReader(this.detectorSignsFile) {
                public void read(DataInputStream stream) throws IOException {
                    for (int count = stream.readInt(); count > 0; --count) {
                        LegacyDetectorSignPairData pair = LegacyDetectorSignPairData.read(stream);
                        DetectorRegion region = DetectorRegion.getRegion(pair.detectorRegionUUID);
                        if (region != null) {
                            dataOnWorld(region.getWorldName()).detectorSignPairs.add(pair);
                        }
                    }
                }
            }.read();
        }
    }

    /**
     * (Re-)saves all legacy data in the original legacy format,
     * if any legacy data is still stored.
     */
    private void save() {
        if (byWorldName.isEmpty()) {
            // Delete all legacy files, if any exist
            this.spawnSignsFile.delete();
            this.detectorSignsFile.delete();
        } else {
            // Save updated legacy files with the migrated worlds omitted

            // Save spawn signs file
            new DataWriter(this.spawnSignsFile) {
                @Override
                public void write(DataOutputStream stream) throws IOException {
                    List<LegacySpawnSignData> spawnSigns = byWorldName.values().stream()
                            .flatMap(data -> data.spawnSigns.stream())
                            .collect(Collectors.toList());

                    stream.writeInt(spawnSigns.size());
                    for (LegacySpawnSignData spawnSign : spawnSigns) {
                        spawnSign.write(stream);
                    }
                }
            }.write();

            // Save detector signs file
            new DataWriter(this.detectorSignsFile) {
                @Override
                public void write(DataOutputStream stream) throws IOException {
                    List<LegacyDetectorSignPairData> detectorSignPairs = byWorldName.values().stream()
                            .flatMap(data -> data.detectorSignPairs.stream())
                            .collect(Collectors.toList());

                    stream.writeInt(detectorSignPairs.size());
                    for (LegacyDetectorSignPairData detectorSignPair : detectorSignPairs) {
                        detectorSignPair.write(stream);
                    }
                }
            }.write();
        }
    }

    private WorldLegacyData dataOnWorld(String worldName) {
        return byWorldName.computeIfAbsent(worldName, name -> new WorldLegacyData());
    }

    private static class WorldLegacyData {
        public final List<LegacySpawnSignData> spawnSigns = new ArrayList<>();
        public final List<LegacyDetectorSignPairData> detectorSignPairs = new ArrayList<>();

        public void importData(OfflineSignStore store, World world) {
            // Import spawn signs
            for (LegacySpawnSignData spawnSign : spawnSigns) {
                // Find the spawn sign
                Sign sign = findSign(world, spawnSign.signLocation, "spawn");
                if (sign == null) {
                    continue; // Sign doesn't exist. Skip.
                }

                // Install the metadata for this sign
                // The handler will automatically initialize the SpawnSign logic itself
                SpawnSignManager.SpawnSignMetadata metadata = new SpawnSignManager.SpawnSignMetadata(
                        spawnSign.interval,
                        System.currentTimeMillis() + spawnSign.remaining - spawnSign.interval,
                        spawnSign.active);
                store.put(sign, metadata);
            }

            // Import detector signs
            for (LegacyDetectorSignPairData detectorSignPair : detectorSignPairs) {
                // Find the detector signs
                Sign sign1 = findSign(world, detectorSignPair.sign1Location, "detector");
                Sign sign2 = findSign(world, detectorSignPair.sign2Location, "detector");
                if (sign1 == null || sign2 == null) {
                    // Sign doesn't exist. Skip. Do clean up the detector region if it exists.
                    DetectorRegion region = DetectorRegion.getRegion(detectorSignPair.detectorRegionUUID);
                    if (region != null && !region.isRegistered()) {
                        region.remove();
                    }
                    continue;
                }

                // Check region even exists
                DetectorRegion region = DetectorRegion.getRegion(detectorSignPair.detectorRegionUUID);
                if (region == null) {
                    continue;
                }

                // Install the metadata for these signs
                // The handler will automatically initialize the DetectorSign logic itself
                OfflineBlock sign1Block = OfflineWorld.of(world).getBlockAt(detectorSignPair.sign1Location);
                OfflineBlock sign2Block = OfflineWorld.of(world).getBlockAt(detectorSignPair.sign2Location);
                store.put(sign1, new DetectorSign.Metadata(sign2Block, region, detectorSignPair.sign1LeverDown));
                store.put(sign2, new DetectorSign.Metadata(sign1Block, region, detectorSignPair.sign2LeverDown));
            }
        }

        private Sign findSign(World world, IntVector3 signLocation, String type) {
            // Actively (sync-) load the chunk this sign is supposedly at
            Block signBlock = signLocation.toBlock(world);
            world.getChunkAt(MathUtil.toChunk(signBlock.getX()), MathUtil.toChunk(signBlock.getZ()));
            Sign sign = BlockUtil.getSign(signBlock);
            if (sign != null && sign.getLine(1).toLowerCase(Locale.ENGLISH).trim().startsWith(type)) {
                return sign;
            }
            return null;
        }
    }

    /**
     * Metadata stored for a single (auto-)spawn sign
     */
    private static class LegacySpawnSignData {
        public final IntVector3 signLocation;
        public final String signWorldName;
        public final long interval;
        public final long remaining;
        public final boolean active;

        public static LegacySpawnSignData read(DataInputStream stream) throws IOException {
            return new LegacySpawnSignData(stream);
        }

        private LegacySpawnSignData(DataInputStream stream) throws IOException {
            this.signLocation = IntVector3.read(stream);
            this.signWorldName = stream.readUTF();
            this.interval = stream.readLong();
            long remainingVal = stream.readLong();
            if (remainingVal == Long.MAX_VALUE) {
                this.remaining = 0;
                this.active = false;
            } else {
                this.remaining = remainingVal;
                this.active = true;
            }
        }

        public void write(DataOutputStream stream) throws IOException {
            this.signLocation.write(stream);
            stream.writeUTF(this.signWorldName);
            stream.writeLong(this.interval);
            if (this.active) {
                stream.writeLong(this.remaining);
            } else {
                stream.writeLong(Long.MAX_VALUE);
            }
        }
    }

    /**
     * Metadata stored for a single detector sign pair
     */
    private static class LegacyDetectorSignPairData {
        public final IntVector3 sign1Location;
        public final IntVector3 sign2Location;
        public final boolean sign1LeverDown;
        public final boolean sign2LeverDown;
        public final UUID detectorRegionUUID;

        public static LegacyDetectorSignPairData read(DataInputStream stream) throws IOException {
            return new LegacyDetectorSignPairData(stream);
        }

        private LegacyDetectorSignPairData(DataInputStream stream) throws IOException {
            this.detectorRegionUUID = StreamUtil.readUUID(stream);
            this.sign1Location = IntVector3.read(stream);
            this.sign2Location = IntVector3.read(stream);
            this.sign1LeverDown = stream.readBoolean();
            this.sign2LeverDown = stream.readBoolean();
        }

        public void write(DataOutputStream stream) throws IOException {
            StreamUtil.writeUUID(stream, this.detectorRegionUUID);
            this.sign1Location.write(stream);
            this.sign2Location.write(stream);
            stream.writeBoolean(this.sign1LeverDown);
            stream.writeBoolean(this.sign2LeverDown);
        }
    }
}
