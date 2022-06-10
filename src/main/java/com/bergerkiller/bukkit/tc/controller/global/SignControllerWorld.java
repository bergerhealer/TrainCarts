package com.bergerkiller.bukkit.tc.controller.global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkNeighbourList;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateListener;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateTracker;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.tc.controller.global.SignController.Entry;
import com.bergerkiller.bukkit.tc.utils.LongBlockCoordinates;

/**
 * Tracks the locations of signs and handles the redstone activation of them. Provides
 * an efficient lookup for use by the rail cache. A controller instance exists per
 * world and is tightly coupled with the rail lookup cache.
 */
public class SignControllerWorld {
    private final SignController controller;
    private final World world;
    private final OfflineWorld offlineWorld;
    private final LongHashMap<List<SignController.Entry>> signsByChunk = new LongHashMap<>();
    private final LongHashMap<SignController.Entry[]> signsByNeighbouringBlock = new LongHashMap<>();
    private final ChunkFutureProvider chunkFutureProvider;

    SignControllerWorld(SignController controller) {
        this.controller = controller;
        this.world = null;
        this.offlineWorld = OfflineWorld.NONE;
        this.chunkFutureProvider = null;
    }

    SignControllerWorld(SignController controller, World world) {
        this.controller = controller;
        this.world = world;
        this.offlineWorld = OfflineWorld.of(world);
        this.chunkFutureProvider = ChunkFutureProvider.of(controller.getPlugin());
    }

    public World getWorld() {
        return this.world;
    }

    public boolean isValid() {
        return this.offlineWorld.getLoadedWorld() == this.world;
    }

    /**
     * Gets whether this sign controller is enabled. If the World is disabled in Traincarts config,
     * this will return false to indicate no processing should occur.
     *
     * @return True if enabled, False if disabled
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * Looks up the signs that exist at, or neighbouring, the specified block.
     *
     * @param block
     * @return Entries nearby
     */
    public SignController.Entry[] findNearby(Block block) {
        return signsByNeighbouringBlock.getOrDefault(LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ()),
                SignController.Entry.NO_ENTRIES);
    }

    /**
     * Looks up the entry for a specific sign
     *
     * @param signBlock
     * @return Entry if found, null otherwise
     */
    public SignController.Entry findForSign(Block signBlock) {
        for (Entry entry : findNearby(signBlock)) {
            if (entry.sign.getBlock().equals(signBlock)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Calls a function on all signs at or neighbouring a specified block.
     * Before it calls the handler, verifies the sign still truly exists.
     *
     * @param block
     * @param handler
     */
    public void forEachNearbyVerify(Block block, Consumer<SignController.Entry> handler) {
        for (SignController.Entry entry : this.findNearby(block)) {
            entry.sign.update();
            if (entry.sign.isRemoved()) {
                this.removeInvalidEntry(entry);
            } else {
                handler.accept(entry);
            }
        }
    }

    /**
     * Starts tracking a newly placed sign. Initializes power state, but does not fire any events.
     * If the sign was already tracked, returns the existing entry instead. If the sign could not
     * be found at this block, returns null.
     *
     * @param signBlock
     * @return entry for the sign, null if not a sign
     */
    public SignController.Entry addSign(Block signBlock) {
        // Find/activate an existing sign
        SignController.Entry existing = this.findForSign(signBlock);
        if (existing != null) {
            controller.activateEntry(existing);
            return existing;
        }

        // Add a new one. Lines of text might be wiped initially.
        Sign sign = BlockUtil.getSign(signBlock);
        if (sign == null) {
            return null;
        } else {
            return createSign(sign);
        }
    }

    private SignController.Entry createSign(Sign sign) {
        // Create entry. Add it to by-chunk and by-block mapping.
        Block signBlock = sign.getBlock();
        SignController.Entry entry = this.controller.createEntry(sign,
                MathUtil.longHashToLong(MathUtil.toChunk(signBlock.getX()),
                                        MathUtil.toChunk(signBlock.getZ())));
        {
            List<SignController.Entry> atChunk = this.signsByChunk.get(entry.chunkKey);
            if (atChunk == null) {
                atChunk = new ArrayList<>();
                this.signsByChunk.put(entry.chunkKey, atChunk);
            }
            atChunk.add(entry);
        }

        this.addChunkByBlockEntry(entry);

        this.controller.activateEntry(entry);

        return entry;
    }

    /**
     * Refreshes the signs that exist in a particular chunk. This is used for the debug
     * command, and for use by external plugins that modify/place signs in weird ways.
     *
     * @param chunk Chunk
     * @return Information about the number of signs added/removed thanks to refreshing
     */
    public RefreshResult refreshInChunk(Chunk chunk) {
        long chunkKey = MathUtil.longHashToLong(chunk.getX(), chunk.getZ());

        // Verify existence of signs we already had. Remove if missing.
        int numRemoved = 0;
        {
            List<SignController.Entry> atChunk = this.signsByChunk.get(chunkKey);
            if (atChunk != null) {
                for (Iterator<SignController.Entry> iter = atChunk.iterator(); iter.hasNext();) {
                    SignController.Entry entry = iter.next();
                    entry.sign.update();
                    if (entry.sign.isRemoved()) {
                        // Remove loaded sign information
                        iter.remove();
                        this.removeFromByBlockEntry(entry);

                        // Remove from the offline signs cache as well
                        controller.getPlugin().getOfflineSigns().removeAll(entry.sign.getBlock());
                        numRemoved++;
                    }
                }
            }
        }

        // Try to add signs we didn't already have
        int numAdded = 0;
        for (BlockState blockState : this.getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                Block signBlock = blockState.getBlock();
                SignController.Entry existing = this.findForSign(signBlock);
                if (existing != null) {
                    controller.activateEntry(existing);
                    continue;
                }

                this.createSign((Sign) blockState);
                numAdded++;
            }
        }

        return new RefreshResult(numAdded, numRemoved);
    }

    /**
     * Removes all data cached/stored in this World
     */
    void clear() {
        for (List<SignController.Entry> atChunk : this.signsByChunk.values()) {
            atChunk.forEach(SignController.Entry::remove);
        }
        this.signsByChunk.clear();
        this.signsByNeighbouringBlock.clear();
    }

    /**
     * Called once a chunk and all it's 8 neighbouring chunks are loaded.
     * This should activate any previously added signs in this chunk.
     *
     * @param chunk
     */
    private void activateSignsInChunk(Chunk chunk) {
        List<SignController.Entry> entries = this.signsByChunk.get(chunk.getX(), chunk.getZ());
        if (entries != null) {
            for (Iterator<SignController.Entry> iter = entries.iterator(); iter.hasNext();) {
                SignController.Entry entry = iter.next();
                if (!entry.activated) {
                    // Check sign still truly exists, and if so, notify its activation
                    entry.sign.update();
                    if (entry.sign.isRemoved()) {
                        iter.remove();
                        this.removeFromByBlockEntry(entry);
                    } else {
                        this.controller.activateEntry(entry);
                    }
                }
            }
        }
    }

    /**
     * Called once a chunk, or one of it's 8 neighbouring chunks, unloads.
     * This should de-activate any previously activated signs.
     * It does not yet remove the signs (but this may have already happened).
     *
     * @param chunk
     */
    private void deactivateSignsInChunk(Chunk chunk) {
        List<SignController.Entry> entries = this.signsByChunk.get(chunk.getX(), chunk.getZ());
        if (entries != null) {
            for (Iterator<SignController.Entry> iter = entries.iterator(); iter.hasNext();) {
                SignController.Entry entry = iter.next();
                if (entry.activated) {
                    // Protect against NPE
                    if (entry.sign.isRemoved()) {
                        iter.remove();
                        this.removeFromByBlockEntry(entry);
                    } else {
                        this.controller.deactivateEntry(entry);
                    }
                }
            }
        }
    }

    /**
     * Adds data about signs stored in a particular chunk
     *
     * @param chunk
     */
    void loadChunk(Chunk chunk) {
        long chunkKey = MathUtil.longHashToLong(chunk.getX(), chunk.getZ());

        // Skip if already added. Might be some edge conditions during world load...
        if (this.signsByChunk.contains(chunkKey)) {
            return;
        }

        List<SignController.Entry> entriesAtChunk = null;
        for (BlockState blockState : getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                SignController.Entry entry = this.controller.createEntry((Sign) blockState, chunkKey);
                if (entriesAtChunk == null) {
                    entriesAtChunk = new ArrayList<>();
                    this.signsByChunk.put(chunkKey, entriesAtChunk);
                }
                entriesAtChunk.add(entry);
                addChunkByBlockEntry(entry);
            }
        }

        // Once all this chunk's neighbours are loaded as well, initialize the initial power state of the sign
        this.chunkFutureProvider.trackNeighboursLoaded(chunk, ChunkNeighbourList.neighboursOf(chunk, 1), new ChunkStateListener() {
            @Override
            public void onRegistered(ChunkStateTracker tracker) {
                if (tracker.isLoaded()) {
                    onLoaded(tracker);
                }
            }

            @Override
            public void onCancelled(ChunkStateTracker tracker) {
            }

            @Override
            public void onLoaded(ChunkStateTracker tracker) {
                activateSignsInChunk(tracker.getChunk());
            }

            @Override
            public void onUnloaded(ChunkStateTracker tracker) {
                deactivateSignsInChunk(tracker.getChunk());
            }
        });
    }

    private Collection<BlockState> getBlockStatesSafe(Chunk chunk) {
        try {
            return WorldUtil.getBlockStates(chunk);
        } catch (Throwable t) {
            this.controller.getPlugin().getLogger().log(Level.SEVERE, "Error reading sign block states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
            return Collections.emptyList();
        }
    }

    private void addChunkByBlockEntry(SignController.Entry entry) {
        Block b = entry.sign.getBlock();
        final long key = LongBlockCoordinates.map(b.getX(), b.getY(), b.getZ());
        addChunkByBlockEntry(entry, key);
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftUp(key));
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftDown(key));
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftEast(key));
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftWest(key));
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftSouth(key));
        addChunkByBlockEntry(entry, LongBlockCoordinates.shiftNorth(key));
    }

    private void addChunkByBlockEntry(final SignController.Entry entry, long key) {
        this.signsByNeighbouringBlock.merge(key, entry.singletonArray, (a, b) -> {
            int len = a.length;
            SignController.Entry[] result = Arrays.copyOf(a, len + 1);
            result[len] = entry;
            return result;
        });
    }

    /**
     * Orders to delete cached sign information about a particular chunk
     *
     * @param chunk
     */
    void unloadChunk(Chunk chunk) {
        List<SignController.Entry> atChunk = this.signsByChunk.remove(chunk.getX(), chunk.getZ());
        if (atChunk != null) {
            // Remove all entries from the by-neighbour-block mapping
            for (SignController.Entry entry : atChunk) {
                // De-activate first, if it was activated still
                if (entry.activated && !entry.sign.isRemoved()) {
                    this.controller.deactivateEntry(entry);
                }

                this.removeFromByBlockEntry(entry);
            }
        }
    }

    void removeInvalidEntry(SignController.Entry entry) {
        // Remove entry from by-chunk mapping
        List<SignController.Entry> atChunk = this.signsByChunk.get(entry.chunkKey);
        if (atChunk != null && atChunk.remove(entry) && atChunk.isEmpty()) {
            this.signsByChunk.remove(entry.chunkKey);
        }

        // Remove entry from by-block mapping
        removeFromByBlockEntry(entry);
    }

    void removeFromByBlockEntry(SignController.Entry entry) {
        Block b = entry.sign.getBlock();
        final long key = LongBlockCoordinates.map(b.getX(), b.getY(), b.getZ());
        removeChunkByBlockEntry(entry, key);
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftUp(key));
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftDown(key));
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftEast(key));
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftWest(key));
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftSouth(key));
        removeChunkByBlockEntry(entry, LongBlockCoordinates.shiftNorth(key));
    }

    private void removeChunkByBlockEntry(SignController.Entry entry, long key) {
        SignController.Entry[] entries = this.signsByNeighbouringBlock.remove(key);

        // If already removed, ignore
        // The range of coordinates that fall well within the chunk can be removed entirely without
        // thinking too hard about it. There is a guarantee that this mask works thanks to the
        // neighbour being at most one block away.
        if (entries == null || LongBlockCoordinates.isWithinChunk(key)) {
            return;
        }

        // Slow method - we have to remove entries at chunk boundaries carefully, so we don't touch
        // entries that refer to signs in neighbouring (still loaded!) chunks.
        SignController.Entry[] newEntries = SignController.Entry.NO_ENTRIES;
        int numNewEntries = 0;
        int len = entries.length;
        while (--len >= 0) {
            SignController.Entry e = entries[len];
            if (e == entry || e.chunkKey == entry.chunkKey) {
                continue; // Entry to remove / same chunk. Yeet!
            }

            // Different chunk. Keep!
            newEntries = Arrays.copyOf(newEntries, numNewEntries + 1);
            newEntries[numNewEntries] = e;
            numNewEntries++;
        }

        // Put if there are entries to keep
        if (numNewEntries > 0) {
            this.signsByNeighbouringBlock.put(key, newEntries);
        }
    }

    static class SignControllerWorldDisabled extends SignControllerWorld {

        SignControllerWorldDisabled(SignController controller, World world) {
            super(controller, world);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public SignController.Entry[] findNearby(Block block) {
            return SignController.Entry.NO_ENTRIES;
        }

        @Override
        public SignController.Entry addSign(Block signBlock) {
            return null;
        }

        @Override
        public RefreshResult refreshInChunk(Chunk chunk) {
            return RefreshResult.NONE;
        }

        @Override
        void loadChunk(Chunk chunk) {}

        @Override
        void unloadChunk(Chunk chunk) {}
    }

    public static class RefreshResult {
        public static final RefreshResult NONE = new RefreshResult(0, 0);
        public final int numAdded, numRemoved;

        public RefreshResult(int numAdded, int numRemoved) {
            this.numAdded = numAdded;
            this.numRemoved = numRemoved;
        }

        public RefreshResult add(RefreshResult other) {
            return new RefreshResult(this.numAdded + other.numAdded,
                                     this.numRemoved + other.numRemoved);
        }
    }
}
