package com.bergerkiller.bukkit.tc.properties.standard.type;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.CollisionMode;

/**
 * Immutable representation of all the collision settings of a train.
 * This defines how trains behave when colliding with entities and blocks.
 */
public final class CollisionOptions {
    private static final EnumMap<CollisionMobCategory, CollisionMode> NO_MOB_MODES = new EnumMap<>(CollisionMobCategory.class);

    /**
     * Default collision configuration of TrainCarts trains
     */
    public static final CollisionOptions DEFAULT = new CollisionOptions(
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
    public static final CollisionOptions CANCEL = new CollisionOptions(
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

    private CollisionOptions(
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

    public CollisionOptions cloneAndSetPlayerMode(CollisionMode mode) {
        // Check unchanged
        if (this.playerMode == mode) {
            return this;
        }

        return new CollisionOptions(this.mobModes,
                                   mode,
                                   this.miscMode,
                                   this.trainMode,
                                   this.blockMode);
    }

    public CollisionOptions cloneAndSetMiscMode(CollisionMode mode) {
        // Check unchanged
        if (this.miscMode == mode) {
            return this;
        }

        return new CollisionOptions(this.mobModes,
                                   this.playerMode,
                                   mode,
                                   this.trainMode,
                                   this.blockMode);
    }

    public CollisionOptions cloneAndSetTrainMode(CollisionMode mode) {
        // Check unchanged
        if (this.trainMode == mode) {
            return this;
        }

        return new CollisionOptions(this.mobModes,
                                   this.playerMode,
                                   this.miscMode,
                                   mode,
                                   this.blockMode);
    }

    public CollisionOptions cloneAndSetBlockMode(CollisionMode mode) {
        // Check unchanged
        if (this.blockMode == mode) {
            return this;
        }

        return new CollisionOptions(this.mobModes,
                                   this.playerMode,
                                   this.miscMode,
                                   this.trainMode,
                                   mode);
    }

    /**
     * Checks all collision mob categories and if their current
     * collision mode matches the expected value, updated the mode
     * to the new value.
     * 
     * @param expected Expected mode, null to expect none to be set (defaults)
     * @param newModeIfExpected New mode, null to remove the mode
     * @return updated collision options
     */
    public CollisionOptions cloneCompareAndSetForAllMobs(CollisionMode expected, CollisionMode newModeIfExpected) {
        EnumMap<CollisionMobCategory, CollisionMode> modes = this.mobModes.clone();
        if (newModeIfExpected == null) {
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (category.isMobCategory() && modes.get(category) == expected) {
                    modes.remove(category);
                }
            }
        } else {
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (category.isMobCategory() && modes.get(category) == expected) {
                    modes.put(category, newModeIfExpected);
                }
            }
        }

        return new CollisionOptions(modes,
                this.playerMode,
                this.miscMode,
                this.trainMode,
                this.blockMode);
    }

    public CollisionOptions cloneAndSetForAllMobs(CollisionMode mode) {
        EnumMap<CollisionMobCategory, CollisionMode> modes = this.mobModes.clone();
        if (mode == null) {
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (category.isMobCategory()) {
                    modes.remove(category);
                }
            }
        } else {
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (category.isMobCategory()) {
                    modes.put(category, mode);
                }
            }
        }

        return new CollisionOptions(modes,
                this.playerMode,
                this.miscMode,
                this.trainMode,
                this.blockMode);
    }

    public CollisionOptions cloneAndSetMobMode(CollisionMobCategory category, CollisionMode mode) {
        // Check not null
        if (category == null) {
            throw new IllegalArgumentException("Collision mob category can not be null");
        }

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

        return new CollisionOptions(modes,
                                   this.playerMode,
                                   this.miscMode,
                                   this.trainMode,
                                   this.blockMode);
    }

    @Override
    public int hashCode() {
        return this.playerMode.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof CollisionOptions) {
            CollisionOptions other = (CollisionOptions) o;
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

    /**
     * Creates a new Builder object with the initial default collision configuration.
     * 
     * @return new builder
     */
    public static Builder builder() {
        return new Builder(DEFAULT);
    }

    /**
     * Creates a new Builder object with the initial collision configuration specified.
     * 
     * @return new builder
     */
    public static Builder builder(CollisionOptions initial) {
        return new Builder(initial);
    }

    /**
     * Builder helper class for setting many fields at once
     */
    public static final class Builder {
        private final EnumMap<CollisionMobCategory, CollisionMode> mobModes;
        private CollisionMode playerMode;
        private CollisionMode miscMode;
        private CollisionMode trainMode;
        private CollisionMode blockMode;

        private Builder(CollisionOptions initial) {
            this.mobModes = new EnumMap<>(CollisionMobCategory.class);
            this.mobModes.putAll(initial.mobModes());
            this.playerMode = initial.playerMode();
            this.miscMode = initial.miscMode();
            this.trainMode = initial.trainMode();
            this.blockMode = initial.blockMode();
        }

        /**
         * Sets the collision mode to use when colliding with players
         * 
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setPlayerMode(CollisionMode mode) {
            this.playerMode = mode;
            return this;
        }

        /**
         * Sets the collision mode to use when colliding with miscellaneous entities.
         * These are all entities that are not players or trains and don't match
         * any configured {@link #setMobMode(CollisionMobCategory, CollisionMode)}.
         * 
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setMiscMode(CollisionMode mode) {
            this.miscMode = mode;
            return this;
        }

        /**
         * Sets the collision mode to use when colliding with other trains
         * 
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setTrainMode(CollisionMode mode) {
            this.trainMode = mode;
            return this;
        }

        /**
         * Sets the collision mode to use when colliding with blocks
         * 
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setBlockMode(CollisionMode mode) {
            this.blockMode = mode;
            return this;
        }

        /**
         * Sets the collision mode to use when colliding with a particular
         * category of mob
         * 
         * @param category The mob category to configure
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setMobMode(CollisionMobCategory category, CollisionMode mode) {
            if (category == null) {
                throw new IllegalArgumentException("Collision mob category cannot be null");
            }
            if (mode == null) {
                this.mobModes.remove(category);
            } else {
                this.mobModes.put(category, mode);
            }
            return this;
        }

        /**
         * Sets the collision mode to use for all categories of mob
         * 
         * @param mode The CollisionMode to use
         * @return this builder
         */
        public Builder setModeForAllMobs(CollisionMode mode) {
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (category.isMobCategory()) {
                    setMobMode(category, mode);
                }
            }
            return this;
        }

        /**
         * Constructs a CollisionOptions instance using all currently configured options
         * 
         * @return built CollisionOptions
         */
        public CollisionOptions build() {
            return new CollisionOptions(
                    this.mobModes.isEmpty() ? NO_MOB_MODES : this.mobModes,
                    this.playerMode,
                    this.miscMode,
                    this.trainMode,
                    this.blockMode
            );
        }
    }
}
