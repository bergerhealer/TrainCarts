package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.GroupLinkEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import com.bergerkiller.bukkit.tc.offline.train.OfflineGroupManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;

public class MinecartGroupStore extends ArrayList<MinecartMember<?>> {
    private static final long serialVersionUID = 1;
    protected static ImplicitlySharedSet<MinecartGroup> groups = new ImplicitlySharedSet<MinecartGroup>();
    protected static boolean hasPhysicsChanges = false;
    private static long lastMaxPerWorldLogTimestamp = 0;

    /**
     * Called onPhysics for all Minecart entities who didn't get ticked in the previous run.
     * This is a sort of hack against the bugged issues on some server implementations.
     *
     * @param plugin Main TrainCarts plugin instance initiating this
     */
    public static void doFixedTick(TrainCarts plugin) {
        try (ImplicitlySharedSet<MinecartGroup> groups_copy = groups.clone()) {
            try {
                for (MinecartGroup group : groups_copy) {
                    // Tick the train
                    group.doPhysics(plugin);

                    // Perform post-tick physics for all Minecarts in the train
                    for (MinecartMember<?> member : group) {
                        if (!member.isUnloaded()) {
                            member.getEntity().doPostTick();
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.handle(t);
            }
        }
    }

    /**
     * Executes the Entity doPostTick() on all trains.
     * This ensures minecart entities are moved to the correct chunk they are in.
     */
    public static void doPostMoveLogic() {
        try (ImplicitlySharedSet<MinecartGroup> groups_copy = groups.clone()) {
            try {
                for (MinecartGroup group : groups_copy) {
                    for (MinecartMember<?> m : group) {
                        m.getEntity().doPostTick();
                    }
                }
            } catch (Throwable t) {
                TrainCarts.plugin.handle(t);
            }
        }
    }

    public static MinecartGroup create(MinecartMember<?>... members) {
        return create(null, members);
    }

    public static MinecartGroup create(String name, MinecartMember<?>... members) {
        Util.checkMainThread("MinecartGroupStore::create(name, members)");
        validateMembersArray(members);

        // There is not a group with this name already?
        MinecartGroup g = new MinecartGroup(members[0].getTrainCarts());
        if (name != null) {
            g.setProperties(TrainPropertiesStore.create(name));
        }
        addMembersAndFinalize(g, members);
        return g;
    }

    /**
     * Creates a new group that recently split from another group. The properties of the
     * original group are applied to the newly created group. The name is based off of
     * the original group's. name.
     * 
     * @param properties The properties to clone and base a split name off of
     * @param members The members of the new group
     * @return new group
     */
    public static MinecartGroup createSplitFrom(TrainProperties properties, MinecartMember<?>... members) {
        Util.checkMainThread("MinecartGroupStore::createSplitFrom(from, members)");
        validateMembersArray(members);

        // Create new group and assign it the properties of a split group
        MinecartGroup g = new MinecartGroup(members[0].getTrainCarts());
        g.setProperties(TrainPropertiesStore.createSplitFrom(properties));
        addMembersAndFinalize(g, members);
        g.getSignTracker().refresh();
        return g;
    }

    private static void validateMembersArray(MinecartMember<?>[] members) {
        final int numMembers = members.length;
        if (numMembers == 0) {
            throw new IllegalArgumentException("Members array is empty, can't create a train with zero carts");
        }
        for (int i = 0; i < numMembers; i++) {
            MinecartMember<?> member = members[i];
            if (member == null) {
                throw new IllegalArgumentException("Member at index " + i + " of members array is null");
            } else if (member.getEntity() == null) {
                throw new IllegalArgumentException("Member at index " + i + " of members array was never spawned as an entity");
            } else if (member.getEntity().isRemoved()) {
                Location lastLoc = member.getEntity().getLocation();
                String worldName = lastLoc.getWorld() == null ? "null" : lastLoc.getWorld().getName();
                throw new IllegalArgumentException(String.format(
                        "Member at index %d of members array is dead (world=%s, x=%.3f, y=%.3f, z=%.3f)",
                        i, worldName, lastLoc.getX(), lastLoc.getY(), lastLoc.getZ()));
            }
        }
    }

    private static void addMembersAndFinalize(MinecartGroup group, MinecartMember<?>... members) {
        for (MinecartMember<?> member : members) {
            member.setUnloaded(false);
            group.add(member);
        }
        group.updateDirection();
        group.getAverageForce();
        groups.add(group);
        GroupCreateEvent.call(group);
        group.onGroupCreated();
    }

    /**
     * If a per-world spawn limit is configured, checks how many TrainCarts minecarts are spawned
     * on the world, and whether the number of carts specified would exceed that limit.
     *
     * @param at Where the cart is spawned. Location is logged if needed.
     * @param numberOfCartsToSpawn Number of carts to spawn
     * @return True if the limit was reached and spawning should not be permitted, False otherwise
     */
    public static boolean isPerWorldSpawnLimitReached(Block at, int numberOfCartsToSpawn) {
        return isPerWorldSpawnLimitReached(at.getLocation(), numberOfCartsToSpawn);
    }

    /**
     * If a per-world spawn limit is configured, checks how many TrainCarts minecarts are spawned
     * on the world, and whether the number of carts specified would exceed that limit.
     *
     * @param at Where the cart is spawned. Location is logged if needed.
     * @param numberOfCartsToSpawn Number of carts to spawn
     * @return True if the limit was reached and spawning should not be permitted, False otherwise
     */
    public static boolean isPerWorldSpawnLimitReached(Location at, int numberOfCartsToSpawn) {
        if (TCConfig.maxCartsPerWorld < 0) {
            return false;
        }

        final TrainCarts traincarts = TrainCarts.plugin;

        int countSpawned = 0;
        try (ImplicitlySharedSet<MinecartGroup> groups_copy = groups.clone()) {
            for (MinecartGroup group : groups_copy) {
                if (!group.isUnloaded() && group.getWorld() == at.getWorld()) {
                    countSpawned += group.size();
                }
            }
        }
        if (TCConfig.maxCartsPerWorldCountUnloaded) {
            countSpawned += traincarts.getOfflineGroups().getStoredMemberCount(at.getWorld());
        }
        if ((countSpawned + numberOfCartsToSpawn) <= TCConfig.maxCartsPerWorld) {
            return false;
        }

        // Exceeds the limit. Write this to the server log with a 30-second cooldown to avoid log spam
        long now = System.currentTimeMillis();
        if (lastMaxPerWorldLogTimestamp == 0 || ((now - lastMaxPerWorldLogTimestamp) > 30000)) {
            traincarts.getLogger().warning("Could not spawn " + numberOfCartsToSpawn + " carts in world '" +
                    at.getWorld().getName() + "' at x=" + at.getBlockX() + " y=" + at.getBlockY() + " z=" + at.getBlockZ() +
                    " because it exceeds limit " +
                    "(" + (countSpawned + numberOfCartsToSpawn) + "/" + TCConfig.maxCartsPerWorld + ")");
            lastMaxPerWorldLogTimestamp = now;
        }

        return true;
    }

    public static MinecartGroup spawn(SpawnableGroup spawnableGroup, List<Location> spawnLocations) {
        List<SpawnableMember> members = spawnableGroup.getMembers();
        if (members.size() > spawnLocations.size()) {
            return null;
        }

        // Convert to a SpawnLocationList and do all the spawn logic there
        SpawnableGroup.SpawnLocationList locations = new SpawnableGroup.SpawnLocationList();
        for (int i = 0; i < members.size(); i++) {
            Location loc = spawnLocations.get(i);
            locations.addMember(members.get(i), loc.getDirection(), loc);
        }
        return spawn(spawnableGroup, locations);
    }

    public static MinecartGroup spawn(SpawnableGroup spawnableGroup, SpawnableGroup.SpawnLocationList locations) {
        return spawn(spawnableGroup, locations, 0.0);
    }

    public static MinecartGroup spawn(SpawnableGroup spawnableGroup, SpawnableGroup.SpawnLocationList locations, double initialSpeed) {
        if (locations.locations.isEmpty()) {
            throw new IllegalArgumentException("Spawn Location List has zero locations to spawn, " +
                    "cannot spawn a train with zero carts");
        }

        MinecartGroup group = new MinecartGroup(spawnableGroup.getTrainCarts());
        group.setProperties(TrainPropertiesStore.createFromConfig(spawnableGroup.getConfig()));
        groups.add(group);

        for (int i = locations.locations.size() - 1; i >= 0; i--) {
            group.add(locations.locations.get(i).spawn(initialSpeed));
        }

        group.updateDirection();
        GroupCreateEvent.call(group);
        group.onGroupCreated();

        return group;
    }

    /**
     * @deprecated Use {@link #spawn(TrainCarts, Location[], EntityType...)} instead
     */
    @Deprecated
    public static MinecartGroup spawn(Location[] at, EntityType... types) {
        return spawn(TrainCarts.plugin, at, types);
    }

    public static MinecartGroup spawn(TrainCarts plugin, Location[] at, EntityType... types) {
        Util.checkMainThread("MinecartGroupStore::spawn(at, types)");
        if (at.length == 0) {
            throw new IllegalArgumentException("One or more locations must be specified, cannot spawn a train with zero carts");
        }
        if (at.length != types.length) {
            throw new IllegalArgumentException("Number of locations is not equal to the number entity types to spawn");
        }

        MinecartGroup g = new MinecartGroup(plugin);
        for (int i = 0; i < at.length; i++) {
            g.add(MinecartMemberStore.spawn(plugin, at[i], types[i]));
        }
        groups.add(g);
        GroupCreateEvent.call(g);
        g.onGroupCreated();
        return g;
    }

    /**
     * Finds all the Minecart Groups that match the name with the expression given
     *
     * @param nameExpression Name expression to match train names against
     * @return a Collection of MinecartGroup that match (unmodifiable)
     */
    public static Collection<MinecartGroup> matchAll(String nameExpression) {
        return TrainPropertiesStore.matchAll(nameExpression).stream()
                .map(TrainProperties::getHolder)
                .filter(Objects::nonNull)
                .collect(StreamUtil.toUnmodifiableList());
    }

    /**
     * Gets a set containing all the minecart groups on the server.
     * When trains could be created while iterating, clone the set first.
     * 
     * @return shared set of all the groups on the server
     */
    public static ImplicitlySharedSet<MinecartGroup> getGroups() {
        return groups;
    }

    public static MinecartGroup get(Entity e) {
        final MinecartMember<?> mm = MinecartMemberStore.getFromEntity(e);
        return mm == null ? null : mm.getGroup();
    }

    /**
     * Links two Minecarts together
     *
     * @param m1
     * @param m2
     * @return LinkResult, whether linking succeeded and other information
     */
    public static LinkResult link(MinecartMember<?> m1, MinecartMember<?> m2) {
        if (m1 == null || m2 == null || m1 == m2 || !m1.isInteractable() || !m2.isInteractable()) {
            return LinkResult.INVALID;
        }
        MinecartGroup g1 = m1.getGroup();
        MinecartGroup g2 = m2.getGroup();
        if (g1 == g2) {
            return LinkResult.ALREADY_LINKED;
        }

        //max links per update
        if (m1.isDerailed() || m2.isDerailed()) {
            return LinkResult.DERAILED;
        }

        //Would the resulting train be too long?
        if (TCConfig.maxCartsPerTrain >= 0 && (g1.size() + g2.size()) > TCConfig.maxCartsPerTrain) {
            return LinkResult.TOO_LONG;
        }

        //Can the two groups bind?
        TrainProperties prop1 = g1.getProperties();
        TrainProperties prop2 = g2.getProperties();

        //Is a powered minecart required?
        if (prop1.isPoweredMinecartRequired() || prop2.isPoweredMinecartRequired()) {
            if (g1.size(EntityType.MINECART_FURNACE) == 0 && g2.size(EntityType.MINECART_FURNACE) == 0) {
                return LinkResult.POWERED_CART_REQUIRED;
            }
        }

        //Can the minecarts reach the other?
        if (!MinecartMember.isTrackConnected(m1, m2))
            return LinkResult.DIFFERENT_TRACKS;

        //append group1 before or after group2?
        int m1index = g1.indexOf(m1);
        int m2index = g2.indexOf(m2);

        //Validate
        if (!g2.canConnect(m1, m2index) || !g1.canConnect(m2, m1index)) {
            return LinkResult.DIFFERENT_TRACKS;
        }

        //Members must be the ends of the train, otherwise fail the collision
        //Make sure no collision occurs either
        if ( !(m1index == 0 || m1index == (g1.size() - 1)) ||
             !(m2index == 0 || m2index == (g2.size() - 1)) )
        {
            return LinkResult.IS_MIDDLE_CARTS;
        }

        //Event
        if (GroupLinkEvent.call(g1, g2).isCancelled())
            return LinkResult.LINK_CANCELLED;

        //Transfer properties if needed
        if (g1.size() > g2.size() || (g1.size() == g2.size() && g1.getTicksLived() > g2.getTicksLived())) {
            // Transfer properties
            g2.getProperties().load(g1.getProperties());
            // Transfer name, assigning a random name to the removed properties
            String name = g1.getProperties().getTrainName();
            g1.getProperties().setTrainName(TrainProperties.generateTrainName());
            g2.getProperties().setTrainName(name);
        }

        //Clear targets and active signs
        g1.getSignTracker().clear();
        g2.getSignTracker().clear();

        //Finally link
        if (m1index == 0 && m2index == 0) {
            Collections.reverse(g1);
            g2.addAll(0, g1);
        } else if (m1index == 0 && m2index == g2.size() - 1) {
            g2.addAll(g1);
        } else if (m1index == g1.size() - 1 && m2index == 0) {
            g2.addAll(0, g1);
        } else if (m1index == g1.size() - 1 && m2index == g2.size() - 1) {
            Collections.reverse(g1);
            g2.addAll(g1);
        } else {
            // This really should not be reached, there is an if check above already
            return LinkResult.IS_MIDDLE_CARTS;
        }

        //Correct the yaw and order
        g2.getAverageForce();
        g2.updateDirection();
        g2.getSignTracker().updatePosition();

        g1.remove();
        if (TCConfig.playHissWhenLinked) {
            m2.playLinkEffect();
        }
        return LinkResult.SUCCESS;
    }

    /**
     * Tells the underlying system that physics have changed. This can mean a block changed
     * type or some other logic that can alter the behavior of a train. Changes that occur
     * during physics will force a train to recalculate rail information.
     */
    public static void notifyPhysicsChange() {
        hasPhysicsChanges = true;
    }

    /**
     * The result of (trying to) link two Minecarts into a train
     */
    public enum LinkResult {
        /** One or both MinecartMember arguments are null or are not loaded */
        INVALID(false),
        /** Both MinecartMembers are already part of the same group */
        ALREADY_LINKED(true),
        /** Minecarts aren't at the ends of the train, so they shouldn't collide yet */
        IS_MIDDLE_CARTS(true),
        /** One or both MinecartMembers are derailed */
        DERAILED(false),
        /** Can't link because the resulting train would be too long */
        TOO_LONG(false),
        /** Can't link because a powered cart is required for that */
        POWERED_CART_REQUIRED(false),
        /** The two members are on different non-connected tracks, no linking occurred */
        DIFFERENT_TRACKS(true),
        /** The GroupLinkEvent was cancelled by a plugin */
        LINK_CANCELLED(false),
        /** Link success! Members are now linked to a train */
        SUCCESS(true);

        private final boolean cancelCollision;

        LinkResult(boolean cancelCollision) {
            this.cancelCollision = cancelCollision;
        }

        /**
         * If the link was result of two Minecarts colliding, returns whether the
         * collision should be cancelled because of this link attempt result.
         *
         * @return True if collision should be cancelled, False if it should be allowed
         *         to occur like normal.
         */
        public boolean isCancelCollision() {
            return cancelCollision;
        }
    }
}
