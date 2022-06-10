package com.bergerkiller.bukkit.tc.controller.components;

import java.util.IdentityHashMap;
import java.util.Iterator;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class SignController implements LibraryComponent, Listener {
    private final TrainCarts plugin;
    private final SignControllerWorld NONE = new SignControllerWorld(null); // Dummy
    private final IdentityHashMap<World, SignControllerWorld> byWorld = new IdentityHashMap<>();
    private boolean disabled = false;
    private SignControllerWorld byWorldLastGet = NONE;

    public SignController(TrainCarts plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        this.plugin.register(this);
    }

    @Override
    public void disable() {
        byWorld.values().forEach(SignControllerWorld::clear);
        byWorld.clear();
        byWorldLastGet = NONE;
        disabled = true;
    }

    /**
     * Gets the SignController for a certain World
     *
     * @param world
     * @return SignController
     */
    public SignControllerWorld forWorld(World world) {
        SignControllerWorld c = byWorldLastGet;
        if (c.getWorld() == world) {
            return c;
        } else {
            return byWorldLastGet = byWorld.computeIfAbsent(world, w -> {
                if (disabled) {
                    throw new IllegalStateException("Can't use SignController, Traincarts is disabled!");
                } else {
                    SignControllerWorld controller = new SignControllerWorld(w);
                    for (Chunk loadedChunk : w.getLoadedChunks()) {
                        controller.loadChunk(loadedChunk);
                    }
                    return controller;
                }
            });
        }
    }

    private SignControllerWorld tryGetForWorld(World world) {
        SignControllerWorld c = byWorldLastGet;
        if (c.getWorld() != world) {
            c = byWorld.get(world);
            if (c != null) {
                byWorldLastGet = c;
            }
        }
        return c;
    }

    /**
     * Deletes old SignController instances from memory that are for Worlds that have unloaded.
     * Avoids potential memory leaks.
     */
    public void cleanupUnloaded() {
        for (Iterator<SignControllerWorld> iter = byWorld.values().iterator(); iter.hasNext();) {
            SignControllerWorld controller = iter.next();
            if (!controller.isValid()) {
                iter.remove();
                byWorldLastGet = NONE;
                controller.clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onWorldInit(WorldInitEvent event) {
        forWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onWorldUnload(WorldUnloadEvent event) {
        // We might be doing some sign logic during unloading, so don't remove cache right away
        final World world = event.getWorld();
        CommonUtil.nextTick(() -> {
            SignControllerWorld controller = byWorld.remove(world);
            if (controller != null) {
                controller.clear();
                byWorldLastGet = NONE;
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChunkLoad(ChunkLoadEvent event) {
        SignControllerWorld controller = forWorld(event.getWorld());
        if (controller != null) {
            controller.loadChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChunkUnload(ChunkUnloadEvent event) {
        SignControllerWorld controller = tryGetForWorld(event.getWorld());
        if (controller != null) {
            controller.unloadChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        SignControllerWorld controller = forWorld(block.getWorld());
        
    }
}
