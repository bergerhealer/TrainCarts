package com.bergerkiller.bukkit.tc.controller.spawnable;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;

/**
 * The information about a single Minecart to be spawned as part of a {@link SpawnableGroup}
 */
public class SpawnableMember {
    private final SpawnableGroup group;
    private final ConfigurationNode config;
    private final double length;
    private final EntityType entityType;
    private final boolean flipped;

    protected SpawnableMember(SpawnableGroup group, ConfigurationNode config) {
        this.group = group;
        this.config = config.clone();
        if (this.config.contains("model.physical.cartLength")) {
            this.length = this.config.get("model.physical.cartLength", 1.0);
        } else if (this.group.getConfig().contains("model.physical.cartLength")) {
            this.length = this.group.getConfig().get("model.physical.cartLength", 1.0);
        } else {
            this.length = 1.0;
        }
        this.entityType = this.config.get("entityType", EntityType.MINECART);
        this.flipped = this.config.get("flipped", false);
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
        return new SpawnableMember(this.group, this.config);
    }

    @Override
    public String toString() {
        return this.entityType.toString();
    }

}
