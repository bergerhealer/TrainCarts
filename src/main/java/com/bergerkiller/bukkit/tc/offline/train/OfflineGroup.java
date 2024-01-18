package com.bergerkiller.bukkit.tc.offline.train;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.logging.Level;

/**
 * A class containing an array of Minecart Members
 * Also adds functions to handle multiple members at once
 * Also adds functions to write and load from/to file
 */
public final class OfflineGroup {
    // These fields are immutable
    public final String name;
    public final OfflineWorld world;
    public final List<DataBlock> actions;
    public final List<DataBlock> skippedSigns;
    public final OfflineMember[] members;

    // These are modified/lazily generated at runtime
    private LongHashSet chunks = null;
    private LongHashSet loadedChunks = null;
    private boolean loaded;
    private boolean isBeingRemoved = false;

    public static OfflineGroup save(MinecartGroup group) {
        try {
            return new OfflineGroup(group);
        } catch (IOException ex) {
            throw new RuntimeException("Unexpected IO Exception", ex);
        }
    }

    private OfflineGroup(MinecartGroup group) throws IOException {
        this(group.getProperties().getTrainName(),
             /* World */
             OfflineWorld.of(group.getWorld()),
             /* Actions */
             group.getTrainCarts().getActionRegistry().saveTracker(group.getActions()),
             /* Skipped Signs */
             group.getTrainCarts().getTrackedSignLookup().serializeUniqueKeys(
                     group.getSignTracker().getSignSkipTracker().getSkippedSigns(),
                     "skipped-sign",
                     RailLookup.TrackedSign::getUniqueKey),
             /* Member List */
             group,
             OfflineMember::new);
    }

    // Constructor used for loading from data
    <T> OfflineGroup(
            final String name,
            final OfflineWorld world,
            final List<DataBlock> actions,
            final List<DataBlock> skippedSigns,
            final Collection<T> memberData,
            final MemberFactory<T> memberFactory
    ) throws IOException {
        this.name = name;
        this.world = world;
        this.actions = actions;
        this.skippedSigns = skippedSigns;
        this.members = memberFactory.createMany(this, memberData);
        this.loaded = false;
    }

    // Renames the group
    private OfflineGroup(OfflineGroup original, String newName) {
        this.name = newName;
        this.world = original.world;
        this.members = original.members;
        this.chunks = original.chunks;
        this.loadedChunks = original.loadedChunks;
        this.loaded = original.loaded;
        this.isBeingRemoved = original.isBeingRemoved;
        this.actions = original.actions;
        this.skippedSigns = original.skippedSigns;
    }

    public OfflineGroup withName(String newName) {
        return new OfflineGroup(this, newName);
    }

    public OfflineGroup withMembers(List<OfflineMember> newMembers) {
        try {
            return new OfflineGroup(name, world, actions, skippedSigns, newMembers, (cgroup, cmember) -> cmember);
        } catch (IOException ex) {
            throw new RuntimeException("Unexpected io exception", ex);
        }
    }

    /**
     * Gets whether this offline group has been loaded into the server
     * as a MinecartGroup. Also returns true if all members of this group
     * were missing and the offline group was purged. If true, this
     * offline group no longer exists in the offline group manager.
     *
     * @return True if loaded as group
     */
    public boolean isLoadedAsGroup() {
        return this.loaded;
    }

    public LongHashSet getChunks() {
        LongHashSet chunks = this.chunks;
        if (chunks == null) {
            // Obtain an average of the amount of elements to store for chunks
            // Assume that each member adds 5 chunks every 10 carts
            final int chunkCount = 25 + (int) ((double) (5 / 10) * (double) members.length);
            chunks = new LongHashSet(chunkCount);
            for (OfflineMember wm : members) {
                for (int x = wm.cx - 2; x <= wm.cx + 2; x++) {
                    for (int z = wm.cz - 2; z <= wm.cz + 2; z++) {
                        chunks.add(MathUtil.longHashToLong(x, z));
                    }
                }
            }
            this.chunks = chunks;
        }
        return chunks;
    }

    public LongHashSet getLoadedChunks() {
        LongHashSet loadedChunks = this.loadedChunks;
        if (loadedChunks == null) {
            this.loadedChunks = loadedChunks = new LongHashSet(getChunks().size());
        }
        return loadedChunks;
    }

    public void forAllChunks(ChunkCoordConsumer action) {
        final LongIterator iter = getChunks().longIterator();
        while (iter.hasNext()) {
            long chunk = iter.next();
            action.accept(MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk));
        }
    }

    public void forAllChunks(LongConsumer action) {
        final LongIterator iter = getChunks().longIterator();
        while (iter.hasNext()) {
            action.accept(iter.next());
        }
    }

    public boolean isMoving() {
        for (OfflineMember member : members) {
            if (member.isMoving()) {
                return true;
            }
        }
        return false;
    }

    void setBeingRemoved() {
        this.isBeingRemoved = true;
    }

    public boolean testFullyLoaded() {
        // When being removed (asynchronously) pretend the group isn't loaded in yet
        // This stalls any restoring action
        if (this.isBeingRemoved) {
            return false;
        }

        return this.getLoadedChunks().size() == this.getChunks().size();
    }

    protected boolean updateLoadedChunks(OfflineGroupWorldLive offlineMap) {
        final LongHashSet loadedChunks = getLoadedChunks();
        loadedChunks.clear();

        World world = this.world.getLoadedWorld();
        if (world != null && offlineMap.canRestoreGroups()) {
            forAllChunks(chunk -> {
                if (WorldUtil.isChunkEntitiesLoaded(world, MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk))) {
                    loadedChunks.add(chunk);
                }
            });
            if (offlineMap.getManager().lastUnloadChunk != null) {
                loadedChunks.remove(offlineMap.getManager().lastUnloadChunk);
            }
            return this.testFullyLoaded();
        } else {
            return false;
        }
    }

    /**
     * Forces all chunks used by this group to become loaded, asynchronously
     *
     * @param world World the group is on
     * @return List of forced chunks, keep these around to allow all chunks to load
     */
    public List<ForcedChunk> forceLoadChunks(World world) {
        List<ForcedChunk> chunks = new ArrayList<>();
        forAllChunks((cx, cz) -> chunks.add(WorldUtil.forceChunkLoaded(world, cx, cz)));
        return chunks;
    }

    /**
     * Tries to find all Minecarts based on their UID and creates a new group
     *
     * @param traincarts TrainCarts plugin instance
     * @return An array of Minecarts
     */
    public MinecartGroup create(TrainCarts traincarts) {
        ArrayList<MinecartMember<?>> groupMembers = new ArrayList<>(this.members.length);
        int missingNo = 0;
        int cx = 0, cz = 0;
        World world = this.world.getLoadedWorld();
        for (OfflineMember offlineMember : this.members) {
            MinecartMember<?> mm = offlineMember.create(traincarts, world);
            if (mm != null) {
                groupMembers.add(mm);
            } else {
                missingNo++;
                cx = offlineMember.cx;
                cz = offlineMember.cz;
            }
        }
        if (missingNo > 0) {
            traincarts.log(Level.WARNING, missingNo + " carts of group '" + this.name + "' " +
                    "are missing near chunk [" + cx + ", " + cz + "]! (externally edited?)");
        }
        this.loaded = true;
        if (groupMembers.isEmpty()) {
            TrainPropertiesStore.remove(this.name);
            return null;
        }

        MinecartGroup group = MinecartGroup.create(this.name, groupMembers.toArray(new MinecartMember[0]));

        // Initialize the group itself
        this.load(group);

        // Initialize the members of the group. Assume that all members load in and that the
        // indices are identical.
        for (int i = 0; i < this.members.length; i++) {
            OfflineMember offlineMember = this.members[i];
            MinecartMember<?> member;
            if (i < group.size() && offlineMember.entityUID.equals(group.get(i).getEntity().getUniqueId())) {
                member = group.get(i);
            } else {
                member = null;
                for (MinecartMember<?> groupMember : group) {
                    if (offlineMember.entityUID.equals(groupMember.getEntity().getUniqueId())) {
                        member = groupMember;
                        break;
                    }
                }
                if (member == null) {
                    continue;
                }
            }

            offlineMember.load(member);
        }

        return group;
    }

    void load(MinecartGroup group) {
        group.getTrainCarts().getActionRegistry().loadTracker(group.getActions(), actions);

        for (Object signKey : group.getTrainCarts().getTrackedSignLookup().deserializeUniqueKeys(skippedSigns)) {
            group.getSignTracker().addOfflineSkippedSignKey(signKey);
        }

        group.getSignTracker().clearUpdates();
    }

    @FunctionalInterface
    public interface ChunkCoordConsumer {
        void accept(int cx, int cz);
    }

    @FunctionalInterface
    public interface MemberFactory<T> {
        OfflineMember create(OfflineGroup group, T data) throws IOException;

        default OfflineMember[] createMany(OfflineGroup group, Collection<T> data) throws IOException {
            int index = 0;
            OfflineMember[] members = new OfflineMember[data.size()];
            for (T member : data) {
                members[index++] = create(group, member);
            }
            return members;
        }
    }
}