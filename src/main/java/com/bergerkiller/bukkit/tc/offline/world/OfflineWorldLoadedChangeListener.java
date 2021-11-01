package com.bergerkiller.bukkit.tc.offline.world;

import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Listener-based tracker of the loaded world of an OfflineWorld instance
 */
public final class OfflineWorldLoadedChangeListener {

    public void enable(Plugin plugin) {
        OfflineWorld.setLoadedWorldSupplier(WorldSupplier::new);
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onWorldInit(WorldInitEvent event) {
                Supplier<World> supplier = OfflineWorld.of(event.getWorld()).loadedWorldSupplier;
                if (supplier instanceof WorldSupplier) {
                    ((WorldSupplier) supplier).loadedWorld = event.getWorld();
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onWorldUnload(WorldUnloadEvent event) {
                Supplier<World> supplier = OfflineWorld.of(event.getWorld()).loadedWorldSupplier;
                if (supplier instanceof WorldSupplier) {
                    ((WorldSupplier) supplier).loadedWorld = null;
                }
            }
        }, plugin);
    }

    public void disable() {
        OfflineWorld.setLoadedWorldSupplier(OfflineWorld.toWorldSupplierFuncDefault);
    }

    private static final class WorldSupplier implements Supplier<World> {
        public World loadedWorld;

        public WorldSupplier(UUID worldUUID) {
            this.loadedWorld = Bukkit.getWorld(worldUUID);
        }

        @Override
        public World get() {
            return this.loadedWorld;
        }
    }
}
