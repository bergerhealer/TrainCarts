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

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;

public class MinecartGroupStore extends ArrayList<MinecartMember<?>> {
    private static final long serialVersionUID = 1;
    protected static ImplicitlySharedSet<MinecartGroup> groups = new ImplicitlySharedSet<MinecartGroup>();
    protected static boolean hasPhysicsChanges = false;

    /**
     * Called onPhysics for all Minecart entities who didn't get ticked in the previous run.
     * This is a sort of hack against the bugged issues on some server implementations.
     */
    public static void doFixedTick() {
        try (ImplicitlySharedSet<MinecartGroup> groups_copy = groups.clone()) {
            try {
                for (MinecartGroup group : groups_copy) {
                    // Tick the train if required
                    if (!group.ticked.clear()) {
                        group.doPhysics();
                    }

                    // Perform post-tick physics for all Minecarts in the train, if not previously ticked
                    for (MinecartMember<?> member : group) {
                        if (!member.ticked.clear()) {
                            member.getEntity().doPostTick();
                        }
                    }
                }
            } catch (Throwable t) {
                TrainCarts.plugin.handle(t);
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

    /**
     * Deprecated: does not initialize a full train and is unsafe to use
     * 
     * @return new group, registered in global group list
     */
    @Deprecated
    public static MinecartGroup create() {
        Util.checkMainThread("MinecartGroupStore::create()");
        MinecartGroup g = new MinecartGroup();
        groups.add(g);
        return g;
    }

    public static MinecartGroup create(MinecartMember<?>... members) {
        return create(null, members);
    }

    public static MinecartGroup create(String name, MinecartMember<?>... members) {
        Util.checkMainThread("MinecartGroupStore::create(name, members)");

        // There is not a group with this name already?
        MinecartGroup g = new MinecartGroup();
        if (name != null) {
            g.setProperties(TrainPropertiesStore.get(name));
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

        // Create new group and assign it the properties of a split group
        MinecartGroup g = new MinecartGroup();
        g.setProperties(TrainPropertiesStore.createSplitFrom(properties));
        addMembersAndFinalize(g, members);
        g.getSignTracker().refresh();
        return g;
    }

    private static void addMembersAndFinalize(MinecartGroup group, MinecartMember<?>... members) {
        for (MinecartMember<?> member : members) {
            if (member != null && member.getEntity() != null && !member.getEntity().isDead()) {
                member.setUnloaded(false);
                group.add(member);
            }
        }
        group.updateDirection();
        group.getAverageForce();
        groups.add(group);
        GroupCreateEvent.call(group);
        group.onGroupCreated();
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
        MinecartGroup group = new MinecartGroup();
        groups.add(group);
        for (int i = locations.locations.size() - 1; i >= 0; i--) {
            SpawnableMember.SpawnLocation loc = locations.locations.get(i);
            Location spawnLoc = loc.location;
            if (loc.member.isFlipped()) {
                spawnLoc = Util.invertRotation(spawnLoc);
            }

            // Spawn the minecart
            group.add(loc.member.spawn(spawnLoc));
        }
        group.updateDirection();
        group.getProperties().load(spawnableGroup.getConfig());
        GroupCreateEvent.call(group);
        group.onGroupCreated();
        return group;
    }

    public static MinecartGroup spawn(Location[] at, EntityType... types) {
        Util.checkMainThread("MinecartGroupStore::spawn(at, types)");
        if (at.length != types.length || at.length == 0) return null;
        MinecartGroup g = new MinecartGroup();
        for (int i = 0; i < types.length; i++) {
            g.add(MinecartMemberStore.spawn(at[i], types[i]));
        }
        groups.add(g);
        GroupCreateEvent.call(g);
        g.onGroupCreated();
        return g;
    }

    /**
     * Finds all the Minecart Groups that match the name with the expression given
     *
     * @param expression to match to
     * @return a Collection of MinecartGroup that match (unmodifiable)
     */
    public static Collection<MinecartGroup> matchAll(String expression) {
        return TrainPropertiesStore.matchAll(expression).stream()
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

    public static boolean link(MinecartMember<?> m1, MinecartMember<?> m2) {
        if (m1 == null || m2 == null || m1 == m2 || !m1.isInteractable() || !m2.isInteractable()) {
            return false;
        }
        MinecartGroup g1 = m1.getGroup();
        MinecartGroup g2 = m2.getGroup();
        //max links per update
        if (g1 != g2) {
            if (m1.isDerailed() || m2.isDerailed()) {
                return false;
            }
            //Can the two groups bind?
            TrainProperties prop1 = g1.getProperties();
            TrainProperties prop2 = g2.getProperties();

            //Is a powered minecart required?
            if (prop1.isPoweredMinecartRequired() || prop2.isPoweredMinecartRequired()) {
                if (g1.size(EntityType.MINECART_FURNACE) == 0 && g2.size(EntityType.MINECART_FURNACE) == 0) {
                    return false;
                }
            }

            //Can the minecarts reach the other?
            if (!MinecartMember.isTrackConnected(m1, m2)) return true;

            //append group1 before or after group2?
            int m1index = g1.indexOf(m1);
            int m2index = g2.indexOf(m2);

            //Validate
            if (!g2.canConnect(m1, m2index) || !g1.canConnect(m2, m1index)) {
                return false;
            }

            //Event
            if (GroupLinkEvent.call(g1, g2).isCancelled()) return false;

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
            } else {
                return false;
            }

            //Correct the yaw and order
            g2.getAverageForce();
            g2.updateDirection();
            g2.getSignTracker().updatePosition();

            g1.remove();
            if (TCConfig.playHissWhenLinked) {
                m2.playLinkEffect();
            }
            return true;
        }
        return false;
    }

    /**
     * Tells the underlying system that physics have changed. This can mean a block changed
     * type or some other logic that can alter the behavior of a train. Changes that occur
     * during physics will force a train to recalculate rail information.
     */
    public static void notifyPhysicsChange() {
        hasPhysicsChanges = true;
    }
}
