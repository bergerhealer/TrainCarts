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
}
