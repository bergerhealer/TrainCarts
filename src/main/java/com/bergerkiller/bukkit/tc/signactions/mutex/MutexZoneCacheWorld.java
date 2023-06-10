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
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Mutex zones that exist on a particular world
 */
public class MutexZoneCacheWorld {
    private static final MutexZone[] NO_ZONES = new MutexZone[0];
    private final OfflineWorld world;
    private final Map<SignSidePositionKey, MutexZone> bySignPosition = new HashMap<>();
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
        bySignPosition.put(new SignSidePositionKey(zone.signBlock.getPosition(), zone.signFront), zone);

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

    public MutexZone removeAtSign(IntVector3 signPosition, boolean front) {
        MutexZone zone = bySignPosition.remove(new SignSidePositionKey(signPosition, front));
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
         * Gets the mutex zones that exist crossing a ray between the current
         * position on a track walking point path and the final position of the
         * current rail.
         * Updates the internally tracked chunk if needed. As such, this
         * method benefits from many queries in the same chunk in a row.
         *
         * @param walker Track walking point
         * @return Mutex zone at this block position with distance to it, or null if none
         */
        public MutexZoneResult get(TrackWalkingPoint walker) {
            RailPath.Position p1 = walker.state.position();
            RailPath.Position p2 = walker.currentRailPath.getEndOfPath(walker.state.railBlock(), p1);
            return get(p1, p2);
        }

        /**
         * Gets the mutex zones that exist crossing a ray between two
         * point positions on the rails.
         * Updates the internally tracked chunk if needed. As such, this
         * method benefits from many queries in the same chunk in a row.
         *
         * @param p1 Start point
         * @param p2 End point
         * @return Mutex zone at this block position with distance to it, or null if none
         */
        public MutexZoneResult get(RailPath.Position p1, RailPath.Position p2) {
            p1.assertAbsolute();
            p2.assertAbsolute();

            int cx1 = MathUtil.toChunk(p1.posX);
            int cz1 = MathUtil.toChunk(p1.posZ);
            int cx2 = MathUtil.toChunk(p2.posX);
            int cz2 = MathUtil.toChunk(p2.posZ);

            // Iterate the chunks visited from p1 to p2
            // If same chunk, skip some special logic that combines mutex zones together
            List<MutexZone> zones;
            if (cx1 == cx2 && cz1 == cz2) {
                zones = Arrays.asList(findZonesInChunk(cx1, cz1));
            } else {
                zones = Collections.emptyList();

                final int cx_step = (cx1 > cx2) ? -1 : 1;
                final int cz_step = (cz1 > cz2) ? -1 : 1;
                int cz = cz1;
                while (true) {
                    int cx = cx1;
                    while (true) {
                        // Loops from cx1/cz1 -> cx2/cz2
                        {
                            for (MutexZone zone : findZonesInChunk(cx, cz)) {
                                if (zones.isEmpty()) {
                                    zones = new ArrayList<>(4);
                                    zones.add(zone);
                                } else if (!zones.contains(zone)) {
                                    zones.add(zone);
                                }
                            }
                        }

                        if (cx == cx2)
                            break;
                        else
                            cx += cx_step;
                    }

                    if (cz == cz2)
                        break;
                    else
                        cz += cz_step;
                }
            }

            if (zones.isEmpty()) {
                return null;
            }

            double motX = p2.posX - p1.posX;
            double motY = p2.posY - p1.posY;
            double motZ = p2.posZ - p1.posZ;
            double distance = p2.distance(p1);

            if (distance <= 1e-10) {
                // Check inside a mutex zone
                IntVector3 blockPos = new IntVector3(p1.posX, p1.posY, p1.posZ);
                for (MutexZone zone : zones) {
                    if (zone.containsBlock(blockPos)) {
                        return new MutexZoneResult(zone, 0.0);
                    }
                }
                return null;
            } else {
                // Check distance away or in a mutex zone
                // Normalize
                double f = 1.0 / distance;
                motX *= f;
                motY *= f;
                motZ *= f;

                // Check if any of the mutex zones include the block
                // Default best is set to the distance of the path searched, so
                // that mutex zones further away are not succeeding here.
                MutexZoneResult best = new MutexZoneResult(null, distance);
                for (MutexZone zone : zones) {
                    double dist = zone.hitTest(p1.posX, p1.posY, p1.posZ,
                                               motX, motY, motZ);
                    if (dist < best.distance) {
                        best = new MutexZoneResult(zone, dist);
                    }
                }
                return best.zone == null ? null : best;
            }
        }

        private MutexZone[] findZonesInChunk(int cx, int cz) {
            // Get/update the mutex zones in the current chunk
            if (cx != this.chunkX || cz != this.chunkZ) {
                this.chunkX = cx;
                this.chunkZ = cz;
                MutexZone[] zones = byChunk.get(cx, cz);
                if (zones == null) {
                    zones = NO_ZONES;
                }
                this.chunkZones = zones;
                return zones;
            } else {
                return this.chunkZones;
            }
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

    /**
     * The result of a mutex zone search
     */
    public static class MutexZoneResult {
        public final MutexZone zone;
        public final double distance;

        public MutexZoneResult(MutexZone zone, double distance) {
            this.zone = zone;
            this.distance = distance;
        }
    }

    private static class SignSidePositionKey {
        public final IntVector3 position;
        public final boolean front;

        public SignSidePositionKey(IntVector3 position, boolean front) {
            this.position = position;
            this.front = front;
        }

        @Override
        public int hashCode() {
            return position.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            SignSidePositionKey other = (SignSidePositionKey) o;
            return position.equals(other.position) && front == other.front;
        }
    }
}
