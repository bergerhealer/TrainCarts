package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZone {
    public final UUID world;
    public final IntVector3 sign;
    public final IntVector3 block;
    public final IntVector3 start;
    public final IntVector3 end;
    public final String statement;
    public final MutexZoneSlot slot;

    private MutexZone(UUID world, IntVector3 sign, IntVector3 block, String name, String statement, int dx, int dy, int dz) {
        this.world = world;
        this.sign = sign;
        this.block = block;
        this.statement = statement;
        this.start = new IntVector3(block.x - dx, block.y - dy, block.z - dz);
        this.end = new IntVector3(block.x + dx, block.y + dy, block.z + dz);
        this.slot = MutexZoneCache.findSlot(name, this);
    }

    public boolean containsBlock(UUID world, IntVector3 block) {
        return world.equals(this.world) &&
                block.x >= start.x && block.y >= start.y && block.z >= start.z &&
                block.x <= end.x && block.y <= end.y && block.z <= end.z;
    }

    public boolean containsBlock(Block block) {
        return block.getWorld().getUID().equals(this.world) &&
                block.getX() >= start.x && block.getY() >= start.y && block.getZ() >= start.z &&
                block.getX() <= end.x && block.getY() <= end.y && block.getZ() <= end.z;
    }

    public boolean isNearby(UUID world, IntVector3 block, int radius) {
        if (!world.equals(this.world)) return false;

        return block.x>=(start.x-radius) && block.y>=(start.y-radius) && block.z>=(start.z-radius) &&
               block.x<=(end.x + radius) && block.y<=(end.y + radius) && block.z<=(end.z + radius);
    }

    public Block getSignBlock() {
        World world = Bukkit.getWorld(this.world);
        if (world != null) {
            return world.getBlockAt(this.sign.x, this.sign.y, this.sign.z);
        }
        return null;
    }

    public static MutexZone fromSign(SignActionEvent info) {
        // mutex dx/dy/dz
        // mutex (dx+dz)/dy
        // mutex (dx+dy+dz)
        int dx = 1;
        int dy = 2;
        int dz = 1;
        String coords = info.getLine(1);
        int firstSpace = coords.indexOf(' ');
        if (firstSpace != -1) {
            coords = coords.substring(firstSpace).trim();
            if (!coords.isEmpty()) {
                String[] parts = coords.split("/");
                if (parts.length >= 3) {
                    dx = ParseUtil.parseInt(parts[0], dx);
                    dy = ParseUtil.parseInt(parts[1], dy);
                    dz = ParseUtil.parseInt(parts[2], dz);
                } else if (parts.length >= 2) {
                    dx = dz = ParseUtil.parseInt(parts[0], dx);
                    dy = ParseUtil.parseInt(parts[1], dy);
                } else if (parts.length >= 1) {
                    dx = dy = dz = ParseUtil.parseInt(parts[0], dx);
                }
            }
        }

        // uuid of world this sign is on
        UUID worldUUID = info.getWorld().getUID();

        // third line can contain a unique name to combine multiple signs together
        // when left empty, it is an anonymous slot (only this sign)
        // when something is on it, prepend world UUID so that it is unique for this world
        String name = info.getLine(2).trim();
        if (name.isEmpty()) {
            name = null;
        } else {
            name = worldUUID.toString() + "_" + name;
        }

        // Fourth line is the statement, if any (statements?)
        String statement = info.getLine(3);

        return new MutexZone(worldUUID, new IntVector3(info.getBlock()), getPosition(info), name, statement, dx, dy, dz);
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
            BlockData data = WorldUtil.getBlockData(signBlock);
            if (MaterialUtil.ISSIGN.get(data)) {
                BlockUtil.setLeversAroundBlock(signBlock.getRelative(data.getAttachedFace()), down);
            }
        }
    }
}
