package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.AsyncTask;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.TempFileOutputStream;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Handles the loading and (periodic) saving of the offline group mapping.
 */
class OfflineGroupFileHandler {
    private final OfflineGroupManager manager;
    private final File dataFile;
    private CompletableFuture<Void> currentSaveOperation = CompletableFuture.completedFuture(null);

    public OfflineGroupFileHandler(OfflineGroupManager manager) {
        this.manager = manager;
        this.dataFile = manager.getTrainCarts().getDataFile("trains.groupdata");
    }

    public void load() {
        new DataReader(dataFile) {
            @Override
            public void read(DataInputStream stream) throws IOException {
                List<OfflineGroupWorld> worlds = readAllGroups(stream);
                manager.load(worlds);
            }
        }.read();
    }

    public void save(TrainCarts.SaveMode saveMode) {
        // Wait for previous auto-save to complete
        if (!currentSaveOperation.isDone()) {
            if (saveMode != TrainCarts.SaveMode.SHUTDOWN) {
                return; // Skip saving this time, previous auto-save is still going on for some reason
            } else if (!waitForSaveCompletion()) {
                return; // Auto-save got stuck
            }
        }

        // On the main thread, collect all OfflineGroups and OfflineMembers of these groups
        // at this current time for all worlds. This is information that won't change
        // asynchronously.
        // During auto-save also save all currently loaded trains. In case the server crashes
        // there is at least a 'chance' of these trains being recovered
        final List<OfflineGroupWorld> worlds;
        if (saveMode == TrainCarts.SaveMode.SHUTDOWN) {
            worlds = manager.createSnapshot();
        } else {
            worlds = OfflineGroupWorld.mergeSnapshots(manager.createSnapshot(),
                                                      OfflineGroupManager.saveAllGroups());
        }

        // Then in an asynchronous task write all data to disk. Use a TempFileOutputStream
        // so an interrupted write won't corrupt the file.
        currentSaveOperation = CommonUtil.runCheckedAsync(() -> {
            try (TempFileOutputStream fileStream = new TempFileOutputStream(dataFile);
                 DataOutputStream stream = new DataOutputStream(fileStream)
            ) {
                try {
                    writeAllGroups(worlds, stream);
                } catch (Throwable t) {
                    fileStream.close(false);
                    throw t;
                }
            }
        }, runnable -> {
            AsyncTask task = new AsyncTask("TrainCarts-OfflineGroupSaver") {
                @Override
                public void run() {
                    runnable.run();
                }
            };
            task.start();
        }).exceptionally(t -> {
            manager.getTrainCarts().getLogger().log(Level.SEVERE, "Failed to save offline group data to disk", t);
            return null;
        });

        // If not auto-saving, wait for saving to complete
        if (saveMode == TrainCarts.SaveMode.SHUTDOWN) {
            waitForSaveCompletion();
        }
    }

    private List<OfflineGroupWorld> readAllGroups(DataInputStream stream) throws IOException {
        List<OfflineGroupWorld> worlds = new ArrayList<>();
        final int worldcount = stream.readInt();
        for (int worldIdx = 0; worldIdx < worldcount; worldIdx++) {
            OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
            final int groupcount = stream.readInt();

            // Read all the groups contained
            List<OfflineGroup> groups = new ArrayList<>(groupcount);
            for (int groupIdx = 0; groupIdx < groupcount; groupIdx++) {
                groups.add(readLegacyGroup(stream, world));
            }

            // Done with world
            worlds.add(OfflineGroupWorld.snapshot(world, groups));
        }
        return worlds;
    }

    private void writeAllGroups(List<OfflineGroupWorld> worlds, DataOutputStream stream) throws IOException {
        // Write it in legacy format
        stream.writeInt(worlds.size());
        for (OfflineGroupWorld world : worlds) {
            StreamUtil.writeUUID(stream, world.getWorld().getUniqueId());
            stream.writeInt(world.totalGroupCount());
            for (OfflineGroup wg : world) {
                wg.writeTo(stream);
            }
        }
    }

    private boolean waitForSaveCompletion() {
        try {
            currentSaveOperation.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            manager.getTrainCarts().log(Level.SEVERE, "Failed to save group data on plugin shutdown: save timed out");
            return false;
        } catch (Throwable t) { /* already logged */ }

        return true;
    }

    private static OfflineGroup readLegacyGroup(DataInputStream stream, OfflineWorld world) throws IOException {
        LegacyOfflineMemberData[] members = new LegacyOfflineMemberData[stream.readInt()];
        for (int i = 0; i < members.length; i++) {
            members[i] = LegacyOfflineMemberData.read(stream);
        }
        String name = stream.readUTF();

        return new OfflineGroup(name, world, Arrays.asList(members),
                (offlineGroup, legacyMember) -> legacyMember.toOfflineMember(offlineGroup));
    }

    public static class LegacyOfflineMemberData {
        public final UUID entityUID;
        public final int cx, cz;
        public final double motX, motZ;

        public static LegacyOfflineMemberData read(DataInputStream stream) throws IOException {
            return new LegacyOfflineMemberData(stream);
        }

        private LegacyOfflineMemberData(DataInputStream stream) throws IOException {
            entityUID = new UUID(stream.readLong(), stream.readLong());
            motX = stream.readDouble();
            motZ = stream.readDouble();
            cx = stream.readInt();
            cz = stream.readInt();
        }

        public OfflineMember toOfflineMember(OfflineGroup offlineGroup) {
            return new OfflineMember(offlineGroup,
                    entityUID, cx, cz, motX, motZ);
        }
    }
}
