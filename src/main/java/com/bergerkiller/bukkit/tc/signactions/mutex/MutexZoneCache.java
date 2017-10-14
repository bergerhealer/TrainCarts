package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZoneCache {
    private static final BlockMap<MutexZone> zones = new BlockMap<MutexZone>();

    /**
     * Loads the mutex zones in a Chunk by iterating the signs within
     * 
     * @param chunk
     */
    public static void loadChunk(Chunk chunk) {
        for (BlockState state : WorldUtil.getBlockStates(chunk)) {
            if (!(state instanceof Sign)) {
                return;
            }
        }
    }

    public static void addMutexSign(SignActionEvent info) {
        zones.put(info.getBlock(), MutexZone.fromSign(info));
    }

    public static void removeMutexSign(SignActionEvent info) {
        zones.remove(info.getBlock());
    }

    /**
     * Finds a mutex zone at a particular block
     * 
     * @param world
     * @param block
     * @return mutex zone, null if not found
     */
    public static MutexZone find(UUID world, IntVector3 block) {
        for (MutexZone zone : zones.values()) {
            if (zone.containsBlock(world, block)) {
                return zone;
            }
        }
        return null;
    }
}
