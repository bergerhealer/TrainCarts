package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Stores all the chunks nearby a train. This information is essential for performing
 * automatic chunk loading when keep-chunks-loaded is set. This takes care of tracking
 * which chunks should be loaded or unloaded while the train moves.<br>
 * <br>
 * This class purposefully excludes any mention of MinecartMember and MinecartGroup to make it
 * easier to test.
 */
public class ChunkArea {
    public static final int CHUNK_RANGE = 2;
    public static final int CHUNK_EDGE = 2 * CHUNK_RANGE + 1;
    public static final int CHUNK_AREA = CHUNK_EDGE * CHUNK_EDGE;
    private World current_world = null;
    private final Set<IntVector2> added_chunk_centers = new HashSet<IntVector2>();
    private final Map<IntVector2, OwnedChunk> chunks = new HashMap<IntVector2, OwnedChunk>();
    private final List<OwnedChunk> removed_chunks = new ArrayList<OwnedChunk>();
    private final List<OwnedChunk> added_chunks = new ArrayList<OwnedChunk>();

    /**
     * Refreshes this chunk area to contain all the minecart chunk coordinates in the set.
     * After this refresh the added and removed chunks can be queried, as well as all currently managed chunks.
     * 
     * @param world the minecarts are in (to detect world changes)
     * @param coordinates of the chunks all minecarts are in
     */
    public void refresh(World world, Set<IntVector2> coordinates) {
        // Reset
        this.removed_chunks.clear();
        this.added_chunks.clear();

        // When world changes, perform a full reset
        if (this.current_world != world) {
            this.current_world = world;
            this.removed_chunks.addAll(this.chunks.values());
            this.added_chunk_centers.clear();
            this.chunks.clear();
        }

        // Find chunk centers that have been added
        for (IntVector2 coord : coordinates) {
            if (this.added_chunk_centers.add(coord)) {
                // Iterate all 5x5 neighbours of this coordinate and store them in the owned chunks
                // If new owned chunks are created, store them in a special 'added' set
                int cx, cz;
                for (cx = -CHUNK_RANGE; cx <= CHUNK_RANGE; cx++) {
                    for (cz = -CHUNK_RANGE; cz <= CHUNK_RANGE; cz++) {
                        IntVector2 ownedCoord = new IntVector2(coord.x + cx, coord.z + cz);
                        OwnedChunk ownedChunk = this.chunks.get(ownedCoord);
                        if (ownedChunk == null) {
                            ownedChunk = new OwnedChunk(world, ownedCoord);
                            ownedChunk.addChunk(coord);
                            this.chunks.put(ownedCoord, ownedChunk);
                            this.added_chunks.add(ownedChunk);
                        } else {
                            ownedChunk.addChunk(coord);
                        }
                    }
                }
            }
        }

        // Find chunk centers that have been removed
        Iterator<IntVector2> added_iter = this.added_chunk_centers.iterator();
        while (added_iter.hasNext()) {
            IntVector2 coord = added_iter.next();
            if (!coordinates.contains(coord)) {
                added_iter.remove();

                // Iterate all 5x5 neighbours of this coordinate and remove them from the owned chunks
                // If owned chunks become empty, store them in a special 'removed' set
                int cx, cz;
                for (cx = -CHUNK_RANGE; cx <= CHUNK_RANGE; cx++) {
                    for (cz = -CHUNK_RANGE; cz <= CHUNK_RANGE; cz++) {
                        IntVector2 ownedCoord = new IntVector2(coord.x + cx, coord.z + cz);
                        OwnedChunk ownedChunk = this.chunks.get(ownedCoord);
                        if (ownedChunk != null) {
                            ownedChunk.removeChunk(coord);
                            if (ownedChunk.isEmpty()) {
                                this.removed_chunks.add(ownedChunk);
                                this.chunks.remove(ownedCoord);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets all the chunk centers in which a minecart resides
     * 
     * @return chunk centers
     */
    public final Set<IntVector2> getAllCenters() {
        return this.added_chunk_centers;
    }

    /**
     * Gets all chunks that are within range of the Minecart
     * 
     * @return all chunks nearby the minecart
     */
    public final Collection<OwnedChunk> getAll() {
        return this.chunks.values();
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
     * A single chunk that has one or more chunks neighbouring it with a minecart in it
     */
    public static final class OwnedChunk {
        private static final Runnable DUMMY_RUNNABLE = new Runnable() { public void run() {} };
        private final int cx, cz;
        private final World world;
        private final Set<IntVector2> chunks = new HashSet<IntVector2>();
        private int distance;

        public OwnedChunk(World world, IntVector2 coord) {
            this.world = world;
            this.cx = coord.x;
            this.cz = coord.z;
            this.distance = Integer.MAX_VALUE;
        }

        public boolean isLoaded() {
            return this.world.isChunkLoaded(this.cx, this.cz);
        }

        public void unloadChunkRequest() {
            this.world.unloadChunkRequest(this.cx, this.cz);
        }

        public void loadChunkRequest() {
            WorldUtil.getChunkAsync(this.world, this.cx, this.cz, DUMMY_RUNNABLE);
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

        public void addChunk(IntVector2 chunk) {
            // Add the chunk. If actually added, update distance if less.
            if (this.chunks.add(chunk)) {
                this.distance = Math.min(this.distance, this.calcDistance(chunk));
            }
        }

        public void removeChunk(IntVector2 chunk) {
            // Remove the chunk. If actually removed, update distance if more.
            if (this.chunks.remove(chunk)) {
                int oldDistance = this.calcDistance(chunk);
                if (oldDistance <= this.distance) {
                    this.distance = Integer.MAX_VALUE;
                    for (IntVector2 storedChunk : this.chunks) {
                        this.distance = Math.min(this.distance, this.calcDistance(storedChunk));
                    }
                }
            }
        }

        public boolean isEmpty() {
            return this.chunks.isEmpty();
        }

        private final int calcDistance(IntVector2 chunk) {
            return Math.max(Math.abs(chunk.x - this.cx),
                            Math.abs(chunk.z - this.cz));
        }
    }

}
