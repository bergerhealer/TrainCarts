package com.bergerkiller.bukkit.tc.offline.sign;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

class OfflineSignStoreListener implements Listener {
    private final OfflineSignStore store;

    public OfflineSignStoreListener(OfflineSignStore store) {
        this.store = store;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWorldInit(WorldInitEvent event) {
        store.loadSignsOnWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        store.verifySignsInChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        store.unloadSignsOnWorld(event.getWorld());
    }
}
