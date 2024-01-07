package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An offline group world that is mapped in the {@link OfflineGroupManager},
 * and is live-updated as trains load and unload.
 */
public class OfflineGroupWorldLive extends OfflineGroupWorld {
    protected final OfflineGroupManager manager;
    private Set<OfflineGroup> groups = new HashSet<>();
    private LongHashMap<HashSet<OfflineGroup>> groupmap = new LongHashMap<>();
    private Set<UUID> minecartEntityUUIDsBeingDestroyed = new HashSet<>();
    private boolean isDuringWorldUnloadEvent = false;

    public OfflineGroupWorldLive(OfflineGroupManager manager, OfflineWorld world) {
        super(world);
        this.manager = manager;
    }

    public OfflineGroupManager getManager() {
        return this.manager;
    }

    @Override
    public Collection<OfflineGroup> getGroups() {
        return this.groups;
    }

    /**
     * Creates an unmodifiable snapshot of this live-updated offline group world
     *
     * @return Snapshot OfflineGroupWorld
     */
    public OfflineGroupWorld createSnapshot() {
        return snapshot(world, groups);
    }

    public void add(OfflineGroup group) {
        this.groups.add(group);
        group.forAllChunks(chunk -> {
            if (!group.getLoadedChunks().contains(chunk)) {
                getOrCreateChunk(chunk).add(group);
            }
        });
    }

    public void setIsDuringWorldUnloadEvent(boolean isDuringWorldUnloadEvent) {
        this.isDuringWorldUnloadEvent = isDuringWorldUnloadEvent;
    }

    public boolean canRestoreGroups() {
        return !this.isDuringWorldUnloadEvent && this.world.isLoaded();
    }

    /**
     * Destroys the minecart entities of an offline group. Loads the chunks the group resides
     * in asynchronously if needed. Returns whether the group was fully destroyed or not.
     * If not destroyed, the offline group is removed regardless.
     *
     * @param world The world this store is for
     * @param group The group to destroy the minecarts of
     * @return Future completed with either true (destroyed them) or false (not found)
     */
    public CompletableFuture<Boolean> destroyAsync(final World world, final OfflineGroup group) {
        final ChunkFutureProvider futureProvider = ChunkFutureProvider.of(TrainCarts.plugin);

        // This makes sure the group doesn't get restored as a train in the middle of removing
        group.setBeingRemoved();

        // Set of minecart entity UUID's to find. Done when empty.
        final List<UUID> minecartEntityUUIDs = Stream.of(group.members)
                .map(m -> m.entityUID)
                .collect(Collectors.toList());
        final Set<UUID> minecartEntityUUIDsRemaining = new HashSet<>(minecartEntityUUIDs);
        minecartEntityUUIDsBeingDestroyed.addAll(minecartEntityUUIDs);

        // This future is completed once done
        final CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();

        // Wait until entities in the chunks have loaded. When they are, find and destroy carts in them
        // For this we use ForcedChunk instances to keep the chunks loaded while we work with them
        // They're closed again once each chunk is processed
        final List<ForcedChunk> chunks = group.forceLoadChunks(world);
        @SuppressWarnings("rawtypes")
        final CompletableFuture[] chunkLoadEntitiesFuture = chunks.stream()
                .map(forcedChunk -> {
                    return futureProvider.whenEntitiesLoaded(world, forcedChunk.getX(), forcedChunk.getZ()).thenAccept(chunk -> {
                        try {
                            if (!minecartEntityUUIDsRemaining.isEmpty()) {
                                for (Entity e : new ArrayList<>(WorldUtil.getEntities(chunk))) {
                                    if (minecartEntityUUIDsRemaining.remove(e.getUniqueId())) {
                                        e.remove();
                                        if (minecartEntityUUIDsRemaining.isEmpty()) {
                                            result.complete(Boolean.TRUE);
                                            break;
                                        }
                                    }
                                }
                            }
                        } finally {
                            forcedChunk.close();
                        }
                    });
                })
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(chunkLoadEntitiesFuture).thenAccept(u -> result.complete(Boolean.FALSE));

        // Run the asynchronous removal process, once done, remove the group from the store
        return result.thenApply(found -> {
            // Remove from mappings
            remove(group);

            // Avoid stale properties
            TrainPropertiesStore.remove(group.name);

            // Cleanup, no longer needed
            minecartEntityUUIDsBeingDestroyed.removeAll(minecartEntityUUIDs);

            return found;
        });
    }

    /**
     * Gets whether a particular Minecart is in the process of being destroyed (asynchronously)
     *
     * @param minecartUUID
     * @return True if this Minecart is being destroyed
     */
    public boolean isDestroyingMinecart(UUID minecartUUID) {
        return minecartEntityUUIDsBeingDestroyed.contains(minecartUUID);
    }

    public void remove(OfflineGroup group) {
        this.groups.remove(group);
        group.forAllChunks(chunk -> {
            Set<OfflineGroup> groups = getOrCreateChunk(chunk);
            if (groups != null) {
                groups.remove(group);
                if (groups.isEmpty()) {
                    this.groupmap.remove(chunk);
                }
            }
        });
    }

    public boolean removeCart(UUID memberUUID) {
        for (OfflineGroup group : groups) {
            for (OfflineMember member : group.members) {
                if (member.entityUID.equals(memberUUID)) {
                    // Create a new list of members that excludes the removed cart
                    ArrayList<OfflineMember> newMembers = new ArrayList<>();
                    for (OfflineMember m : group.members) {
                        if (!m.entityUID.equals(memberUUID)) {
                            newMembers.add(m);
                        }
                    }

                    // Undo previous registration
                    remove(group);

                    // Add a new group with the updated members, if the list isn't empty
                    if (!newMembers.isEmpty()) {
                        add(new OfflineGroup(group.name, group.world, newMembers, (cgroup, cmember) -> cmember));
                    }

                    // Finished
                    return true;
                }
            }
        }
        return false;
    }

    public final OfflineGroup remove(String groupName) {
        for (OfflineGroup group : groups) {
            if (group.name.equals(groupName)) {
                remove(group);
                return group;
            }
        }
        return null;
    }

    public Set<OfflineGroup> removeFromChunk(Chunk chunk) {
        return removeFromChunk(chunk.getX(), chunk.getZ());
    }

    public Set<OfflineGroup> removeFromChunk(int x, int z) {
        return removeFromChunk(MathUtil.longHashToLong(x, z));
    }

    public Set<OfflineGroup> removeFromChunk(long chunk) {
        Set<OfflineGroup> rval = this.groupmap.remove(chunk);
        if (rval != null) {
            for (OfflineGroup group : rval) {
                group.getLoadedChunks().add(chunk);
            }
        }
        return rval;
    }

    public Set<OfflineGroup> getFromChunk(Chunk chunk) {
        return this.getFromChunk(chunk.getX(), chunk.getZ());
    }

    public Set<OfflineGroup> getFromChunk(int x, int z) {
        return this.getFromChunk(MathUtil.longHashToLong(x, z));
    }

    public Set<OfflineGroup> getFromChunk(long chunk) {
        return this.groupmap.get(chunk);
    }

    public Set<OfflineGroup> getOrCreateChunk(long chunk) {
        HashSet<OfflineGroup> rval = this.groupmap.get(chunk);
        if (rval == null) {
            rval = new HashSet<>(1);
            this.groupmap.put(chunk, rval);
        }
        return rval;
    }
}
