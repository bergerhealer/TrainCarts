package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;
import java.util.IdentityHashMap;

import org.bukkit.World;

/**
 * Set of world names with cached world instances to quickly
 * and efficiently check whether a world is part of the list or not.
 * Does not properly support null checks for contains().<br>
 * <br>
 * Make sure to call {@link #onWorldUnloaded(World)} when worlds unload
 * to avoid potential memory leak situations.
 */
public class ConfiguredWorldSet {
    private final HashSet<String> lowercaseNames = new HashSet<String>();
    private final IdentityHashMap<World, Boolean> byWorld = new IdentityHashMap<World, Boolean>();
    private World lastContainsWorld = null;
    private boolean lastContainsResult = false;

    public void onWorldUnloaded(World world) {
        byWorld.remove(world);
        lastContainsWorld = null;
    }

    public void clear() {
        lowercaseNames.clear();
        byWorld.clear();
        lastContainsWorld = null;
    }

    public void add(String name) {
        lowercaseNames.add(name.toLowerCase());
        byWorld.clear(); // force re-calc
        lastContainsWorld = null; // force re-calc
    }

    public boolean contains(String worldName) {
        return lowercaseNames.contains(worldName.toLowerCase());
    }

    public boolean contains(World world) {
        if (world == lastContainsWorld) {
            return lastContainsResult;
        }

        boolean result = byWorld.computeIfAbsent(world, w -> lowercaseNames.contains(w.getName().toLowerCase()));
        lastContainsWorld = world;
        lastContainsResult = result;
        return result;
    }

    public boolean isEmpty() {
        return lowercaseNames.isEmpty();
    }
}
