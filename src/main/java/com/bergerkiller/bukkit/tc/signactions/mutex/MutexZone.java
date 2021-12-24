package com.bergerkiller.bukkit.tc.signactions.mutex;

import org.bukkit.Location;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZone {
    public final OfflineBlock signBlock;
    public final IntVector3 start;
    public final IntVector3 end;
    public final String statement;
    public final MutexZoneSlot slot;

    private MutexZone(OfflineBlock signBlock, IntVector3 start, IntVector3 end, String name, String statement) {
        this.signBlock = signBlock;
        this.statement = statement;
        this.start = start;
        this.end = end;
        this.slot = MutexZoneCache.findSlot(name, this);
    }

    public boolean containsBlock(IntVector3 block) {
        return block.x >= start.x && block.y >= start.y && block.z >= start.z &&
               block.x <= end.x && block.y <= end.y && block.z <= end.z;
    }

    public boolean containsBlock(Block block) {
        return block.getX() >= start.x && block.getY() >= start.y && block.getZ() >= start.z &&
               block.getX() <= end.x && block.getY() <= end.y && block.getZ() <= end.z;
    }

    public boolean isNearby(IntVector3 block, int radius) {
        return block.x>=(start.x-radius) && block.y>=(start.y-radius) && block.z>=(start.z-radius) &&
               block.x<=(end.x + radius) && block.y<=(end.y + radius) && block.z<=(end.z + radius);
    }

    public Block getSignBlock() {
        return this.signBlock.getLoadedBlock();
    }

    public static IntVector3 getPosition(SignActionEvent info) {
        Location middlePos = info.getCenterLocation();
        if (middlePos != null) {
            return new IntVector3(middlePos);
        } else {
            return new IntVector3(info.getBlock());
        }
    }

    protected void setLevers(boolean down) {
        Block signBlock = getSignBlock();
        if (signBlock != null) {
            signBlock.getChunk();
            BlockData data = WorldUtil.getBlockData(signBlock);
            if (MaterialUtil.ISSIGN.get(data)) {
                BlockUtil.setLeversAroundBlock(signBlock.getRelative(data.getAttachedFace()), down);
            }
        }
    }

    public static MutexZone create(OfflineWorld world, IntVector3 signPosition, MutexSignMetadata metadata) {
        return new MutexZone(world.getBlockAt(signPosition), metadata.start, metadata.end, metadata.name, metadata.statement);
    }
}
