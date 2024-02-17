package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.AsyncTask;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.TempFileOutputStream;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
                OfflineGroupFileFormatModern.Data data = OfflineGroupFileFormatModern.readAll(stream);
                manager.load(data.worlds);
                MutexZoneCache.loadState(manager.getTrainCarts(), data.root);
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

        // Modern Data
        final OfflineGroupFileFormatModern.Data data = new OfflineGroupFileFormatModern.Data(worlds);

        // Save all mutex slot states
        MutexZoneCache.saveState(manager.getTrainCarts(), data.root);

        // Make sure this class is loaded up-front in case of a frozen save
        StreamUtil.toUnmodifiableList();

        // Then in an asynchronous task write all data to disk. Use a TempFileOutputStream
        // so an interrupted write won't corrupt the file.
        currentSaveOperation = CommonUtil.runCheckedAsync(() -> {
            try (TempFileOutputStream fileStream = new TempFileOutputStream(dataFile);
                 DataOutputStream stream = new DataOutputStream(fileStream)
            ) {
                try {
                    OfflineGroupFileFormatModern.writeAll(stream, data);
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

    private boolean waitForSaveCompletion() {
        try {
            currentSaveOperation.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            manager.getTrainCarts().log(Level.SEVERE, "Failed to save group data on plugin shutdown: save timed out");
            return false;
        } catch (Throwable t) { /* already logged */ }

        return true;
    }
}
