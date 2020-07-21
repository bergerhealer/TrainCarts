package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;

/**
 * Stores all the chunks nearby a train. This information is essential for performing
 * automatic chunk loading when keep-chunks-loaded is set. This takes care of tracking
 * which chunks should be loaded or unloaded while the train moves.<br>
 * <br>
 * This class purposefully excludes any mention of MinecartMember and MinecartGroup to make it
 * easier to test.
 */
public class ChunkArea {
    public static final Runnable DUMMY_RUNNABLE = new Runnable() { public void run() {} };
    public static final int CHUNK_RANGE = 2;
    public static final int CHUNK_EDGE = 2 * CHUNK_RANGE + 1;
    public static final int CHUNK_AREA = CHUNK_EDGE * CHUNK_EDGE;
    private World current_world = null;
    private final LongHashSet added_chunk_centers = new LongHashSet();
    private LongHashMap<OwnedChunk> chunks = new LongHashMap<OwnedChunk>();
    private final List<OwnedChunk> all_chunks = new ArrayList<OwnedChunk>();
    private final List<OwnedChunk> removed_chunks = new ArrayList<OwnedChunk>();
    private final List<OwnedChunk> added_chunks = new ArrayList<OwnedChunk>();

    /**
     * Clears all chunks stores inside this chunk area, without handling events for them.
     * This makes it have the same state as when first constructed.
     */
    public void reset() {
        this.added_chunk_centers.clear();
        this.chunks.clear();
        for (OwnedChunk chunk : all_chunks) {
            chunk.forcedChunk.close();
        }
        this.all_chunks.clear();
        this.removed_chunks.clear();
        this.added_chunks.clear();
    }

    /**
     * Refreshes this chunk area to contain all the minecart chunk coordinates in the set.
     * After this refresh the added and removed chunks can be queried, as well as all currently managed chunks.
     * 
     * @param world the minecarts are in (to detect world changes)
     * @param coordinates of the chunks all minecarts are in
     */
    public void refresh(World world, LongHashSet coordinates) {
        // Reset
        this.removed_chunks.clear();
        this.added_chunks.clear();

        // When world changes, perform a full reset
        if (this.current_world != world) {
            this.current_world = world;
            this.removed_chunks.addAll(this.chunks.getValues());
            for (OwnedChunk chunk : this.removed_chunks) {
                chunk.forcedChunk.close();
            }
            this.added_chunk_centers.clear();
            this.chunks = new LongHashMap<OwnedChunk>();
            this.all_chunks.clear();
        }

        // Sync previous distance
        for (OwnedChunk owned : this.all_chunks) {
            owned.distance_previous = owned.distance;
        }

        // Find chunk centers that have been added
        LongHashSet.LongIterator iter = coordinates.longIterator();
        while (iter.hasNext()) {
            long coord = iter.next();
            if (this.added_chunk_centers.add(coord)) {
                // Iterate all 5x5 neighbours of this coordinate and store them in the owned chunks
                // If new owned chunks are created, store them in a special 'added' set
                int mx = MathUtil.longHashMsw(coord);
                int mz = MathUtil.longHashLsw(coord);
                int cx, cz;
                for (cx = -CHUNK_RANGE; cx <= CHUNK_RANGE; cx++) {
                    for (cz = -CHUNK_RANGE; cz <= CHUNK_RANGE; cz++) {
                        long ownedCoord = MathUtil.longHashToLong(mx + cx, mz + cz);
                        OwnedChunk ownedChunk = this.chunks.get(ownedCoord);
                        if (ownedChunk == null) {
                            ownedChunk = new OwnedChunk(world, mx + cx, mz + cz);
                            ownedChunk.addChunk(coord, mx, mz);
                            this.all_chunks.add(ownedChunk);
                            this.chunks.put(ownedCoord, ownedChunk);
                            this.added_chunks.add(ownedChunk);
                        } else {
                            ownedChunk.addChunk(coord, mx, mz);
                        }
                    }
                }
            }
        }

        // Find chunk centers that have been removed
        LongHashSet.LongIterator added_iter = this.added_chunk_centers.longIterator();
        while (added_iter.hasNext()) {
            long coord = added_iter.next();
            if (!coordinates.contains(coord)) {
                added_iter.remove();

                // Iterate all 5x5 neighbours of this coordinate and remove them from the owned chunks
                // If owned chunks become empty, store them in a special 'removed' set
                int mx = MathUtil.longHashMsw(coord);
                int mz = MathUtil.longHashLsw(coord);
                int cx, cz;
                for (cx = -CHUNK_RANGE; cx <= CHUNK_RANGE; cx++) {
                    for (cz = -CHUNK_RANGE; cz <= CHUNK_RANGE; cz++) {
                        long ownedCoord = MathUtil.longHashToLong(mx + cx, mz + cz);
                        OwnedChunk ownedChunk = this.chunks.get(ownedCoord);
                        if (ownedChunk != null) {
                            ownedChunk.removeChunk(coord, mx, mz);
                            if (ownedChunk.isEmpty()) {
                                ownedChunk.forcedChunk.close();
                                this.removed_chunks.add(ownedChunk);
                                this.chunks.remove(ownedCoord);
                                this.all_chunks.remove(ownedChunk);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds all the chunks kept loaded inside this chunk area to a list, preventing them
     * from unloading again. All the forced chunks obtained should be closed by the caller
     * of this method.
     * 
     * @param forcedChunks List of forced chunks to add to
     */
    public final void getForcedChunks(List<ForcedChunk> forcedChunks) {
        for (OwnedChunk chunk : this.all_chunks) {
            if (!chunk.forcedChunk.isNone()) {
                forcedChunks.add(chunk.forcedChunk.clone());
            }
        }
    }

    /**
     * Gets all the chunk centers in which a minecart resides
     * 
     * @return chunk centers
     */
    public final LongHashSet getAllCenters() {
        return this.added_chunk_centers;
    }

    /**
     * Gets all chunks that are within range of the Minecart
     * 
     * @return all chunks nearby the minecart
     */
    public final Collection<OwnedChunk> getAll() {
        return this.all_chunks;
    }

    /**
     * Gets all chunks that were removed during the last {@link #refresh(Set)}
     * 
     * @return removed chunks
     */
    public final List<OwnedChunk> getRemoved() {
        return this.removed_chunks;
    }

    /**
     * Gets all chunks that were added during the last {@link #refresh(Set)}
     * 
     * @return added chunks
     */
    public final List<OwnedChunk> getAdded() {
        return this.added_chunks;
    }

    /**
     * Gets whether a particular chunk coordinate, encoded as a Long, are contained
     * in this chunk area.
     * 
     * @param chunkLongCoord
     * @return True if contained.
     */
    public boolean containsChunk(long chunkLongCoord) {
        return this.chunks.contains(chunkLongCoord);
    }

    /**
     * Gets whether a particular chunk coordinates, are contained
     * in this chunk area.
     * 
     * @param chunkX
     * @param chunkZ
     * @return True if contained.
     */
    public boolean containsChunk(int chunkX, int chunkZ) {
        return containsChunk(MathUtil.longHashToLong(chunkX, chunkZ));
    }

    /**
     * A single chunk that has one or more chunks neighbouring it with a minecart in it
     */
    public static final class OwnedChunk {
        private final int cx, cz;
        private final World world;
        private final LongHashSet chunks = new LongHashSet();
        private int distance;
        private int distance_previous;
        private final ForcedChunk forcedChunk = ForcedChunk.none();

        public OwnedChunk(World world, int cx, int cz) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
            this.distance = Integer.MAX_VALUE;
            this.distance_previous = Integer.MAX_VALUE;
        }

        public boolean isLoaded() {
            return this.world.isChunkLoaded(this.cx, this.cz);
        }

        public void keepLoaded(boolean keepLoaded) {
            if (keepLoaded) {
                this.forcedChunk.move(ChunkUtil.forceChunkLoaded(this.world, this.cx, this.cz));
            } else {
                this.forcedChunk.close();
            }
        }

        public void loadChunk() {
            this.world.getChunkAt(this.cx, this.cz);
        }

        public World getWorld() {
            return this.world;
        }

        public int getX() {
            return this.cx;
        }

        public int getZ() {
            return this.cz;
        }

        public int getDistance() {
            return this.distance;
        }

        public int getPreviousDistance() {
            return this.distance_previous;
        }

        public boolean isAdded() {
            return (this.distance < Integer.MAX_VALUE) && (this.distance_previous == Integer.MAX_VALUE);
        }

        public boolean isRemoved() {
            return (this.distance == Integer.MAX_VALUE) && (this.distance_previous < Integer.MAX_VALUE);
        }

        private void addChunk(long key, int cx, int cz) {
            // Add the chunk. If actually added, update distance if less.
            if (this.chunks.add(key)) {
                this.distance = Math.min(this.distance, this.calcDistance(cx, cz));
            }
        }

        private void removeChunk(long key, int cx, int cz) {
            // Remove the chunk. If actually removed, update distance if more.
            if (this.chunks.remove(key)) {
                int oldDistance = this.calcDistance(cx, cz);
                if (oldDistance <= this.distance) {
                    this.distance = Integer.MAX_VALUE;
                    LongHashSet.LongIterator iter = this.chunks.longIterator();
                    while (iter.hasNext()) {
                        long storedChunk = iter.next();
                        int distance = this.calcDistance(MathUtil.longHashMsw(storedChunk), MathUtil.longHashLsw(storedChunk));
                        if (distance < this.distance) {
                            this.distance = distance;
                        }
                    }
                }
            }
        }

        public boolean isEmpty() {
            return this.chunks.isEmpty();
        }

        private final int calcDistance(int cx, int cz) {
            return Math.max(Math.abs(cx - this.cx),
                            Math.abs(cz - this.cz));
        }
    }

}
