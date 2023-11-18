package com.bergerkiller.bukkit.tc.signactions.mutex;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A mutex zone that is a cuboid made between two block positions
 */
public class MutexZoneCuboid extends MutexZone {
    public final IntVector3 start;
    public final IntVector3 end;
    private final OrientedBoundingBox bb;

    protected MutexZoneCuboid(OfflineBlock signBlock, boolean signFront, IntVector3 start, IntVector3 end, MutexZoneSlotType type, String name, String statement) {
        super(signBlock, signFront, type, name, statement);
        this.start = start;
        this.end = end;
        this.bb = OrientedBoundingBox.naturalFromTo(new Vector(start.x, start.y, start.z),
                                                    new Vector(end.x + 1.0, end.y + 1.0, end.z + 1.0));
    }

    @Override
    public boolean containsBlock(IntVector3 block) {
        return block.x >= start.x && block.y >= start.y && block.z >= start.z &&
               block.x <= end.x && block.y <= end.y && block.z <= end.z;
    }

    @Override
    public boolean containsBlock(Block block) {
        return block.getX() >= start.x && block.getY() >= start.y && block.getZ() >= start.z &&
               block.getX() <= end.x && block.getY() <= end.y && block.getZ() <= end.z;
    }

    @Override
    public boolean isNearby(IntVector3 block, int radius) {
        return block.x>=(start.x-radius) && block.y>=(start.y-radius) && block.z>=(start.z-radius) &&
               block.x<=(end.x + radius) && block.y<=(end.y + radius) && block.z<=(end.z + radius);
    }

    @Override
    public void forAllContainedChunks(ChunkCoordConsumer action) {
        int chunkMinX = start.getChunkX();
        int chunkMaxX = end.getChunkX();
        int chunkMinZ = start.getChunkZ();
        int chunkMaxZ = end.getChunkZ();
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                action.accept(cx, cz);
            }
        }
    }

    @Override
    public long showDebugColorSeed() {
        return MathUtil.longHashToLong(start.hashCode(), end.hashCode());
    }

    @Override
    public void showDebug(Player player, Color color) {
        double x1 = start.x;
        double y1 = start.y;
        double z1 = start.z;
        double x2 = end.x + 1.0;
        double y2 = end.y + 1.0;
        double z2 = end.z + 1.0;
        DebugTool.showCube(player, color, x1, y1, z1, x2, y2, z2);
    }

    @Override
    public double hitTest(double posX, double posY, double posZ,
                          double motX, double motY, double motZ
    ) {
        return bb.hitTest(posX, posY, posZ, motX, motY, motZ);
    }
}
