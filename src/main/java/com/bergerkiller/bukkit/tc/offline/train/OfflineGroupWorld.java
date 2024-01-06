package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A collection of offline groups stored on a particular World
 */
public abstract class OfflineGroupWorld implements Iterable<OfflineGroup> {
    protected final OfflineWorld world;

    public OfflineGroupWorld(OfflineWorld world) {
        this.world = world;
    }

    /**
     * Gets all the offline groups that exist on this world
     *
     * @return Collection of offline groups
     */
    public abstract Collection<OfflineGroup> getGroups();

    /**
     * OfflineWorld these groups are on
     *
     * @return OfflineWorld
     */
    public OfflineWorld getWorld() {
        return this.world;
    }

    @Override
    public Iterator<OfflineGroup> iterator() {
        return getGroups().iterator();
    }

    public boolean isEmpty() {
        return getGroups().isEmpty();
    }

    public int totalGroupCount() {
        return getGroups().size();
    }

    public int totalMemberCount() {
        int count = 0;
        for (OfflineGroup group : getGroups()) {
            count += group.members.length;
        }
        return count;
    }

    public static OfflineGroupWorld snapshot(OfflineWorld world, Collection<OfflineGroup> groups) {
        final List<OfflineGroup> snapshotGroups = Collections.unmodifiableList(new ArrayList<>(groups));
        return new OfflineGroupWorld(world) {
            @Override
            public Collection<OfflineGroup> getGroups() {
                return snapshotGroups;
            }
        };
    }

    public static List<OfflineGroupWorld> snapshot(Map<OfflineWorld, List<OfflineGroup>> groupsByWorld) {
        List<OfflineGroupWorld> worldsList = new ArrayList<>(groupsByWorld.size());
        for (Map.Entry<OfflineWorld, List<OfflineGroup>> entry : groupsByWorld.entrySet()) {
            worldsList.add(snapshot(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(worldsList);
    }

    public static List<OfflineGroupWorld> mergeSnapshots(List<OfflineGroupWorld> first, List<OfflineGroupWorld> second) {
        if (first.isEmpty()) {
            return second;
        } else if (second.isEmpty()) {
            return first;
        } else {
            Map<OfflineWorld, List<OfflineGroup>> merged = new IdentityHashMap<>(Math.max(first.size(), second.size()));
            for (OfflineGroupWorld world : first) {
                merged.computeIfAbsent(world.getWorld(), w -> new ArrayList<>(world.getGroups().size()))
                        .addAll(world.getGroups());
            }
            for (OfflineGroupWorld world : second) {
                merged.computeIfAbsent(world.getWorld(), w -> new ArrayList<>(world.getGroups().size()))
                        .addAll(world.getGroups());
            }
            return snapshot(merged);
        }
    }
}
