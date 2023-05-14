package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.TickTracker;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.mountiplex.reflection.SafeMethod;

/**
 * Stores the chunks kept loaded to track obstacles up ahead.
 * Is used by the track walking point to keep previously walked paths loaded,
 * reducing the chunk load/unload jitter that it can otherwise cause.
 */
public class ForwardChunkArea {
    private final TickTracker beginTickTracker;
    private World world;
    private final LongHashMap<Entry> entries;
    private final List<Entry> entriesList;
    private Entry lastEntry;
    private boolean state;

    public ForwardChunkArea() {
        this.world = null;
        this.entries = new LongHashMap<>();
        this.entriesList = new ArrayList<>();
        this.lastEntry = null;
        this.state = false;
        this.beginTickTracker = new TickTracker();
        this.beginTickTracker.setRunnable(() -> {
            // Wipe previous entries whose state mismatches, which indicates it hasn't been add()-ed
            boolean expectedState = state;
            if (lastEntry != null && lastEntry.state != expectedState) {
                lastEntry = null; // Make sure this is invalidated, we remove this later
            }
            for (Iterator<Entry> iter = entriesList.iterator(); iter.hasNext();) {
                Entry e = iter.next();
                if (e.state != expectedState) {
                    iter.remove();
                    entries.remove(e.key);
                    e.chunk.close();
                }
            }

            // Flip state
            state = !expectedState;
        });
    }

    /**
     * Must be called at the beginning of each new track iteration call, ideally
     * at the start of the tick. Afterwards, {@link #add(int, int)} can be
     * called to keep all the chunks that need to stay loaded.
     */
    public void begin(World world) {
        // Only run this once a tick at most. This makes sure that when the
        // forward path prediction runs multiple times (maybe some add-on calls it),
        // it doesn't repeatedly create and clear a chunk area. Instead, it will
        // combine the add() that occur in both cleanly.
        this.beginTickTracker.update();

        // When changing world, reset chunk area completely
        if (this.world != world) {
            reset();
            this.world = world;
        }
    }

    /**
     * Releases all chunks kept loaded. Must be called when the train dies/unloads/plugin shutdown
     */
    public void reset() {
        if (!entriesList.isEmpty()) {
            for (Entry e : entriesList) {
                e.chunk.close();
            }
            entries.clear();
            entriesList.clear();
            lastEntry = null;
        }
    }

    public void addBlock(Block block) {
        add(block.getX() >> 4, block.getZ() >> 4);
    }

    public void add(int cx, int cz) {
        // Track new chunk
        long key = MathUtil.longHashToLong(cx, cz);
        Entry e = lastEntry;
        if (e == null || e.key != key) {
            e = entries.computeIfAbsent(key, k -> {
                Entry newEntry = new Entry(FORCE_LOADED_FUNC.forceLoaded(world, cx, cz), k, false);
                entriesList.add(newEntry);
                return newEntry;
            });
            lastEntry = e;
        }
        e.state = state;
    }

    private static final class Entry {
        public final ForcedChunk chunk;
        public final long key;
        public boolean state;

        public Entry(ForcedChunk chunk, long key, boolean state) {
            this.chunk = chunk;
            this.key = key;
            this.state = state;
        }
    }

    @FunctionalInterface
    private static interface ForceLoadedFunc {
        ForcedChunk forceLoaded(World world, int cx, int cz);
    }

    // Copied from LightCleaner. Can remove once we depend on BKCL 1.19.2-v3 or newer
    private static final ForceLoadedFunc FORCE_LOADED_FUNC;
    static {
        if (SafeMethod.contains(ForcedChunk.class, "load", World.class, int.class, int.class, int.class)) {
            // Use a radius of 1 so it only loads this one chunk and its direct neighbours
            // The neighbours are important for signs and stuff
            FORCE_LOADED_FUNC = (w, cx, cz) -> ForcedChunk.load(w, cx, cz, 1);
        } else {
            // Fallback for older bkcl: used default radius of 2
            FORCE_LOADED_FUNC = WorldUtil::forceChunkLoaded;
        }
    }
}
