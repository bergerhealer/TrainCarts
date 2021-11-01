package com.bergerkiller.bukkit.tc.offline.world;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.World;

/**
 * Maps a value by an OfflineWorld argument. Has optimized methods to deal
 * with a loaded Bukkit world argument.
 * 
 * @param <V> Value type
 */
public class OfflineWorldMap<V> {
    private final Map<OfflineWorld, V> values = new IdentityHashMap<OfflineWorld, V>();
    private OfflineWorld lastGetKey = OfflineWorld.NONE;
    private V lastGetValue = null;

    public V get(OfflineWorld world) {
        if (world == lastGetKey) {
            return lastGetValue;
        } else {
            lastGetKey = world;
            return lastGetValue = values.get(world);
        }
    }

    public V get(World world) {
        if (world == lastGetKey.getLoadedWorld()) {
            return lastGetValue;
        } else {
            OfflineWorld oWorld = OfflineWorld.of(world);
            lastGetKey = oWorld;
            return lastGetValue = values.get(oWorld);
        }
    }

    public V getOrDefault(OfflineWorld world, V defaultValue) {
        if (world == lastGetKey) {
            return lastGetValue;
        } else {
            return values.getOrDefault(world, defaultValue);
        }
    }

    public V getOrDefault(World world, V defaultValue) {
        if (world == lastGetKey.getLoadedWorld()) {
            return lastGetValue;
        } else {
            return values.getOrDefault(OfflineWorld.of(world), defaultValue);
        }
    }

    public V remove(OfflineWorld world) {
        lastGetKey = OfflineWorld.NONE;
        return values.remove(world);
    }

    public V remove(World world) {
        return remove(OfflineWorld.of(world));
    }

    public V put(OfflineWorld world, V value) {
        lastGetKey = OfflineWorld.NONE;
        return values.put(world, value);
    }

    public V put(World world, V value) {
        return put(OfflineWorld.of(world), value);
    }

    public V computeIfAbsent(OfflineWorld world, Function<OfflineWorld, ? extends V> mappingFunction) {
        if (lastGetKey == world) {
            return lastGetValue;
        } else {
            return values.computeIfAbsent(world, mappingFunction);
        }
    }

    public V computeIfAbsent(World world, Function<World, ? extends V> mappingFunction) {
        if (lastGetKey.getLoadedWorld() == world) {
            return lastGetValue;
        } else {
            return values.computeIfAbsent(OfflineWorld.of(world), unused -> mappingFunction.apply(world));
        }
    }

    public Collection<V> values() {
        return values.values();
    }

    public void clear() {
        lastGetKey = OfflineWorld.NONE;
        values.clear();
    }
}
