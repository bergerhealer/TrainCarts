package com.bergerkiller.bukkit.tc.controller.spawnable;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

/**
 * The information about a single Minecart to be spawned as part of a {@link SpawnableGroup}
 */
public class SpawnableMember {
    private static final double DEFAULT_CART_LENGTH = 0.98;
    private final SpawnableGroup group;
    private final ConfigurationNode config;
    private final double length;
    private final EntityType entityType;
    private final boolean flipped;

    protected SpawnableMember(SpawnableGroup group, ConfigurationNode config) {
        this.group = group;
        this.config = config.clone();
        if (this.config.contains("model.physical.cartLength")) {
            this.length = this.config.get("model.physical.cartLength", DEFAULT_CART_LENGTH);
        } else if (this.group.getConfig().contains("model.physical.cartLength")) {
            this.length = this.group.getConfig().get("model.physical.cartLength", DEFAULT_CART_LENGTH);
        } else {
            this.length = DEFAULT_CART_LENGTH;
        }
        this.entityType = this.config.get("entityType", EntityType.MINECART);
        this.flipped = this.config.get("flipped", false);
    }

    /**
     * Spawns this Spawnable Member in the world
     * 
     * @param spawnLoc
     * @return spawned Minecart
     */
    public MinecartMember<?> spawn(Location spawnLoc) {
        // When initializing the config, act as unloaded to avoid creation of group
        MinecartMember<?> mm = MinecartMemberStore.spawn(spawnLoc, getEntityType());
        mm.setUnloaded(true);
        mm.getProperties().load(this.config);
        if (this.config.isNode("data")) {
            mm.onTrainSpawned(this.config.getNode("data"));
        }
        mm.setUnloaded(false);
        return mm;
    }

    /**
     * Gets the train member configuration to be applied to the cart's properties
     * after spawning.
     * 
     * @return cart configuration
     */
    public ConfigurationNode getConfig() {
        return this.config;
    }

    /**
     * Gets the Entity Type to spawn for this Minecart
     * 
     * @return entity type
     */
    public EntityType getEntityType() {
        return this.entityType;
    }

    /**
     * Gets the length of this Minecart
     * 
     * @return length
     */
    public double getLength() {
        return this.length;
    }

    /**
     * Gets whether the Minecart's orientation is flipped 180 degrees when spawning
     * 
     * @return True if flipped
     */
    public boolean isFlipped() {
        return this.flipped;
    }

    /**
     * Gets the permission node for spawning this Minecart
     * 
     * @return spawn permission
     */
    public Permission getPermission() {
        switch (this.getEntityType()) {
        case MINECART_CHEST: return Permission.SPAWNER_STORAGE;
        case MINECART_FURNACE: return Permission.SPAWNER_POWERED;
        case MINECART_HOPPER: return Permission.SPAWNER_HOPPER;
        case MINECART_TNT: return Permission.SPAWNER_TNT;
        case MINECART_MOB_SPAWNER: return Permission.SPAWNER_SPAWNER;
        case MINECART_COMMAND: return Permission.SPAWNER_COMMAND;
        default: return Permission.SPAWNER_REGULAR;
        }
    }

    @Override
    public SpawnableMember clone() {
        return cloneWithGroup(this.group);
    }

    /**
     * Clones this SpawnableMember and assigns it a new group
     * 
     * @param group
     * @return spawnable member with new group assigned
     */
    protected SpawnableMember cloneWithGroup(SpawnableGroup group) {
        return new SpawnableMember(group, this.config);
    }

    @Override
    public String toString() {
        return this.entityType.toString();
    }

    /**
     * Stores the information used to spawn a single spawnable member
     */
    public static class SpawnLocation {
        /** The SpawnableMember this spawn location is for */
        public final SpawnableMember member;
        /** The exact location and forward-orientation of the member when spawned */
        public final Location location;
        /** The forward movement direction the member has at this spawn location */
        public final Vector forward;

        public SpawnLocation(SpawnableMember member, Vector forward, Location location) {
            this.member = member;
            this.forward = forward;
            this.location = location;
        }
    }
}
