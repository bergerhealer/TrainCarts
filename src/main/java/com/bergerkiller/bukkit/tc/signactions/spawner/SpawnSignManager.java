package com.bergerkiller.bukkit.tc.signactions.spawner;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;

/**
 * Tracks all the spawn signs globally on the server, and tracks the regular
 * interval of spawning trains at them. Every tick new chunks are routinely loaded
 * asynchronously prior to new spawns to minimize performance problems.
 */
public class SpawnSignManager {
    public static final long SPAWN_WARMUP_TIME = 10000; // give 10 seconds time to load chunks
    public static final long SPAWN_LOAD_DEBOUNCE = 30000; // Keep the area around a spawn sign loaded for at most 30s
    private final TrainCarts plugin;
    private final UpdateTask updateTask;
    private final Map<OfflineBlock, SpawnSign> signs = new HashMap<OfflineBlock, SpawnSign>();
    private List<SpawnSign> cachedSortedSigns = null; // when null, is re-sorted

    public SpawnSignManager(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new UpdateTask(plugin);
    }

    public void load() {
        this.plugin.getOfflineSigns().registerHandler(SpawnSignMetadata.class, new OfflineSignMetadataHandler<SpawnSignMetadata>() {
            @Override
            public void onUpdated(OfflineSignStore store, OfflineSign sign, SpawnSignMetadata oldValue, SpawnSignMetadata newValue) {
                SpawnSign spawnSign = signs.get(sign.getBlock());
                if (spawnSign != null) {
                    spawnSign.updateState(sign, newValue);
                    notifyChanged();
                }
            }

            @Override
            public void onAdded(OfflineSignStore store, OfflineSign sign, SpawnSignMetadata metadata) {
                SpawnSign newSpawnSign = new SpawnSign(store, sign, metadata);
                signs.put(sign.getBlock(), newSpawnSign);
                notifyChanged();
            }

            @Override
            public void onRemoved(OfflineSignStore store, OfflineSign sign, SpawnSignMetadata metadata) {
                SpawnSign removedSign = signs.remove(sign.getBlock());
                if (removedSign != null) {
                    removedSign.loadChunksAsyncReset();
                }
                notifyChanged();
            }

            @Override
            public SpawnSignMetadata onSignChanged(OfflineSignStore store, OfflineSign oldSign, OfflineSign newSign, SpawnSignMetadata metadata) {
                // If first line changes, too complicated to handle here
                if (!oldSign.getLine(0).equals(newSign.getLine(0))) {
                    return null;
                }

                // Check changes of the second line (spawn timing)
                if (!oldSign.getLine(1).equals(newSign.getLine(1))) {
                    if (!newSign.getLine(1).toLowerCase(Locale.ENGLISH).startsWith("spawn")) {
                        return null;
                    }

                    SpawnSign.SpawnOptions options = SpawnSign.SpawnOptions.fromOfflineSign(newSign);
                    if (metadata.intervalMillis != options.autoSpawnInterval) {
                        metadata = metadata.setInterval(options.autoSpawnInterval);
                    }
                }

                // If the last two lines change, that's fine. We want to refresh those.
                return metadata;
            }

            @Override
            public void onEncode(DataOutputStream stream, OfflineSign sign, SpawnSignMetadata value) throws IOException {
                stream.writeBoolean(value.active);
                stream.writeLong(value.intervalMillis);
                stream.writeLong(value.autoSpawnStartTime);
            }

            @Override
            public SpawnSignMetadata onDecode(DataInputStream stream, OfflineSign sign) throws IOException {
                boolean active = stream.readBoolean();
                long intervalMillis = stream.readLong();
                long autoSpawnStartTime = stream.readLong();
                return new SpawnSignMetadata(intervalMillis, autoSpawnStartTime, active);
            }
        });
    }

    public void enable() {
        this.updateTask.start(1, 1);
    }

    public void disable() {
        this.plugin.getOfflineSigns().unregisterHandler(SpawnSignMetadata.class);
        this.updateTask.stop();
        this.clear(); // For good measure, but will already be cleaned up
    }

    public void clear() {
        for (SpawnSign old_sign : this.signs.values()) {
            old_sign.loadChunksAsyncReset();
        }
        this.signs.clear();
        this.cachedSortedSigns = null;
    }

    /**
     * Gets the SpawnSign that exists at a specified sign block, if one exists there
     *
     * @param signBlock Block of the spawn sign
     * @return SpawnSign instance, or null if there is no spawn sign here
     */
    public SpawnSign get(Block signBlock) {
        return this.signs.get(OfflineBlock.of(signBlock));
    }

    public SpawnSign create(SignActionEvent signEvent) {
        OfflineBlock position = OfflineBlock.of(signEvent.getBlock());
        SpawnSign result = this.signs.get(position);
        if (result != null) {
            result.updateUsingEvent(signEvent);
        } else if (signEvent.getTrackedSign().isRealSign()) {
            // Install new metadata for this sign
            SpawnSign.SpawnOptions options = SpawnSign.SpawnOptions.fromEvent(signEvent);
            SpawnSignMetadata metadata = new SpawnSignMetadata(
                    /*  interval  */ options.autoSpawnInterval,
                    /* last spawn */ System.currentTimeMillis() + options.autoSpawnInterval,
                    /*   active   */ signEvent.isPowered());

            // Put metadata, which will also put an entry in the signs mapping
            this.plugin.getOfflineSigns().put(signEvent.getSign(), metadata);
            result = this.signs.get(position);
            if (result == null) {
                throw new IllegalStateException("No SpawnSign was put, onAdded() not called");
            }
        } else {
            // Fake it, don't store it.
            SpawnSign.SpawnOptions options = SpawnSign.SpawnOptions.fromEvent(signEvent);
            SpawnSignMetadata metadata = new SpawnSignMetadata(
                    /*  interval  */ options.autoSpawnInterval,
                    /* last spawn */ System.currentTimeMillis() + options.autoSpawnInterval,
                    /*   active   */ signEvent.isPowered());
            result = new SpawnSign(null, OfflineSign.fromSign(signEvent.getSign()), metadata);
        }
        return result;
    }

    public void remove(SignActionEvent signEvent) {
        this.plugin.getOfflineSigns().remove(signEvent.getBlock(), SpawnSignMetadata.class);
    }

    public void remove(SpawnSign sign) {
        this.plugin.getOfflineSigns().remove(sign.getLocation(), SpawnSignMetadata.class);
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

            // Doesn't appear to be needed anymore.
            /*
            Collections.sort(this.cachedSortedSigns, new Comparator<SpawnSign>() {
                @Override
                public int compare(SpawnSign s1, SpawnSign s2) {
                    return Long.compare(s1.getNextSpawnTime(), s2.getNextSpawnTime());
                }
            });
            */
        }
        return this.cachedSortedSigns;
    }

    /**
     * Tells the manager that a spawn sign has changed prompting re-saving and
     * resetting of the sorted list.
     */
    public void notifyChanged() {
        this.cachedSortedSigns = null;
    }

    private class UpdateTask extends Task {
        private long previousTime = Long.MAX_VALUE;

        public UpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            if (previousTime != Long.MAX_VALUE) {
                for (SpawnSign pending : getSigns()) {
                    long remainingMillis = pending.getRemaining(previousTime, currentTime);
                    if (remainingMillis > SPAWN_LOAD_DEBOUNCE) {
                        pending.loadChunksAsyncResetAuto();
                    } else if (remainingMillis == 0) {
                        pending.spawn();
                    } else if (remainingMillis <= SPAWN_WARMUP_TIME) {
                        // Warmup! How many chunks are loaded versus should be loaded by now?
                        pending.loadChunksAsync(1.0 - ((double) (remainingMillis-1000) / (double) SPAWN_WARMUP_TIME));
                    }
                }
            }
            previousTime = currentTime;
        }

    }

    public static final class SpawnSignMetadata {
        public final long intervalMillis;
        public final long autoSpawnStartTime;
        public final boolean active;

        public SpawnSignMetadata(long intervalMillis, long autoSpawnStartTime, boolean active) {
            this.intervalMillis = intervalMillis;
            this.autoSpawnStartTime = autoSpawnStartTime;
            this.active = active;
        }

        public SpawnSignMetadata setAutoSpawnStart(long timestamp) {
            return new SpawnSignMetadata(this.intervalMillis, timestamp, this.active);
        }

        public SpawnSignMetadata setActive(boolean active) {
            return new SpawnSignMetadata(this.intervalMillis, this.autoSpawnStartTime, active);
        }

        public SpawnSignMetadata setInterval(long intervalMillis) {
            return new SpawnSignMetadata(intervalMillis, this.autoSpawnStartTime, this.active);
        }
    }
}
