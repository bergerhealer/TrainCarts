package com.bergerkiller.bukkit.tc.attachments.control.light;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.TrainCarts;

public abstract class LightAPIController {
    private static final Map<World, LightAPIController> _blockLightControllers = new HashMap<>();
    private static final Map<World, LightAPIController> _skyLightControllers = new HashMap<>();
    private static SyncTask _task;
    private boolean syncPending;

    protected LightAPIController() {
        syncPending = false;
    }

    protected void schedule() {
        if (!syncPending) {
            syncPending = true;
            if (_task == null) {
                _task = new SyncTask();
                if (_task.getPlugin().isEnabled()) {
                    _task.start(1, 1);
                }
            }
        }
    }

    /**
     * Gets a controller instance for a World
     * 
     * @param world The world on which light is manipulated
     * @param skyLight Whether to change the sky light (true) or block light (false)
     * @return controller
     */
    public static LightAPIController get(World world, boolean skyLight) {
        Map<World, LightAPIController> map = skyLight ? _skyLightControllers : _blockLightControllers;
        LightAPIController controller = map.get(world);
        if (controller == null) {
            try {
                controller = skyLight ? LightAPIControllerImpl.forSkyLight(world) : LightAPIControllerImpl.forBlockLight(world);
            } catch (Throwable t) {
                Plugin plugin = Bukkit.getPluginManager().getPlugin("LightAPI");
                if (plugin == null) {
                    // Not loaded
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler: LightAPI plugin is not enabled!");
                } else if (plugin.getDescription().getMain().equals("ru.beykerykt.minecraft.lightapi.bukkit.impl.BukkitPlugin")) {
                    // LightAPI is used instead of LightAPI-Fork - not supported!
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler: LightAPI is installed, but you need LightAPI-fork instead!");
                } else {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler", t);
                }
                controller = LightAPIControllerUnavailable.INSTANCE;
            }
            map.put(world, controller);
        }
        return controller;
    }

    public static void disableWorld(World world) {
        _blockLightControllers.remove(world);
        _skyLightControllers.remove(world);
    }

    public static void disable() {
        _blockLightControllers.clear();
        _skyLightControllers.clear();
        Task.stop(_task);
        _task = null;
    }

    public abstract void add(IntVector3 position, int level);

    public abstract void remove(IntVector3 position, int level);

    public abstract void move(IntVector3 old_position, IntVector3 new_position, int level);

    protected abstract boolean onSync();

    public final boolean sync() {
        syncPending = false;
        return onSync();
    }

    private static class SyncTask extends Task {
        private int ticksIdle = 0;

        public SyncTask() {
            super(TrainCarts.plugin);
        }

        @Override
        public void run() {
            boolean busy = false;
            for (LightAPIController controller : _blockLightControllers.values()) {
                busy |= controller.sync();
            }
            for (LightAPIController controller : _skyLightControllers.values()) {
                busy |= controller.sync();
            }
            if (busy) {
                ticksIdle = 0;
            } else if (++ticksIdle > 100) {
                stop();
                _task = null;
            }
        }
    }
}
