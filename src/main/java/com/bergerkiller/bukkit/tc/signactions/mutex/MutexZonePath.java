package com.bergerkiller.bukkit.tc.signactions.mutex;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A mutex zone covering a path trail of blocks
 */
public class MutexZonePath extends MutexZone {
    private final RailLookup.TrackedSign sign;
    private final MinecartGroup group;
    private final double spacing;
    private final double maxDistance;
    private final Set<IntVector3> blocks = new HashSet<>(128);
    private int tickLastUsed;
    private MutexZoneCacheWorld world;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int minCX, minCZ, maxCX, maxCZ;

    private final List<OrientedBoundingBox> cubes = new ArrayList<>(128);

    protected MutexZonePath(
            RailLookup.TrackedSign sign,
            MinecartGroup group,
            IntVector3 initialBlock,
            OptionsBuilder options
    ) {
        super(OfflineBlock.of(sign.signBlock), true /* unused */, options.type, options.name, options.statement);
        this.sign = sign;
        this.group = group;
        this.spacing = options.spacing;
        this.maxDistance = options.maxDistance;
        this.tickLastUsed = CommonUtil.getServerTicks();

        blocks.add(initialBlock);
        updateBB(initialBlock);
        minX = maxX = initialBlock.x;
        minY = maxY = initialBlock.y;
        minZ = maxZ = initialBlock.z;
        minCX = maxCX = initialBlock.getChunkX();
        minCZ = maxCZ = initialBlock.getChunkZ();
    }

    @Override
    protected void addToWorld(MutexZoneCacheWorld world) {
        world.byPathingKey.put(new MutexZoneCacheWorld.PathingSignKey(sign, group), this);
        this.world = world;
    }

    public void remove() {
        if (world.byPathingKey.remove(new MutexZoneCacheWorld.PathingSignKey(sign, group), this)) {
            world.remove(this);
        }
    }

    @Override
    public double getSpacing(MinecartGroup group) {
        return this.group == group ? 0.0 : spacing;
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
        boolean chunksChanged = false;
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

        // If changed, and the sign is still mapped (sanity check), update chunks
        if (chunksChanged && world.byPathingKey.get(new MutexZoneCacheWorld.PathingSignKey(sign, group)) == this) {
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
        for (IntVector3 block : blocks) {
            Vector pos = MathUtil.addToVector(block.toVector(), 0.5, 0.5, 0.5);
            PlayerUtil.spawnDustParticles(player, pos, color);

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
        sign.setOutput(down);
    }

    @Override
    public void onUsed(MinecartGroup group) {
        if (this.group == group) {
            tickLastUsed = CommonUtil.getServerTicks();
        }
    }

    public boolean isExpired(int expireTick) {
        return tickLastUsed < expireTick || group.isUnloaded();
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
