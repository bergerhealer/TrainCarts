package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Groups train updates by world while preserving the incoming order within each world.
 */
final class TrainUpdateSorter {
    private final Map<MinecartGroup, UpdatedGroup> groups = new LinkedHashMap<>();
    private final Map<World, List<UpdatedGroup>> groupsByWorld = new LinkedHashMap<>();

    public TrainUpdateSorter() {
    }

    /**
     * Rebuilds the internal world-&gt;groups structure from the input collection.
     * The per-world order is preserved as encountered.
     * Call this before calling {@link #forEachGroup(Consumer)}
     *
     * @param groups Groups to synchronize from
     */
    public void sync(Iterable<MinecartGroup> groups) {
        final Object removalMarker = new Object();

        // Insert all the groups into the groups and by-world maps.
        // Move groups in the wrong world to the right one.
        // If the train teleports to another world during the update that is no big deal,
        // it only affects the update order.
        for (MinecartGroup group : groups) {
            UpdatedGroup updatedGroup = this.groups.computeIfAbsent(group, UpdatedGroup::new);
            updatedGroup.marker = removalMarker;

            // Ensure stored in by-world mapping
            World world = group.getWorld();
            if (updatedGroup.world != world) {
                if (updatedGroup.world != null) {
                    removeFromWorld(updatedGroup.world, updatedGroup);
                }
                addToWorld(world, updatedGroup);
                updatedGroup.world = world;
            }

            // Refresh centroid position the train has
            updatedGroup.computeCentroid();
        }

        // Remove groups that no longer exist
        this.groups.values().removeIf(updatedGroup -> {
            if (updatedGroup.marker == removalMarker) {
                return false;
            } else {
                // Group no longer exists, remove it from the by-world mapping also
                removeFromWorld(updatedGroup.world, updatedGroup);
                return true;
            }
        });

        // Sort the groups in each list by world, by proximity to each other.
        this.groupsByWorld.values().forEach(TrainUpdateSorter::sortGroupsByProximity);
    }

    private static void sortGroupsByProximity(List<UpdatedGroup> groups) {
        if (groups.size() <= 2) {
            return;
        }

        // Used hilbert curve algorithm to sort them (hilbert key computed previously)
        IntVector3 s;
        groups.sort(Comparator
                .comparingLong((UpdatedGroup group) -> group.hilbertKey)
                .thenComparing(group -> group.centroid));
    }

    /**
     * Iterates all of the same groups that were previously {@link #sync(Iterable)} synchronized.
     * The updates are performed in an optimal order.
     *
     * @param action Action to perform on each group
     */
    public void forEachGroup(Consumer<MinecartGroup> action) {
        groupsByWorld.values().forEach(updatedGroups -> updatedGroups.forEach(
                updatedGroup -> action.accept(updatedGroup.group)));
    }

    private void removeFromWorld(World world, UpdatedGroup updatedGroup) {
        List<UpdatedGroup> worldGroups = this.groupsByWorld.get(world);
        if (worldGroups != null) {
            worldGroups.remove(updatedGroup);
            if (worldGroups.isEmpty()) {
                this.groupsByWorld.remove(world);
            }
        }
    }

    private void addToWorld(World world, UpdatedGroup updatedGroup) {
        this.groupsByWorld.computeIfAbsent(world, w -> new ArrayList<>()).add(updatedGroup);
        updatedGroup.world = world;
    }

    private static final class UpdatedGroup {
        final MinecartGroup group;
        /** World bucket, initially null */
        World world;
        /** Marked whenever this group was found during iteration */
        Object marker;
        /** Center position the group last had during sync() */
        IntVector3 centroid;
        /** Hilbert curve key of the centroid */
        long hilbertKey;

        UpdatedGroup(MinecartGroup group) {
            this.group = group;
            this.world = null;
        }

        /**
         * Checks the train and calculates the center position of all average carts.
         */
        public void computeCentroid() {
            Vector centroid = new Vector();
            int groupCount = 0;
            for (MinecartMember<?> member : group) {
                CommonEntity<?> entity = member.getEntity();
                if (entity != null) {
                    centroid.add(entity.loc.vector());
                    groupCount++;
                }
            }
            if (groupCount > 0) {
                centroid.multiply(1.0 / groupCount);
            }
            this.centroid = IntVector3.blockOf(centroid);
            this.hilbertKey = computeHilbertKey(this.centroid.x, this.centroid.z);
        }

        /**
         * This computes a hilbert key for a hilbert curve so that the items can be easily sorted
         * by that after.
         *
         * @param blockX Block X coordinate (centroid)
         * @param blockZ Block Z coordinate (centroid)
         * @return Hilbert key
         */
        private static long computeHilbertKey(int blockX, int blockZ) {
            long x = (blockX ^ 0x80000000) & 0xFFFFFFFFL;
            long y = (blockZ ^ 0x80000000) & 0xFFFFFFFFL;
            long key = 0L;
            final long MASK = 0x7FFFFFFFL;
            for (int level = 30; level >= 0; --level) {
                long rx = (x >>> level) & 1L;
                long ry = (y >>> level) & 1L;

                key |= (((rx << 1) | (rx ^ ry)) << (level << 1));

                if (ry == 0) {
                    if (rx != 0) {
                        x ^= MASK;
                        y ^= MASK;
                    }

                    long t = x;
                    x = y;
                    y = t;
                }
            }

            return key;
        }
    }
}
