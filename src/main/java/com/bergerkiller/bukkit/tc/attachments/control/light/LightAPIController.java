package com.bergerkiller.bukkit.tc.attachments.control.light;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

import ru.beykerykt.lightapi.LightAPI;
import ru.beykerykt.lightapi.LightType;
import ru.beykerykt.lightapi.chunks.ChunkInfo;

public class LightAPIController {
    private static final Map<World, LightAPIController> _blockLightControllers = new HashMap<>();
    private static final Map<World, LightAPIController> _skyLightControllers = new HashMap<>();
    private static SyncTask _task;

    private static void schedule(LightAPIController controller) {
        if (!controller.syncPending) {
            controller.syncPending = true;
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
            controller = new LightAPIController(world, skyLight ? LightType.SKY : LightType.BLOCK);
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

    private final World world;
    private final LightType lightType;
    private final ListMultimap<IntVector3, Integer> states;
    private final Map<IntVector3, List<Integer>> dirty;
    private boolean syncPending;

    private LightAPIController(World world, LightType lightType) {
        this.world = world;
        this.lightType = lightType;
        this.states = LinkedListMultimap.create(100);
        this.dirty = new HashMap<>();
        this.syncPending = false;
    }

    public void add(IntVector3 position, int level) {
        List<Integer> list = states.get(position);
        if (list.isEmpty() || Collections.max(list).intValue() < level) {
            dirty.put(position, list);
            schedule(this);
        }
        list.add(Integer.valueOf(level));
    }

    public void remove(IntVector3 position, int level) {
        List<Integer> list = states.get(position);
        if (list.remove(Integer.valueOf(level)) && (list.isEmpty() || Collections.max(list).intValue() < level)) {
            dirty.put(position, list);
            schedule(this);
        }
    }

    public void move(IntVector3 old_position, IntVector3 new_position, int level) {
        remove(old_position, level);
        add(new_position, level);
    }

    public boolean sync() {
        syncPending = false;
        if (dirty.isEmpty()) {
            return false;
        }
        final boolean async = true;
        Set<ChunkInfo> chunks = new HashSet<>();

        // First process all dirty entries that need light removed
        for (Map.Entry<IntVector3, List<Integer>> dirty_entry : dirty.entrySet()) {
            List<Integer> levels = dirty_entry.getValue();
            if (levels.isEmpty()) {
                IntVector3 pos = dirty_entry.getKey();
                LightAPI.deleteLight(world, pos.x, pos.y, pos.z, lightType, async);
                chunks.addAll(LightAPI.collectChunks(world, pos.x, pos.y, pos.z, lightType, 15));
            }
        }

        // Then process the dirty entries that create new light
        for (Map.Entry<IntVector3, List<Integer>> dirty_entry : dirty.entrySet()) {
            List<Integer> levels = dirty_entry.getValue();
            if (!levels.isEmpty()) {
                IntVector3 pos = dirty_entry.getKey();
                int level = Collections.max(dirty_entry.getValue()).intValue();
                LightAPI.createLight(world, pos.x, pos.y, pos.z, lightType, level, async);
                chunks.addAll(LightAPI.collectChunks(world, pos.x, pos.y, pos.z, lightType, level));
            }
        }

        // Refresh chunks and done
        dirty.clear();
        for (ChunkInfo chunk : chunks) {
            LightAPI.updateChunk(chunk, lightType);
        }
        return true;
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
