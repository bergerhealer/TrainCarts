package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.UUID;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZone {
    public final UUID world;
    public final IntVector3 block;
    public final IntVector3 start;
    public final IntVector3 end;

    private MutexZone(UUID world, IntVector3 block, int dx, int dy, int dz) {
        this.world = world;
        this.block = block;
        this.start = new IntVector3(block.x - dx, block.y - dy, block.z - dz);
        this.end = new IntVector3(block.x + dx, block.y + dy, block.z + dz);
    }

    public boolean containsBlock(UUID world, IntVector3 block) {
        return world.equals(this.world) &&
                block.x >= start.x && block.y >= start.y && block.z >= start.z &&
                block.x <= end.x && block.y <= end.y && block.z <= end.z;
    }

    public boolean isNearby(UUID world, IntVector3 block) {
        return world.equals(this.world); //TODO: Also check block
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
                if (parts.length >= 1) {
                    dx = dy = dz = ParseUtil.parseInt(parts[0], dx);
                } else if (parts.length >= 2) {
                    dx = dz = ParseUtil.parseInt(parts[0], dx);
                    dy = ParseUtil.parseInt(parts[1], dy);
                } else if (parts.length >= 3) {
                    dx = ParseUtil.parseInt(parts[0], dx);
                    dz = ParseUtil.parseInt(parts[1], dx);
                    dy = ParseUtil.parseInt(parts[2], dy);
                }
            }
        }

        IntVector3 pos = new IntVector3(info.getBlock());
        return new MutexZone(info.getWorld().getUID(), pos, dx, dy, dz);
    }
}
