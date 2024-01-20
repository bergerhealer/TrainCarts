package com.bergerkiller.bukkit.tc.signactions.mutex;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.debug.particles.DebugParticles;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * A mutex zone covering a path trail of blocks
 */
public class MutexZonePath extends MutexZone {
    private final TrainCarts plugin;
    protected final MutexZoneCacheWorld.PathingSignKey key;
    private MutexZoneCacheWorld world; // Assigned when added
    private RailLookup.TrackedSign sign;
    private final double spacing;
    private final double maxDistance;
    private final Set<IntVector3> blocks = new LinkedHashSet<>(128);
    private int tickLastUsed;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int minCX, minCZ, maxCX, maxCZ;

    private final List<OrientedBoundingBox> cubes = new ArrayList<>(128);

    protected MutexZonePath(
            final TrainCarts plugin,
            final RailLookup.TrackedSign sign,
            final TrainProperties trainProperties,
            final OptionsBuilder options
    ) {
        this(plugin,
             OfflineBlock.of(sign.signBlock),
             MutexZoneCacheWorld.PathingSignKey.of(sign.getUniqueKey(), trainProperties),
             sign, options);
    }

    private MutexZonePath(
            final TrainCarts plugin,
            final OfflineBlock signBlock,
            final MutexZoneCacheWorld.PathingSignKey key,
            final RailLookup.TrackedSign sign, // Null allowed
            final OptionsBuilder options
    ) {
        super(signBlock, true /* unused */, options.type, options.name, options.statement);
        this.plugin = plugin;
        this.key = key;
        this.sign = sign;
        this.spacing = options.spacing;
        this.maxDistance = options.maxDistance;
        this.tickLastUsed = -1;
    }

    /**
     * Reads all the path mutexes stored in a root data block
     *
     * @param plugin TrainCarts plugin instance
     * @param root Root OfflineDataBlock where path mutexes are stored as children
     * @return List of decoded path mutexes
     */
    public static List<MutexZonePath> readAll(TrainCarts plugin, OfflineDataBlock root) {
        List<OfflineDataBlock> pathDataBlockList = root.findChildren("path-mutex");
        if (pathDataBlockList.isEmpty()) {
            return Collections.emptyList();
        }

        List<MutexZonePath> paths = new ArrayList<>(pathDataBlockList.size());
        for (OfflineDataBlock pathDataBlock : pathDataBlockList) {
            // Read the mutex zone path data
            final MutexZonePath path;
            try (DataInputStream stream = pathDataBlock.readData()) {
                int version = Util.readVariableLengthInt(stream); // Might be useful in the future
                if (version == 1) {
                    OfflineBlock signBlock = OfflineBlock.readFrom(stream);
                    Optional<MutexZoneCacheWorld.PathingSignKey> key = MutexZoneCacheWorld.PathingSignKey.readFrom(plugin, stream);
                    if (!key.isPresent()) {
                        continue; // Skip if train doesn't exist, or sign is of an unknown type
                    }

                    OptionsBuilder options = createOptions();
                    options.type(MutexZoneSlotType.readFrom(stream));
                    options.name(stream.readUTF());
                    options.statement(stream.readUTF());
                    options.spacing(stream.readDouble());
                    options.maxDistance(stream.readDouble());
                    path = new MutexZonePath(plugin, signBlock, key.get(),
                            null, // Sign is lazily initialized when needed
                            options);

                    // Read all the blocks
                    int blockCount = Util.readVariableLengthInt(stream);
                    if (blockCount == 0) {
                        throw new IllegalStateException("Pathing mutex at " + signBlock + " has zero rail blocks");
                    }
                    for (int i = 0; i < blockCount; i++) {
                        path.addBlock(IntVector3.read(stream));
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported data version: " + version);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load pathing mutex", t);
                continue;
            }

            paths.add(path);
        }
        return Collections.unmodifiableList(paths);
    }

    public void writeTo(OfflineDataBlock root) {
        try {
            root.addChildOrAbort("path-mutex", stream -> {
                Util.writeVariableLengthInt(stream, 1); // Version

                OfflineBlock.writeTo(stream, signBlock);
                if (!key.writeTo(plugin, stream)) {
                    throw new OfflineDataBlock.AbortChildException();
                }

                type.writeTo(stream);
                stream.writeUTF(slot.getName());
                stream.writeUTF(statement);
                stream.writeDouble(spacing);
                stream.writeDouble(getMaxDistance());

                // Write all the blocks in the same order they were added
                Util.writeVariableLengthInt(stream, blocks.size());
                for (IntVector3 block : blocks) {
                    block.write(stream);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pathing mutex at " + signBlock, t);
        }
    }

    public String getTrainName() {
        return key.trainProperties.getTrainName();
    }

    public boolean isByGroup(MinecartGroup group) {
        return key.trainProperties == group.getProperties();
    }

    @Override
    protected void addToWorld(MutexZoneCacheWorld world) {
        world.byPathingKey.put(key, this);
        this.world = world;
    }

    public void remove() {
        if (world.byPathingKey.remove(key, this)) {
            world.remove(this);
        }
    }

    @Override
    public double getSpacing(MinecartGroup group) {
        return isByGroup(group) ? 0.0 : spacing;
    }

    /**
     * Gets the maximum distance from the beginning of this path that the mutex exists at
     *
     * @return Maximum distance
     */
    public double getMaxDistance() {
        return maxDistance;
    }

    public void addBlock(IntVector3 block) {
        if (!blocks.add(block)) {
            return;
        }

        // Update cubes for hit-testing
        updateBB(block);

        boolean chunksChanged = false;
        if (blocks.size() == 1) {
            // Store initial block
            minX = maxX = block.x;
            minY = maxY = block.y;
            minZ = maxZ = block.z;
            minCX = maxCX = block.getChunkX();
            minCZ = maxCZ = block.getChunkZ();
            chunksChanged = true;
        } else {
            // Update cuboid
            if (block.x < minX) {
                minX = block.x;
            } else if (block.x > maxX) {
                maxX = block.x;
            }
            if (block.y < minY) {
                minY = block.y;
            } else if (block.y > maxY) {
                maxY = block.y;
            }
            if (block.z < minZ) {
                minZ = block.z;
            } else if (block.z > maxZ) {
                maxZ = block.z;
            }

            // Update chunk area. If changed, refresh in mapping
            int cx = block.getChunkX();
            int cz = block.getChunkZ();
            if (cx < minCX) {
                minCX = cx;
                chunksChanged = true;
            } else if (cx > maxCX) {
                maxCX = cx;
                chunksChanged = true;
            }
            if (cz < minCZ) {
                minCZ = cz;
                chunksChanged = true;
            } else if (cz > maxCZ) {
                maxCZ = cz;
                chunksChanged = true;
            }
        }

        // If changed, and the sign is still mapped (sanity check), update chunks
        if (chunksChanged && world != null && world.byPathingKey.get(key) == this) {
            world.addNewChunks(this);
        }
    }

    private void updateBB(IntVector3 coord) {
        cubes.add(OrientedBoundingBox.naturalFromTo(new Vector(coord.x, coord.y, coord.z),
                                                    new Vector(coord.x + 1.0, coord.y + 1.0, coord.z + 1.0)));
    }

    @Override
    public boolean containsBlock(IntVector3 block) {
        return blocks.contains(block);
    }

    @Override
    public boolean isNearby(IntVector3 block, int radius) {
        return block.x>=(minX-radius) && block.y>=(minY-radius) && block.z>=(minZ-radius) &&
               block.x<=(maxX+radius) && block.y<=(maxY+radius) && block.z<=(maxZ+radius);
    }

    @Override
    public void forAllContainedChunks(ChunkCoordConsumer action) {
        //TODO: Is it more optimal to track chunk coordinates as well?
        //      This method will include more chunks than needed for diagonal paths
        int chunkMinX = minCX;
        int chunkMaxX = maxCX;
        int chunkMinZ = minCZ;
        int chunkMaxZ = maxCZ;
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                action.accept(cx, cz);
            }
        }
    }

    @Override
    public long showDebugColorSeed() {
        return signBlock.hashCode();
    }

    @Override
    public void showDebug(Player player, Color color) {
        DebugParticles particles = DebugParticles.of(player);
        for (IntVector3 block : blocks) {
            Vector pos = MathUtil.addToVector(block.toVector(), 0.5, 0.5, 0.5);
            particles.point(color, pos);

            // Too laggy.
            /*
            DebugToolUtil.showCubeParticles(player, color,
                    block.x, block.y, block.z,
                    block.x + 1.0, block.y + 1.0, block.z + 1.0);
            */
        }
    }

    @Override
    protected void setLeversDown(boolean down) {
        if (sign == null) {
            sign = plugin.getTrackedSignLookup().getTrackedSign(key.uniqueKey);
            if (sign != null) {
                sign.setOutput(down);
            }
        }
    }

    @Override
    public void onUsed(MinecartGroup group) {
        if (isByGroup(group)) {
            tickLastUsed = CommonUtil.getServerTicks();
        }
    }

    public boolean isExpired(int expireTick) {
        if (key.trainProperties.isRemoved()) {
            return true; // Train removed, expired instantly
        } else if (!key.trainProperties.isLoaded()) {
            tickLastUsed = -1;
            return false; // Wait until train loads in again
        } else if (tickLastUsed == -1) {
            tickLastUsed = CommonUtil.getServerTicks();
            return false; // Loaded, track expiry again from this point
        } else {
            return tickLastUsed < expireTick; // Expire after not used for some time
        }
    }

    @Override
    public double hitTest(double posX, double posY, double posZ, double motX, double motY, double motZ) {
        //TODO: Oh god...the performance :(
        double result = Double.MAX_VALUE;
        for (OrientedBoundingBox bb : cubes) {
            result = Math.min(result, bb.hitTest(posX, posY, posZ, motX, motY, motZ));
        }
        return result;
    }

    public static OptionsBuilder createOptions() {
        return new OptionsBuilder();
    }

    public static final class OptionsBuilder {
        private double spacing = 1.0;
        private double maxDistance = 64.0;
        private MutexZoneSlotType type = MutexZoneSlotType.NORMAL;
        private String name = "";
        private String statement = "";

        private OptionsBuilder() {
        }

        public double spacing() {
            return spacing;
        }

        public OptionsBuilder spacing(double spacing) {
            this.spacing = MathUtil.clamp(spacing, 0.0, TCConfig.maxMutexSize);
            return this;
        }

        public double maxDistance() {
            return maxDistance;
        }

        public OptionsBuilder maxDistance(double maxDistance) {
            this.maxDistance = MathUtil.clamp(maxDistance, 0.0, TCConfig.maxMutexSize);
            return this;
        }

        public OptionsBuilder type(MutexZoneSlotType type) {
            this.type = type;
            return this;
        }

        public OptionsBuilder name(String name) {
            this.name = name;
            return this;
        }

        public OptionsBuilder statement(String statement) {
            this.statement = statement;
            return this;
        }
    }
}
