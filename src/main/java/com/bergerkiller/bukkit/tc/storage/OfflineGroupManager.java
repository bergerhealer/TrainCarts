package com.bergerkiller.bukkit.tc.storage;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
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

public class OfflineGroupManager {
    public static Long lastUnloadChunk = null;
    private static boolean chunkLoadReq = false;
    private static boolean isRefreshingGroups = false;
    private static Map<String, OfflineGroup> containedTrains = new HashMap<>();
    private static HashSet<UUID> containedMinecarts = new HashSet<>();
    private static final Map<UUID, OfflineGroupMapImpl> managers = new HashMap<>();

    private static OfflineGroupMapImpl get(UUID uuid) {
        return managers.computeIfAbsent(uuid, u -> new OfflineGroupMapImpl());
    }

    private static OfflineGroupMapImpl get(World world) {
        return get(world.getUID());
    }

    public static void loadChunk(Chunk chunk) {
        chunkLoadReq = true;
        // Ignore chunk loads while refreshing
        if (isRefreshingGroups) {
            return;
        }
        synchronized (managers) {
            OfflineGroupMapImpl map = managers.get(chunk.getWorld().getUID());
            if (map != null) {
                if (map.isEmpty()) {
                    managers.remove(chunk.getWorld().getUID());
                } else {
                    Set<OfflineGroup> groups = map.removeFromChunk(chunk);
                    if (groups != null) {
                        for (OfflineGroup group : groups) {
                            if (group.testFullyLoaded()) {
                                //a participant to be restored
                                if (group.updateLoadedChunks(chunk.getWorld())) {
                                    map.restoreGroup(group, chunk.getWorld());
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

    public static void unloadChunk(Chunk chunk) {
        synchronized (managers) {
            OfflineGroupMap map = managers.get(chunk.getWorld().getUID());
            if (map != null) {
                if (map.isEmpty()) {
                    managers.remove(chunk.getWorld().getUID());
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
    }

    public static void refresh() {
        for (World world : WorldUtil.getWorlds()) {
            refresh(world);
        }
    }

    public static void refresh(World world) {
        synchronized (managers) {
            OfflineGroupMapImpl map = managers.get(world.getUID());
            if (map != null) {
                if (map.isEmpty()) {
                    managers.remove(world.getUID());
                } else {
                    map.refreshGroups(world);
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
    public static Map<OfflineGroup, List<ForcedChunk>> getForceLoadedChunks() {
        Map<OfflineGroup, List<ForcedChunk>> chunks = new HashMap<>();
        for (World world : WorldUtil.getWorlds()) {
            synchronized (managers) {
                OfflineGroupMap map = managers.get(world.getUID());
                if (map != null && !map.isEmpty()) {
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
        }
        return chunks;
    }

    public static boolean isDestroyingGroupOf(Minecart minecart) {
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
    public static CompletableFuture<Boolean> destroyGroupAsync(String groupName) {
        OfflineGroup group = containedTrains.get(groupName);
        if (group == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        // Find the loaded World. If not loaded, just remove the metadata and 'succeed'
        World world = Bukkit.getWorld(group.worldUUID);
        if (world == null) {
            OfflineGroupManager.removeGroup(groupName);
            TrainPropertiesStore.remove(groupName);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Find the group manager for this world
        OfflineGroupMap map;
        synchronized (managers) {
            map = managers.get(group.worldUUID);
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
    public static CompletableFuture<Integer> destroyAllAsync(final World world, final boolean includingVanilla) {
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
        final OfflineGroupMap map;
        synchronized (managers) {
            map = managers.get(world.getUID());
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
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Unhandled error destroying carts", e);
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
    public static CompletableFuture<Integer> destroyAllAsync(final boolean includingVanilla) {
        final CompletableFuture<Integer>[] futures = Bukkit.getWorlds().stream()
                .map(world -> destroyAllAsync(world, includingVanilla))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).thenApply(unused -> {
            int total = 0;
            for (CompletableFuture<Integer> future : futures) {
                try {
                    total += future.get();
                } catch (InterruptedException | ExecutionException e) {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Unhandled error destroying carts", e);
                }
            }

            // Can now clear these, too
            TrainProperties.clearAll();
            synchronized (managers) {
                managers.clear();
            }

            return total;
        });
    }

    private static int destroyMinecartsInLoadedChunks(World world) {
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
    public static void removeBuggedMinecarts(World world) {
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

    public static void deinit() {
        managers.clear();
        containedMinecarts.clear();
        containedTrains.clear();
    }

    /**
     * Loads the buffered groups from file
     *
     * @param filename - The groupdata file to read from
     */
    public static void init(String filename) {
        synchronized (managers) {
            deinit();
            new DataReader(filename) {
                public void read(DataInputStream stream) throws IOException {
                    int totalgroups = 0;
                    int totalmembers = 0;
                    final int worldcount = stream.readInt();
                    for (int worldIdx = 0; worldIdx < worldcount; worldIdx++) {
                        UUID worldUID = StreamUtil.readUUID(stream);
                        final int groupcount = stream.readInt();
                        OfflineGroupMap map = get(worldUID);

                        // Read all the groups contained
                        for (int groupIdx = 0; groupIdx < groupcount; groupIdx++) {
                            OfflineGroup wg = OfflineGroup.readFrom(stream);
                            wg.worldUUID = worldUID;

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
                    TrainCarts.plugin.log(Level.INFO, msg);
                }
            }.read();
        }
    }

    /**
     * Saves the buffered groups to file
     *
     * @param filename - The groupdata file to write to
     */
    public static void save(String filename) {
        synchronized (managers) {
            new DataWriter(filename) {
                public void write(DataOutputStream stream) throws IOException {
                    //clear empty worlds
                    Iterator<OfflineGroupMapImpl> iter = managers.values().iterator();
                    while (iter.hasNext()) {
                        if (iter.next().isEmpty()) {
                            iter.remove();
                        }
                    }

                    //Write it
                    stream.writeInt(managers.size());
                    for (Map.Entry<UUID, OfflineGroupMapImpl> entry : managers.entrySet()) {
                        StreamUtil.writeUUID(stream, entry.getKey());

                        stream.writeInt(entry.getValue().size());
                        for (OfflineGroup wg : entry.getValue()) wg.writeTo(stream);
                    }
                }
            }.write();
        }
    }

    /**
     * Stores the information of a group in this offline storage system
     *
     * @param group to store
     */
    public static void storeGroup(MinecartGroup group) {
        if (group == null || !group.isValid()) {
            return;
        }
        final World world = group.getWorld();
        if (world == null) {
            return;
        }
        synchronized (managers) {
            OfflineGroup wg = new OfflineGroup(group);
            wg.updateLoadedChunks(world);
            get(world).add(wg);
        }
    }

    /**
     * Check if a minecart is in an offline group<br>
     * Used to check if a minecart can be linked
     *
     * @param uniqueId of the Minecart
     */
    public static boolean containsMinecart(UUID uniqueId) {
        return containedMinecarts.contains(uniqueId);
    }

    public static int getStoredCount() {
        return containedTrains.size();
    }

    public static int getStoredCountInLoadedWorlds() {
        int count = 0;
        synchronized (managers) {
            for (Map.Entry<UUID, OfflineGroupMapImpl> entry : managers.entrySet()) {
                if (Bukkit.getWorld(entry.getKey()) != null) {
                    count += entry.getValue().size();
                }
            }
        }
        return count;
    }

    public static boolean contains(String trainname) {
        return containedTrains.containsKey(trainname);
    }

    public static boolean containsInLoadedWorld(String trainname) {
        OfflineGroup offlineGroup = containedTrains.get(trainname);
        return offlineGroup != null && Bukkit.getWorld(offlineGroup.worldUUID) != null;
    }

    public static void rename(String oldtrainname, String newtrainname) {
        synchronized (managers) {
            for (OfflineGroupMap map : managers.values()) {
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

    public static void removeMember(UUID memberUUID) {
        synchronized (managers) {
            if (containedMinecarts.remove(memberUUID)) {
                for (OfflineGroupMap map : managers.values()) {
                    if (map.removeCart(memberUUID)) {
                        break;
                    }
                }
            }
        }
    }

    public static void removeGroup(String groupName) {
        synchronized (managers) {
            for (OfflineGroupMap map : managers.values()) {
                OfflineGroup group = map.remove(groupName);
                if (group != null) {
                    break;
                }
            }
        }
    }

    public static OfflineGroup findGroup(String groupName) {
        synchronized (managers) {
            for (OfflineGroupMap map : managers.values()) {
                for (OfflineGroup group : map.values()) {
                    if (group.name.equals(groupName)) {
                        return group;
                    }
                }
            }
        }
        return null;
    }

    public static OfflineMember findMember(String groupName, UUID uuid) {
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

    private static final class OfflineGroupMapImpl extends OfflineGroupMap {

        public void restoreGroup(OfflineGroup group, World world) {
            this.remove(group);
            group.create(world);
        }

        public void refreshGroups(World world) {
            // While refreshing, ignore incoming Chunk Load events
            // We do not want the group map to change concurrently!
            isRefreshingGroups = true;
            List<OfflineGroup> groupsBuffer = new ArrayList<>(this.size());
            try {
                // Keep refreshing until no new chunks are being loaded
                // Why? Keepchunksloaded trains can cause other trains to load
                do {
                    chunkLoadReq = false;

                    // Go by all groups and try to restore them
                    groupsBuffer.clear();
                    groupsBuffer.addAll(this.values());
                    for (OfflineGroup group : groupsBuffer) {
                        if (group.updateLoadedChunks(world)) {
                            restoreGroup(group, world);
                        }
                    }
                } while (chunkLoadReq);
            } catch (Throwable t) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE, "Unhandled error handling train restoring", t);
            }
            isRefreshingGroups = false;
        }

        @Override
        public void add(OfflineGroup group) {
            super.add(group);
            containedTrains.put(group.name, group);
            for (OfflineMember member : group.members) {
                containedMinecarts.add(member.entityUID);
            }
        }

        @Override
        public void remove(OfflineGroup group) {
            super.remove(group);
            containedTrains.remove(group.name);
            for (OfflineMember member : group.members) {
                containedMinecarts.remove(member.entityUID);
            }
        }
    }
}
