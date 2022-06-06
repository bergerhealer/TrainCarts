package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;

/**
 * Mutex zones that exist on a particular world
 */
public class MutexZoneCacheWorld {
    private static final MutexZone[] NO_ZONES = new MutexZone[0];
    private final OfflineWorld world;
    private final Map<IntVector3, MutexZone> bySignPosition = new HashMap<>();
    private final LongHashMap<MutexZone[]> byChunk = new LongHashMap<>();

    public MutexZoneCacheWorld(OfflineWorld world) {
        this.world = world;
    }

    public World getWorld() {
        return this.world.getLoadedWorld();
    }

    public OfflineWorld getOfflineWorld() {
        return this.world;
    }

    public MovingPoint track(IntVector3 blockPosition) {
        return new MovingPoint(blockPosition.getChunkX(), blockPosition.getChunkZ());
    }

    public MutexZone find(IntVector3 position) {
        MutexZone[] inChunk = byChunk.get(position.getChunkX(), position.getChunkZ());
        if (inChunk != null) {
            for (MutexZone zone : inChunk) {
                if (zone.containsBlock(position)) {
                    return zone;
                }
            }
        }
        return null;
    }

    public boolean isMutexZoneNearby(IntVector3 block, int radius) {
        int chunkMinX = MathUtil.toChunk(block.x - radius);
        int chunkMaxX = MathUtil.toChunk(block.x + radius);
        int chunkMinZ = MathUtil.toChunk(block.z - radius);
        int chunkMaxZ = MathUtil.toChunk(block.z + radius);
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                MutexZone[] zonesAtChunk = byChunk.get(cx, cz);
                if (zonesAtChunk != null) {
                    for (MutexZone zone : zonesAtChunk) {
                        if (zone.isNearby(block, radius)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<MutexZone> findNearbyZones(IntVector3 block, int radius) {
        List<MutexZone> result = Collections.emptyList();
        int chunkMinX = (block.x - radius) >> 4;
        int chunkMaxX = (block.x + radius) >> 4;
        int chunkMinZ = (block.z - radius) >> 4;
        int chunkMaxZ = (block.z + radius) >> 4;
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                MutexZone[] zonesAtChunk = byChunk.get(cx, cz);
                if (zonesAtChunk != null) {
                    for (MutexZone zone : zonesAtChunk) {
                        if (zone.isNearby(block, radius)) {
                            if (result.isEmpty()) {
                                result = new ArrayList<>();
                            }
                            result.add(zone);
                        }
                    }
                }
            }
        }
        return result;
    }

    public void add(MutexZone zone) {
        bySignPosition.put(zone.signBlock.getPosition(), zone);

        // Usually only one zone sits in a chunk. This optimizes that case.
        MutexZone[] singleZone = new MutexZone[] {zone};

        // Register in all the chunks
        int chunkMinX = zone.start.getChunkX();
        int chunkMaxX = zone.end.getChunkX();
        int chunkMinZ = zone.start.getChunkZ();
        int chunkMaxZ = zone.end.getChunkZ();
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                long key = MathUtil.longHashToLong(cx, cz);
                MutexZone[] atChunk = byChunk.get(key);
                if (atChunk == null) {
                    byChunk.put(key, singleZone);
                } else {
                    int len = atChunk.length;
                    atChunk = Arrays.copyOf(atChunk, len + 1);
                    atChunk[len] = zone;
                    byChunk.put(key, atChunk);
                }
            }
        }
    }

    public MutexZone removeAtSign(IntVector3 signPosition) {
        MutexZone zone = bySignPosition.remove(signPosition);
        if (zone != null) {
            // De-register in all the chunks
            int chunkMinX = zone.start.getChunkX();
            int chunkMaxX = zone.end.getChunkX();
            int chunkMinZ = zone.start.getChunkZ();
            int chunkMaxZ = zone.end.getChunkZ();
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                    long key = MathUtil.longHashToLong(cx, cz);
                    MutexZone[] atChunk = byChunk.remove(key);
                    if (atChunk != null && (atChunk.length > 1 || atChunk[0] != zone)) {
                        // Remove the mutex zone from the array and put back the new array
                        for (int i = atChunk.length-1; i >= 0; --i) {
                            if (atChunk[i] == zone) {
                                atChunk = LogicUtil.removeArrayElement(atChunk, i);
                            }
                        }
                        byChunk.put(key, atChunk);
                    }
                }
            }
        }
        return zone;
    }

    public void clear() {
        bySignPosition.clear();
        byChunk.clear();
    }

    /**
     * Tracks the mutex zones at given block positions. Automatically retrieves the mutex zones
     * at chunk boundaries. Should be used when querying the mutex zones along a trail of rail
     * blocks that don't change chunk coordinates often.
     */
    public final class MovingPoint {
        private int chunkX;
        private int chunkZ;
        private MutexZone[] chunkZones;

        private MovingPoint(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;

            MutexZone[] zones = byChunk.get(chunkX, chunkZ);
            this.chunkZones = (zones == null) ? NO_ZONES : zones;
        }

        /**
         * Gets the mutex zone that exists at the given block position.
         * Updates the internally tracked chunk if needed. As such, this
         * method benefits from many queries in the same chunk in a row.
         *
         * @param blockPosition
         * @return Mutex zone at this block position, or null if none
         */
        public MutexZone get(IntVector3 blockPosition) {
            // Get/update the mutex zones in the current chunk of this block
            MutexZone[] zones;
            {
                int cx = blockPosition.getChunkX();
                int cz = blockPosition.getChunkZ();
                if (cx != this.chunkX || cz != this.chunkZ) {
                    this.chunkX = cx;
                    this.chunkZ = cz;
                    zones = byChunk.get(cx, cz);
                    if (zones == null) {
                        zones = NO_ZONES;
                    }
                    this.chunkZones = zones;
                } else {
                    zones = this.chunkZones;
                }
            }

            // Check if any of the mutex zones include the block
            for (MutexZone zone : zones) {
                if (zone.containsBlock(blockPosition)) {
                    return zone;
                }
            }
            return null;
        }

        /**
         * Checks whether there are any mutex zones nearby the current chunk
         * this moving point is tracking. This checks whether there are mutex
         * zones in this current chunk, or any of the neighbouring chunks.
         *
         * @return True if there are mutex zones nearby
         */
        public boolean isNear() {
            if (chunkZones != NO_ZONES) {
                return true;
            }

            for (int cz = -1; cz <= 1; cz++) {
                for (int cx = -1; cx <= 1; cx++) {
                    if ((cx != 0 || cz != 0) && byChunk.contains(this.chunkX + cx, this.chunkZ + cz)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
