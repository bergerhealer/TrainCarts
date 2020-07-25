package com.bergerkiller.bukkit.tc.signactions.spawner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

/**
 * Tracks all the spawn signs globally on the server, and tracks the regular
 * interval of spawning trains at them. Every tick new chunks are routinely loaded
 * asynchronously prior to new spawns to minimize performance problems.
 */
public class SpawnSignManager {
    public static final long SPAWN_WARMUP_TIME = 10000; // give 10 seconds time to load chunks
    public static final long SPAWN_LOAD_DEBOUNCE = 30000; // Keep the area around a spawn sign loaded for at most 30s
    private final UpdateTask updateTask;
    private final BlockMap<SpawnSign> signs = new BlockMap<SpawnSign>();
    private List<SpawnSign> cachedSortedSigns = null; // when null, is re-sorted
    private boolean hasChanges = false; // for autosave

    public SpawnSignManager(TrainCarts plugin) {
        this.updateTask = new UpdateTask(plugin);
    }

    public void init() {
        updateTask.start(1, 1);
    }

    public void deinit() {
        updateTask.stop();
        this.clear();
    }

    public void load(String filename) {
        // Load spawn signs from file
        this.clear();
        new DataReader(filename) {
            public void read(DataInputStream stream) throws IOException {
                int count = stream.readInt();
                for (; count > 0; --count) {
                    SpawnSign sign = SpawnSign.read(stream);
                    SpawnSignManager.this.signs.put(sign.getLocation(), sign);
                }
            }
        }.read();
        hasChanges = false;
    }

    public void save(boolean autosave, String filename) {
        // Save spawn signs to file
        if (autosave && !hasChanges) {
            return;
        }
        new DataWriter(filename) {
            public void write(DataOutputStream stream) throws IOException {
                stream.writeInt(SpawnSignManager.this.signs.size());
                for (SpawnSign sign : SpawnSignManager.this.signs.values()) {
                    sign.write(stream);
                }
            }
        }.write();
        hasChanges = false;
    }

    public void clear() {
        for (SpawnSign old_sign : this.signs.values()) {
            old_sign.loadChunksAsyncReset();
        }
        this.signs.clear();
        this.cachedSortedSigns = null;
    }

    public SpawnSign create(SignActionEvent signEvent) {
        SpawnSign result = this.signs.get(signEvent.getBlock());
        if (result == null) {
            result = new SpawnSign(new BlockLocation(signEvent.getBlock()));
            this.signs.put(result.getLocation(), result);
        }
        result.load(signEvent);
        this.notifyChanged();
        return result;
    }

    public void remove(SignActionEvent signEvent) {
        SpawnSign removed = this.signs.remove(signEvent.getBlock());
        if (removed != null) {
            removed.loadChunksAsyncReset();
            this.notifyChanged();
        }
    }

    public void remove(SpawnSign sign) {
        SpawnSign removed = this.signs.remove(sign.getLocation());
        if (removed != null) {
            removed.loadChunksAsyncReset();
            this.notifyChanged();
        }
    }

    /**
     * Gets a list of spawn signs, sorted with the spawn sign that is soonest
     * to spawn at the beginning of the list.
     * 
     * @return sorted spawn signs
     */
    public List<SpawnSign> getSigns() {
        if (this.cachedSortedSigns == null) {
            this.cachedSortedSigns = new ArrayList<SpawnSign>(this.signs.values());
            Collections.sort(this.cachedSortedSigns, new Comparator<SpawnSign>() {
                @Override
                public int compare(SpawnSign s1, SpawnSign s2) {
                    return Long.compare(s1.getNextSpawnTime(), s2.getNextSpawnTime());
                }
            });
        }
        return this.cachedSortedSigns;
    }

    /**
     * Tells the manager that a spawn sign has changed prompting re-saving and
     * resetting of the sorted list.
     */
    public void notifyChanged() {
        this.hasChanges = true;
        this.cachedSortedSigns = null;
    }

    private class UpdateTask extends Task {

        public UpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            long time = System.currentTimeMillis();
            for (SpawnSign pending : getSigns()) {
                long remainingMillis = pending.getRemaining(time);
                if (remainingMillis > SPAWN_LOAD_DEBOUNCE) {
                    pending.loadChunksAsyncResetAuto();
                } else if (remainingMillis == 0) {
                    pending.spawn();
                    pending.nextSpawnTime();
                } else if (remainingMillis <= SPAWN_WARMUP_TIME) {
                    // Warmup! How many chunks are loaded versus should be loaded by now?
                    pending.loadChunksAsync(1.0 - ((double) (remainingMillis-1000) / (double) SPAWN_WARMUP_TIME));
                }
            }
        }

    }
}
