package com.bergerkiller.bukkit.tc.properties.collision;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.CollisionMode;

/**
 * Immutable representation of all the collision settings of a train
 */
public final class CollisionConfig {
    private static final EnumMap<CollisionMobCategory, CollisionMode> NO_MOB_MODES = new EnumMap<>(CollisionMobCategory.class);

    /**
     * Default collision configuration of TrainCarts trains
     */
    public static final CollisionConfig DEFAULT = new CollisionConfig(
            NO_MOB_MODES, /* Mob modes */
            CollisionMode.DEFAULT, /* Players */
            CollisionMode.PUSH, /* Misc */
            CollisionMode.LINK, /* Trains */
            CollisionMode.DEFAULT /* Blocks */
    );

    /**
     * Collision configuration that has all possible collision modes set to
     * {@link CollisionMode#CANCEL}. When this configuration is used,
     * trains will go through everything, not doing any collision hit detection.
     */
    public static final CollisionConfig CANCEL = new CollisionConfig(
            NO_MOB_MODES, /* Mob modes */
            CollisionMode.CANCEL, /* Players */
            CollisionMode.CANCEL, /* Misc */
            CollisionMode.CANCEL, /* Trains */
            CollisionMode.CANCEL /* Blocks */
    );

    private final EnumMap<CollisionMobCategory, CollisionMode> mobModes;
    private final CollisionMode playerMode;
    private final CollisionMode miscMode;
    private final CollisionMode trainMode;
    private final CollisionMode blockMode;

    private CollisionConfig(
            EnumMap<CollisionMobCategory, CollisionMode> mobModes,
            CollisionMode playerMode,
            CollisionMode miscMode,
            CollisionMode trainMode,
            CollisionMode blockMode
    ) {
        this.mobModes = mobModes;
        this.playerMode = playerMode;
        this.miscMode = miscMode;
        this.trainMode = trainMode;
        this.blockMode = blockMode;
    }

    /**
     * Gets a mapping of all configured mob category collision modes
     * 
     * @return mob modes map
     */
    public Map<CollisionMobCategory, CollisionMode> mobModes() {
        return Collections.unmodifiableMap(this.mobModes);
    }

    /**
     * Gets the collision mode used when colliding with a certain category of mobs.
     * If none is defined, returns null.
     * 
     * @param category Mob category
     * @return collision mode for this mob category
     */
    public CollisionMode mobMode(CollisionMobCategory category) {
        return this.mobModes.get(category);
    }

    /**
     * Gets the collision mode to use for a given entity. If this is a Player,
     * then the player mode is returned. If one of the defined mob categories
     * matches, the set mode is returned. Otherwise, the miscellaneous mode is
     * returned.
     * 
     * @param entity The entity to find the collision mode for
     * @return Collision mode
     */
    public CollisionMode forEntity(Entity entity) {
        if (entity instanceof Player) {
            return this.playerMode;
        }
        for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
            CollisionMode collisionMode = mobMode(collisionConfigObject);
            if (collisionMode != null && collisionConfigObject.isMobType(entity)) {
                return collisionMode;
            }
        }
        return this.miscMode;
    }

    /**
     * Gets the collision mode used when colliding with players
     * 
     * @return player collision mode
     */
    public CollisionMode playerMode() {
        return this.playerMode;
    }

    /**
     * Gets the collision mode used when colliding with non-living entities, like items
     * 
     * @return miscellaneous collision mode
     */
    public CollisionMode miscMode() {
        return this.miscMode;
    }

    /**
     * Gets the collision mode used when colliding with other trains
     * 
     * @return train collision mode
     */
    public CollisionMode trainMode() {
        return this.trainMode;
    }

    /**
     * Gets the collision mode used when colliding with blocks
     * 
     * @return block collision mode
     */
    public CollisionMode blockMode() {
        return this.blockMode;
    }

    /**
     * Checks whether collision with entities has any effect.
     * If collision with players, trains, mobs and other miscellaneous
     * entities is all set to {@link CollisionMode#CANCEL}, then
     * this method will return false. Otherwise, it will return true.
     * 
     * @return True if collisions with entities are possible
     */
    public boolean collidesWithEntities() {
        if (this.playerMode != CollisionMode.CANCEL ||
            this.trainMode != CollisionMode.CANCEL ||
            this.miscMode != CollisionMode.CANCEL)
        {
            return true;
        }

        // Check if any mobs were set to collide
        for (Map.Entry<CollisionMobCategory, CollisionMode> entry : this.mobModes.entrySet()) {
            if (entry.getValue() != CollisionMode.CANCEL) {
                return true;
            }
        }

        // It's all cancel! So it doesn't collide.
        return false;
    }

    public CollisionConfig setPlayerMode(CollisionMode mode) {
        // Check unchanged
        if (this.playerMode == mode) {
            return this;
        }

        return new CollisionConfig(this.mobModes,
                                   mode,
                                   this.miscMode,
                                   this.trainMode,
                                   this.blockMode);
    }

    public CollisionConfig setMiscMode(CollisionMode mode) {
        // Check unchanged
        if (this.miscMode == mode) {
            return this;
        }

        return new CollisionConfig(this.mobModes,
                                   this.playerMode,
                                   mode,
                                   this.trainMode,
                                   this.blockMode);
    }

    public CollisionConfig setTrainMode(CollisionMode mode) {
        // Check unchanged
        if (this.trainMode == mode) {
            return this;
        }

        return new CollisionConfig(this.mobModes,
                                   this.playerMode,
                                   this.miscMode,
                                   mode,
                                   this.blockMode);
    }

    public CollisionConfig setBlockMode(CollisionMode mode) {
        // Check unchanged
        if (this.blockMode == mode) {
            return this;
        }

        return new CollisionConfig(this.mobModes,
                                   this.playerMode,
                                   this.miscMode,
                                   this.trainMode,
                                   mode);
    }

    public CollisionConfig setMobMode(CollisionMobCategory category, CollisionMode mode) {
        // Check unchanged
        if (this.mobModes.get(category) == mode) {
            return this;
        }

        // Create new map
        EnumMap<CollisionMobCategory, CollisionMode> modes;
        if (mode == null && this.mobModes.size() == 1 && this.mobModes.containsKey(category)) {
            modes = DEFAULT.mobModes; // Memory optimization
        } else {
            modes = this.mobModes.clone();
            if (mode == null) {
                modes.remove(category);
            } else {
                modes.put(category, mode);
            }
        }

        return new CollisionConfig(modes,
                                   this.playerMode,
                                   this.miscMode,
                                   this.trainMode,
                                   this.blockMode);
    }

    public ConfigurationNode toConfig() {
        ConfigurationNode collisionConfig = new ConfigurationNode();

        for (Map.Entry<CollisionMobCategory, CollisionMode> entry : this.mobModes.entrySet()) {
            collisionConfig.set(entry.getKey().getMobType(), entry.getValue());
        }

        if (this.playerMode != DEFAULT.playerMode) {
            collisionConfig.set("players", this.playerMode);
        }
        if (this.miscMode != DEFAULT.miscMode) {
            collisionConfig.set("misc", this.miscMode);
        }
        if (this.trainMode != DEFAULT.trainMode) {
            collisionConfig.set("train", this.trainMode);
        }
        if (this.blockMode != DEFAULT.blockMode) {
            collisionConfig.set("block", this.blockMode);
        }

        return collisionConfig;
    }

    public static CollisionConfig fromConfig(ConfigurationNode collisionConfig) {
        EnumMap<CollisionMobCategory, CollisionMode> mobModes = new EnumMap<>(CollisionMobCategory.class);
        CollisionMode playerMode = DEFAULT.playerMode;
        CollisionMode miscMode = DEFAULT.miscMode;
        CollisionMode trainMode = DEFAULT.trainMode;
        CollisionMode blockMode = DEFAULT.blockMode;

        for (CollisionMobCategory category : CollisionMobCategory.values()) {
            if (collisionConfig.contains(category.getMobType())) {
                CollisionMode mode = collisionConfig.get(category.getMobType(), CollisionMode.class, null);
                if (mode != null) {
                    mobModes.put(category, mode);
                }
            }
        }

        if (collisionConfig.contains("players")) {
            playerMode = collisionConfig.get("players", playerMode);
        }
        if (collisionConfig.contains("misc")) {
            miscMode = collisionConfig.get("misc", miscMode);
        }
        if (collisionConfig.contains("train")) {
            trainMode = collisionConfig.get("train", trainMode);
        }
        if (collisionConfig.contains("block")) {
            blockMode = collisionConfig.get("block", blockMode);
        }

        // Optimization (avoid needless empty enum maps)
        if (mobModes.isEmpty()) {
            mobModes = DEFAULT.mobModes;
        }

        return new CollisionConfig(mobModes, playerMode, miscMode, trainMode, blockMode);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof CollisionConfig) {
            CollisionConfig other = (CollisionConfig) o;
            return this.playerMode == other.playerMode &&
                   this.miscMode == other.miscMode &&
                   this.trainMode == other.trainMode &&
                   this.blockMode == other.blockMode &&
                   this.mobModes.equals(other.mobModes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 0; // Just to prevent failure
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("CollisionConfig{");
        str.append("player=").append(this.playerMode.name());
        str.append(",misc=").append(this.miscMode.name());
        str.append(",train=").append(this.trainMode.name());
        str.append(",block=").append(this.blockMode.name());
        for (Map.Entry<CollisionMobCategory, CollisionMode> entry : this.mobModes.entrySet()) {
            str.append(',').append(entry.getKey().getMobType());
            str.append('=').append(entry.getValue().name());
        }
        str.append('}');
        return str.toString();
    }
}
