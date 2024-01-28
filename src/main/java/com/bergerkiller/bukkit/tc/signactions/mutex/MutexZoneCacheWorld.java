package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
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
    protected final Map<SignSidePositionKey, MutexZone> bySignPosition = new HashMap<>();
    protected final Map<PathingSignKey, MutexZonePath> byPathingKey = new HashMap<>();
    private final LongHashMap<MutexZone[]> byChunk = new LongHashMap<>();
    private final Set<MutexZone> newZonesLive = new HashSet<>();
    private List<MutexZone> newZones = Collections.emptyList();

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
        return new MovingPoint(byChunk::get, blockPosition.getChunkX(), blockPosition.getChunkZ());
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

    public MutexZone findBySign(IntVector3 signPosition, boolean signFront) {
        return bySignPosition.get(new SignSidePositionKey(signPosition, signFront));
    }

    /**
     * Gets a List of all mutex zones that have been added since the previous tick.
     * This List can be used during train updates to identify new mutex zones, and register
     * their presence inside them.
     *
     * @return List of new mutex zones
     */
    public List<MutexZone> getNewZones() {
        return newZones;
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
        zone.addToWorld(this);
        newZonesLive.add(zone);
        mapToChunks(zone, false);
    }

    protected void remove(MutexZone zone) {
        // Remove from the 'new zones' tracking as well
        {
            newZonesLive.remove(zone);
            int newZoneIdx = newZones.indexOf(zone);
            if (newZoneIdx != -1) {
                List<MutexZone> copy = new ArrayList<>(newZones);
                copy.remove(newZoneIdx);
                newZones = copy;
            }
        }

        unmapFromChunks(zone);
    }

    protected void addNewChunks(MutexZone zone) {
        mapToChunks(zone, true);
    }

    private void mapToChunks(MutexZone zone, boolean checkDuplicates) {
        // Usually only one zone sits in a chunk. This optimizes that case.
        MutexZone[] singleZone = new MutexZone[] {zone};

        // Register in all the chunks
        zone.forAllContainedChunks((cx, cz) -> {
            long key = MathUtil.longHashToLong(cx, cz);
            MutexZone[] atChunk = byChunk.get(key);
            if (atChunk == null) {
                byChunk.put(key, singleZone);
            } else if (!checkDuplicates || !isChunkInArray(atChunk, zone)) {
                int len = atChunk.length;
                atChunk = Arrays.copyOf(atChunk, len + 1);
                atChunk[len] = zone;
                byChunk.put(key, atChunk);
            }
        });
    }

    private void unmapFromChunks(MutexZone zone) {
        zone.forAllContainedChunks((cx, cz) -> {
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
        });
    }

    private boolean isChunkInArray(MutexZone[] zones, MutexZone zone) {
        for (MutexZone zoneInZones : zones) {
            if (zoneInZones == zone) {
                return true;
            }
        }
        return false;
    }

    public MutexZone removeAtSign(IntVector3 signPosition, boolean front) {
        MutexZone zone = bySignPosition.remove(new SignSidePositionKey(signPosition, front));
        if (zone != null) {
            // De-register in all the chunks
            remove(zone);
        }
        return zone;
    }

    public MutexZonePath getOrCreatePathingMutex(
            RailLookup.TrackedSign sign,
            MinecartGroup group,
            IntVector3 initialBlock,
            UnaryOperator<MutexZonePath.OptionsBuilder> optionsBuilder
    ) {
        // Find existing
        TrainProperties trainProperties = group.getProperties();
        MutexZonePath path = byPathingKey.get(PathingSignKey.of(sign.getUniqueKey(), trainProperties));
        if (path != null) {
            return path;
        }

        // Create new
        path = new MutexZonePath(group.getTrainCarts(), sign, trainProperties,
                optionsBuilder.apply(MutexZonePath.createOptions()));
        path.addBlock(initialBlock);
        add(path);
        return path;
    }

    public void clear() {
        bySignPosition.clear();
        byPathingKey.clear();
        byChunk.clear();
    }

    public void onTick() {
        updatePathingMutexes();

        // Track all mutex zones that have been newly added since previous tick
        // Trains look at these every tick to see if they are inside them, since
        // normally they only look from the head of the train onwards.
        if (newZonesLive.isEmpty()) {
            newZones = Collections.emptyList();
        } else {
            newZones = new ArrayList<>(newZonesLive);
            newZonesLive.clear();
        }
    }

    private void updatePathingMutexes() {
        if (byPathingKey.isEmpty()) {
            return;
        }

        // Expire pathing mutex zones that haven't been visited by the owning group in some time
        int expireTick = CommonUtil.getServerTicks() - 2;
        for (Iterator<MutexZonePath> iter = byPathingKey.values().iterator(); iter.hasNext();) {
            MutexZonePath zonePath = iter.next();
            if (zonePath.isExpired(expireTick)) {
                iter.remove();
                remove(zonePath);
            }
        }
    }

    /**
     * Tracks the mutex zones at given block positions. Automatically retrieves the mutex zones
     * at chunk boundaries. Should be used when querying the mutex zones along a trail of rail
     * blocks that don't change chunk coordinates often.
     */
    public static final class MovingPoint {
        private final MutexZoneByChunkGetter byChunkGetter;
        private int chunkX;
        private int chunkZ;
        private MutexZone[] chunkZones;

        public MovingPoint(MutexZoneByChunkGetter byChunkGetter, int chunkX, int chunkZ) {
            this.byChunkGetter = byChunkGetter;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;

            MutexZone[] zones = byChunkGetter.getAt(chunkX, chunkZ);
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
                MutexZone[] zones = byChunkGetter.getAt(cx, cz);
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
                    if ((cx != 0 || cz != 0) && byChunkGetter.getAt(this.chunkX + cx, this.chunkZ + cz) != null) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @FunctionalInterface
    public interface MutexZoneByChunkGetter {
        MutexZone[] getAt(int cx, int cz);
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

    protected static final class PathingSignKey {
        public final Object uniqueKey;
        public final TrainProperties trainProperties;

        private PathingSignKey(Object signUniqueKey, TrainProperties trainProperties) {
            this.uniqueKey = signUniqueKey;
            this.trainProperties = trainProperties;
        }

        public static PathingSignKey of(Object signUniqueKey, TrainProperties trainProperties) {
            return new PathingSignKey(signUniqueKey, trainProperties);
        }

        public static Optional<PathingSignKey> readFrom(TrainCarts plugin, DataInputStream stream) throws IOException {
            Object signUniqueKey = plugin.getTrackedSignLookup().deserializeUniqueKey(Util.readByteArray(stream));
            TrainProperties trainProperties = TrainPropertiesStore.get(stream.readUTF());
            if (signUniqueKey == null || trainProperties == null) {
                return Optional.empty();
            } else {
                return Optional.of(of(signUniqueKey, trainProperties));
            }
        }

        public boolean writeTo(TrainCarts plugin, DataOutputStream stream) throws IOException {
            if (trainProperties.isRemoved()) {
                return false;
            }

            byte[] data = plugin.getTrackedSignLookup().serializeUniqueKey(uniqueKey);
            if (data == null) {
                return false;
            }

            Util.writeByteArray(stream, data);
            stream.writeUTF(trainProperties.getTrainName());
            return true;
        }

        @Override
        public int hashCode() {
            return uniqueKey.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            PathingSignKey other = (PathingSignKey) o;
            return uniqueKey.equals(other.uniqueKey) && trainProperties == other.trainProperties;
        }
    }

    protected static class SignSidePositionKey {
        public final IntVector3 position;
        public final boolean front;

        public static SignSidePositionKey ofZone(MutexZone zone) {
            return new SignSidePositionKey(zone.signBlock.getPosition(), zone.signFront);
        }

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
