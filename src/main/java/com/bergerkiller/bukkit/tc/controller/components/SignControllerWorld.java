package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;

/**
 * Tracks the locations of signs and handles the redstone activation of them. Provides
 * an efficient lookup for use by the rail cache. A controller instance exists per
 * world and is tightly coupled with the rail lookup cache.
 */
public class SignControllerWorld {
    private final World world;
    private final OfflineWorld offlineWorld;

    SignControllerWorld(World world) {
        this.world = world;
        this.offlineWorld = OfflineWorld.of(world);
    }

    public World getWorld() {
        return this.world;
    }

    public boolean isValid() {
        return this.offlineWorld.getLoadedWorld() == this.world;
    }

    /**
     * Removes all data cached/stored in this World
     */
    void clear() {
        
    }

    /**
     * Adds data about signs stored in a particular chunk
     *
     * @param chunk
     */
    void loadChunk(Chunk chunk) {
        
    }

    /**
     * Orders to delete cached sign information about a particular chunk
     *
     * @param chunk
     */
    void unloadChunk(Chunk chunk) {
        
    }
}
