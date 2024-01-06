package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorldMap;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Stores all the trains that have unloaded. On plugin shutdown, stores all
 * trains that exist at that time. Includes mechanisms for restoring trains
 * from offline-stored data.
 */
public class OfflineGroupManager implements TrainCarts.Provider {
    private final TrainCarts plugin;
    Long lastUnloadChunk = null;
    private boolean chunkLoadReq = false;
    private boolean isRefreshingGroups = false;
    private Map<String, OfflineGroup> containedTrains = new HashMap<>();
    private HashSet<UUID> containedMinecarts = new HashSet<>();
    private final OfflineWorldMap<OfflineGroupWorldImpl> worlds = new OfflineWorldMap<OfflineGroupWorldImpl>();

    public OfflineGroupManager(TrainCarts plugin) {
        this.plugin = plugin;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return plugin;
    }

    private OfflineGroupWorldImpl get(OfflineWorld world) {
        // Note: computeIfAbsent bug!
        OfflineGroupWorldImpl map = worlds.get(world);
        if (map == null) {
            map = new OfflineGroupWorldImpl(this, world);
            worlds.put(world, map);
        }
        return map;
    }

    private OfflineGroupWorldImpl get(World world) {
        // Note: computeIfAbsent bug!
        OfflineGroupWorldImpl map = worlds.get(world);
        if (map == null) {
            map = new OfflineGroupWorldImpl(this, OfflineWorld.of(world));
            worlds.put(world, map);
        }
        return map;
    }

    public void unloadWorld(World world) {
        ArrayList<MinecartGroup> groupsOnWorld = new ArrayList<>();
        for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
            if (group.getWorld() == world) {
                groupsOnWorld.add(group);
            }
        }

        synchronized (worlds) {
            final OfflineGroupWorldImpl map = get(world);

            // Mark as handling the world unload event
            // This makes sure it doesn't try to restore the trains we are
            // trying to unload, or miscalculate the number of unloaded chunks.
            map.setIsDuringWorldUnloadEvent(true);
            try {
                groupsOnWorld.forEach(MinecartGroup::unload);

                // Reset loaded chunk count for OfflineGroups to 0
                map.values().forEach(group -> group.updateLoadedChunks(map));
            } finally {
                map.setIsDuringWorldUnloadEvent(false);
            }
        }
    }

    public void loadChunk(Chunk chunk) {
        chunkLoadReq = true;
        // Ignore chunk loads while refreshing
        if (isRefreshingGroups) {
            return;
        }
        synchronized (worlds) {
            OfflineGroupWorldImpl map = worlds.get(chunk.getWorld());
            if (map != null && map.canRestoreGroups()) {
                if (map.isEmpty()) {
                    worlds.remove(chunk.getWorld());
                } else {
                    Set<OfflineGroup> groups = map.removeFromChunk(chunk);
                    if (groups != null) {
                        for (OfflineGroup group : groups) {
                            if (group.testFullyLoaded()) {
                                //a participant to be restored
                                if (group.updateLoadedChunks(map)) {
                                    map.restoreGroup(group);
                                } else {
                                    //add it again
                                    map.add(group);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void unloadChunk(Chunk chunk) {
        // This chunk is still referenced in existing groups
        // Make sure to mark this chunks as unloaded, as at this point Bukkit still
        // says it is not.
        long chunkCoordLong = MathUtil.longHashToLong(chunk.getX(), chunk.getZ());
        lastUnloadChunk = Long.valueOf(chunkCoordLong);

        // Check no trains are keeping the chunk loaded
        World chunkWorld = chunk.getWorld();
        for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
            if (group.isInChunk(chunkWorld, chunkCoordLong)) {
                unloadChunkForGroup(group, chunk);
            }
        }

        // Double-check
        for (Entity entity : WorldUtil.getEntities(chunk)) {
            if (entity instanceof Minecart) {
                MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
                if (member == null || !member.isInteractable()) {
                    continue;
                }
                unloadChunkForGroup(member.getGroup(), chunk);
            }
        }

        // Remove the chunk from all known OfflineGroups
        synchronized (worlds) {
            OfflineGroupWorld map = worlds.get(chunk.getWorld());
            if (map != null) {
                if (map.isEmpty()) {
                    worlds.remove(chunk.getWorld());
                } else {
                    Set<OfflineGroup> groupset = map.getFromChunk(chunk);
                    if (groupset != null) {
                        for (OfflineGroup group : groupset) {
                            group.loadedChunks.remove(MathUtil.longHashToLong(chunk.getX(), chunk.getZ()));
                        }
                    }
                }
            }
        }

        // At this point unloading is done, and we can stop tracking the unloaded chunk
        lastUnloadChunk = null;
    }

    private static void unloadChunkForGroup(MinecartGroup group, Chunk chunk) {
        if (group.canUnload()) {
            group.unload();
        } else if (group.getChunkArea().containsChunk(chunk.getX(), chunk.getZ()))  {
            group.getTrainCarts().log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded unexpectedly!");
        } else {
            group.getTrainCarts().log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded because chunk area wasn't up to date!");
        }
    }

    public void refresh() {
        for (World world : WorldUtil.getWorlds()) {
            refresh(world);
        }
    }

    public void refresh(World world) {
        synchronized (worlds) {
            OfflineGroupWorldImpl map = worlds.get(world);
            if (map != null) {
                if (map.isEmpty()) {
                    worlds.remove(world);
                } else if (map.canRestoreGroups()) {
                    map.refreshGroups();
                }
            }
        }
    }

    /**
     * Asynchronously forces all chunks kept loaded to be loaded.
     * In the future, once the chunks are loaded, will restore the
     * associated trains.
     *
     * @return List of chunks to keep loaded mapped to the group that does
     */
    public Map<OfflineGroup, List<ForcedChunk>> getForceLoadedChunks() {
        Map<OfflineGroup, List<ForcedChunk>> chunks = new HashMap<>();
        for (World world : WorldUtil.getWorlds()) {
            chunks.putAll(getForceLoadedChunks(world));
        }
        return chunks;
    }

    /**
     * Asynchronously forces all chunks kept loaded to be loaded
     * for a single world.
     * In the future, once the chunks are loaded, will restore the
     * associated trains.
     *
     * @param world The world to find groups for to keep loaded
     * @return List of chunks to keep loaded mapped to the group that does
     */
    public Map<OfflineGroup, List<ForcedChunk>> getForceLoadedChunks(World world) {
        Map<OfflineGroup, List<ForcedChunk>> chunks = new HashMap<>();
        synchronized (worlds) {
            OfflineGroupWorld map = worlds.get(world);
            if (map != null && !map.isEmpty() && map.canRestoreGroups()) {
                for (OfflineGroup group : map.values()) {
                    TrainProperties prop = TrainProperties.get(group.name);
                    if (prop == null || !prop.isKeepingChunksLoaded()) {
                        continue;
                    }
                    if (TCConfig.keepChunksLoadedOnlyWhenMoving && !group.isMoving()) {
                        continue;
                    }
                    chunks.put(group, group.forceLoadChunks(world));
                }
            }
        }
        return chunks;
    }

    public boolean isDestroyingGroupOf(Minecart minecart) {
        return get(minecart.getWorld()).isDestroyingMinecart(minecart.getUniqueId());
    }

    /**
     * Tries to destroy a group that is not currently loaded. If the world the group is
     * on exists, loads the chunks the group is at and tries to destroy the entities there.
     * This process is asynchronous.
     *
     * @param groupName Name of the group to destroy
     * @return True if the group was destroyed successfully, False if this (partially) failed
     */
    public CompletableFuture<Boolean> destroyGroupAsync(String groupName) {
        OfflineGroup group = containedTrains.get(groupName);
        if (group == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        // Find the loaded World. If not loaded, just remove the metadata and 'succeed'
        World world = group.world.getLoadedWorld();
        if (world == null) {
            removeGroup(groupName);
            TrainPropertiesStore.remove(groupName);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Find the group manager for this world
        OfflineGroupWorld map;
        synchronized (worlds) {
            map = worlds.get(group.world);
            if (map == null) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
        }

        // Remove asynchronously
        return map.destroyAsync(world, group);
    }

    /**
     * Destroys all the loaded and unloaded trains on a world. Groups that aren't loaded are
     * loaded in asynchronously and destroyed from the then-loaded chunks.
     *
     * @param world World to destroy all groups on
     * @param includingVanilla Whether to also destroy currently loaded vanilla Minecarts
     * @return Future completed with the number of destroyed trains
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Integer> destroyAllAsync(final World world, final boolean includingVanilla) {
        // Ignore worlds that are disabled
        if (TrainCarts.isWorldDisabled(world)) {
            return CompletableFuture.completedFuture(0);
        }

        // Remove loaded groups
        final int removedLoadedGroupCount;
        {
            int count = 0;
            for (MinecartGroup g : MinecartGroup.getGroups().cloneAsIterable()) {
                if (g.getWorld() == world) {
                    if (!g.isEmpty()) {
                        count++;
                    }
                    g.destroy();
                }
            }

            // Destroy non-traincarts minecart entities
            if (includingVanilla) {
                count += destroyMinecartsInLoadedChunks(world);
            }

            removedLoadedGroupCount = count;
        }

        // Remove bugged-out Minecarts
        removeBuggedMinecarts(world);

        // Get a list of all offline groups on this world
        final List<OfflineGroup> offlineGroups;
        final OfflineGroupWorld map;
        synchronized (worlds) {
            map = worlds.get(world);
            if (map == null) {
                offlineGroups = Collections.emptyList();
            } else {
                offlineGroups = new ArrayList<>(map.values());
            }
        }

        // If there are no offline (unloaded) groups, we are done here
        if (offlineGroups.isEmpty()) {
            return CompletableFuture.completedFuture(removedLoadedGroupCount);
        }

        // Destroy all the offline groups asynchronously in parallel
        CompletableFuture<Boolean>[] destroyFutures = offlineGroups.stream()
                .map(group -> map.destroyAsync(world, group))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(destroyFutures).thenApply(unused -> {
            int count = removedLoadedGroupCount;
            for (CompletableFuture<Boolean> future : destroyFutures) {
                try {
                    if (future.get()) {
                        count++;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    plugin.getLogger().log(Level.SEVERE, "Unhandled error destroying carts", e);
                }
            }
            return count;
        });
    }

    /**
     * Asynchronously destroys all the trains on the server (for all worlds)
     *
     * @param includingVanilla Whether to also destroy currently loaded vanilla Minecarts
     * @return Future completed once this is done with the number of trains that were destroyed
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Integer> destroyAllAsync(final boolean includingVanilla) {
        final CompletableFuture<Integer>[] futures = Bukkit.getWorlds().stream()
                .map(world -> destroyAllAsync(world, includingVanilla))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).thenApply(unused -> {
            int total = 0;
            for (CompletableFuture<Integer> future : futures) {
                try {
                    total += future.get();
                } catch (InterruptedException | ExecutionException e) {
                    plugin.getLogger().log(Level.SEVERE, "Unhandled error destroying carts", e);
                }
            }

            // Can now clear these, too
            TrainProperties.clearAll();
            synchronized (worlds) {
                worlds.clear();
            }

            return total;
        });
    }

    private int destroyMinecartsInLoadedChunks(World world) {
        int count = 0;
        for (Chunk chunk : WorldUtil.getChunks(world)) {
            for (Entity e : chunk.getEntities()) {
                if (e instanceof Minecart && !e.isDead()) {
                    e.remove();
                    Util.markChunkDirty(chunk);
                    if (MinecartMemberStore.getFromEntity(e) == null) {
                        count++;
                    }
                }
            }
        }
        for (Entity e : world.getEntities()) {
            if (e instanceof Minecart && !e.isDead()) {
                e.remove();

                Chunk chunk = WorldUtil.getChunk(world, EntityUtil.getChunkX(e), EntityUtil.getChunkZ(e));
                if (chunk != null) {
                    Util.markChunkDirty(chunk);
                }
            }
        }
        return count;
    }

    /**
     * Gets rid of all Minecarts that are stored in the chunk, but not in the World,
     * resolving collision problems. (this should really never happen, but it is there just in case)
     */
    public void removeBuggedMinecarts(World world) {
        Set<org.bukkit.entity.Entity> toRemove = new HashSet<>();
        
        Set<org.bukkit.entity.Entity> worldentities = new HashSet<org.bukkit.entity.Entity>();
        for (Entity entity : WorldUtil.getEntities(world)) {
            worldentities.add(entity);
        }

        for (Chunk chunk : WorldUtil.getChunks(world)) {
            // Remove entities that are falsely added
            Iterator<org.bukkit.entity.Entity> iter = WorldUtil.getEntities(chunk).iterator();
            while (iter.hasNext()) {
                org.bukkit.entity.Entity e = iter.next();
                if (!worldentities.contains(e)) {
                    iter.remove();
                    toRemove.add(e);
                }
            }
            // Remove them from other locations
            for (org.bukkit.entity.Entity e : toRemove) {
                WorldUtil.removeEntity(e);
            }
            toRemove.clear();
        }
    }

    public void deinit() {
        worlds.clear();
        containedMinecarts.clear();
        containedTrains.clear();
    }

    /**
     * Loads the buffered groups from file
     *
     * @param filename - The groupdata file to read from
     */
    public void init(String filename) {
        synchronized (worlds) {
            deinit();
            new DataReader(filename) {
                public void read(DataInputStream stream) throws IOException {
                    int totalgroups = 0;
                    int totalmembers = 0;
                    final int worldcount = stream.readInt();
                    for (int worldIdx = 0; worldIdx < worldcount; worldIdx++) {
                        OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
                        final int groupcount = stream.readInt();
                        OfflineGroupWorld map = get(world);

                        // Read all the groups contained
                        for (int groupIdx = 0; groupIdx < groupcount; groupIdx++) {
                            OfflineGroup wg = OfflineGroup.readFrom(stream);
                            wg.world = world;

                            // Register the new offline group within (this) Manager
                            map.add(wg);
                            totalmembers += wg.members.length;
                            totalgroups++;
                        }
                    }
                    String msg = totalgroups + " Train";
                    if (totalgroups == 1) msg += " has";
                    else msg += "s have";
                    msg += " been loaded in " + worldcount + " world";
                    if (worldcount != 1) msg += "s";
                    msg += ". (" + totalmembers + " Minecart";
                    if (totalmembers != 1) msg += "s";
                    msg += ")";
                    plugin.log(Level.INFO, msg);
                }
            }.read();
        }
    }

    /**
     * Saves the buffered groups to file
     *
     * @param filename - The groupdata file to write to
     */
    public void save(String filename) {
        synchronized (worlds) {
            new DataWriter(filename) {
                public void write(DataOutputStream stream) throws IOException {
                    //clear empty worlds
                    Iterator<OfflineGroupWorldImpl> iter = worlds.values().iterator();
                    while (iter.hasNext()) {
                        if (iter.next().isEmpty()) {
                            iter.remove();
                        }
                    }

                    //Write it
                    stream.writeInt(worlds.size());
                    for (Map.Entry<OfflineWorld, OfflineGroupWorldImpl> entry : worlds.entrySet()) {
                        StreamUtil.writeUUID(stream, entry.getKey().getUniqueId());

                        stream.writeInt(entry.getValue().totalGroupCount());
                        for (OfflineGroup wg : entry.getValue()) wg.writeTo(stream);
                    }
                }
            }.write();
        }
    }

    /**
     * Saves the full representation of a train to an offline group represention.
     * This offline group can then be stored into this manager. It will hold
     * no references to the original MinecartGroup.
     *
     * @param group Loaded MinecartGroup
     * @return OfflineGroup, or null if the group is invalid and cannot be saved
     */
    public static OfflineGroup saveGroup(MinecartGroup group) {
        if (group == null || !group.isValid()) {
            return null;
        }
        final World world = group.getWorld();
        if (world == null) {
            return null;
        }
        return new OfflineGroup(group);
    }

    /**
     * Stores the information of a group in this offline storage system
     *
     * @param group OfflineGroup to store
     * @see #saveGroup(MinecartGroup)
     */
    public void storeGroup(OfflineGroup group) {
        synchronized (worlds) {
            OfflineGroupWorldImpl map = get(group.world);
            group.updateLoadedChunks(map);
            map.add(group);
        }
    }

    /**
     * Check if a minecart is in an offline group<br>
     * Used to check if a minecart can be linked
     *
     * @param uniqueId of the Minecart
     */
    public boolean containsMinecart(UUID uniqueId) {
        return containedMinecarts.contains(uniqueId);
    }

    public int getStoredMemberCount(World world) {
        synchronized (worlds) {
            OfflineGroupWorldImpl map = worlds.get(world);
            return (map == null) ? 0 : map.totalMemberCount();
        }
    }

    public int getStoredCount() {
        return containedTrains.size();
    }

    public int getStoredCountInLoadedWorlds() {
        int count = 0;
        synchronized (worlds) {
            for (OfflineGroupWorldImpl map : worlds.values()) {
                if (map.canRestoreGroups()) {
                    count += map.totalGroupCount();
                }
            }
        }
        return count;
    }

    public boolean contains(String trainname) {
        return containedTrains.containsKey(trainname);
    }

    public boolean containsInLoadedWorld(String trainname) {
        OfflineGroup offlineGroup = containedTrains.get(trainname);
        return offlineGroup != null && offlineGroup.world.isLoaded();
    }

    public void rename(String oldtrainname, String newtrainname) {
        synchronized (worlds) {
            for (OfflineGroupWorld map : worlds.values()) {
                for (OfflineGroup group : map) {
                    if (group.name.equals(oldtrainname)) {
                        group.name = newtrainname;
                        containedTrains.remove(oldtrainname);
                        containedTrains.put(newtrainname, group);
                        return;
                    }
                }
            }
        }
    }

    public void removeMember(UUID memberUUID) {
        synchronized (worlds) {
            if (containedMinecarts.remove(memberUUID)) {
                for (OfflineGroupWorld map : worlds.values()) {
                    if (map.removeCart(memberUUID)) {
                        break;
                    }
                }
            }
        }
    }

    public void removeGroup(String groupName) {
        synchronized (worlds) {
            for (OfflineGroupWorld map : worlds.values()) {
                OfflineGroup group = map.remove(groupName);
                if (group != null) {
                    break;
                }
            }
        }
    }

    public OfflineGroup findGroup(String groupName) {
        synchronized (worlds) {
            for (OfflineGroupWorld map : worlds.values()) {
                for (OfflineGroup group : map.values()) {
                    if (group.name.equals(groupName)) {
                        return group;
                    }
                }
            }
        }
        return null;
    }

    public OfflineMember findMember(String groupName, UUID uuid) {
        OfflineGroup group = findGroup(groupName);
        if (group != null) {
            for (OfflineMember member : group.members) {
                if (member.entityUID.equals(uuid)) {
                    return member;
                }
            }
        }
        return null;
    }

    private static final class OfflineGroupWorldImpl extends OfflineGroupWorld {

        public OfflineGroupWorldImpl(OfflineGroupManager manager, OfflineWorld world) {
            super(manager, world);
        }

        public void restoreGroup(OfflineGroup group) {
            this.remove(group);
            group.create(manager.plugin, group.world.getLoadedWorld());
        }

        public void refreshGroups() {
            // While refreshing, ignore incoming Chunk Load events
            // We do not want the group map to change concurrently!
            manager.isRefreshingGroups = true;
            List<OfflineGroup> groupsBuffer = new ArrayList<>(this.totalGroupCount());
            try {
                // Keep refreshing until no new chunks are being loaded
                // Why? Keepchunksloaded trains can cause other trains to load
                do {
                    manager.chunkLoadReq = false;

                    // Go by all groups and try to restore them
                    groupsBuffer.clear();
                    groupsBuffer.addAll(this.values());
                    for (OfflineGroup group : groupsBuffer) {
                        if (group.updateLoadedChunks(this)) {
                            restoreGroup(group);
                        }
                    }
                } while (manager.chunkLoadReq);
            } catch (Throwable t) {
                manager.plugin.getLogger().log(Level.SEVERE, "Unhandled error handling train restoring", t);
            }
            manager.isRefreshingGroups = false;
        }

        @Override
        public void add(OfflineGroup group) {
            super.add(group);
            manager.containedTrains.put(group.name, group);
            for (OfflineMember member : group.members) {
                manager.containedMinecarts.add(member.entityUID);
            }
        }

        @Override
        public void remove(OfflineGroup group) {
            super.remove(group);
            manager.containedTrains.remove(group.name);
            for (OfflineMember member : group.members) {
                manager.containedMinecarts.remove(member.entityUID);
            }
        }
    }
}
