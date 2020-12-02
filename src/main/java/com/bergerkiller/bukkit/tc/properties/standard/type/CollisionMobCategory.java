package com.bergerkiller.bukkit.tc.properties.standard.type;

import java.util.Set;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.utils.EntityGroupingUtil;
import com.bergerkiller.bukkit.common.utils.EntityGroupingUtil.EntityCategory;
import com.bergerkiller.bukkit.tc.CollisionMode;

/**
 * This enum contains entity types that a minecart could collide with.
 * This allows the user to configure collision modes in a very granular
 * fashion. The categories here are based on the minecraft wiki
 * (http://minecraft.gamepedia.com/Mobs#List_of_mobs) and in
 * com.bergerkiller.bukkit.common.utils.EntityGroupingUtil.
 */
public enum CollisionMobCategory {
    /*
     * Short Name, Plural Name, EntityCategory or method name, Legacy Mob, Display String, Default Collision Mode
     * Legacy Mob = In versions of TrainCarts for Minecraft <= 1.7
     */
    PETS("pet", "pets", EntityCategory.TAMED.getEntityClasses(), "Pets", null),
    JOCKEYS("jockey", "jockeys", EntityCategory.JOCKEY.getEntityClasses(), "Jockeys", null),
    KILLER_BUNNIES("killer_bunny", "killer_bunnies", EntityCategory.KILLER_BUNNY.getEntityClasses(), "Killer Bunnies", null),
    NPCS("npc", "npcs", EntityCategory.NPC.getEntityClasses(), "NPCs", null),
    ANIMALS("animal", "animals", EntityCategory.ANIMAL.getEntityClasses(), "Animals", null),
    MONSTERS("monster", "monsters", EntityCategory.MONSTER.getEntityClasses(), "Monsters", null),
    PASSIVE_MOBS("passive", "passives", EntityCategory.PASSIVE.getEntityClasses(), "Passive Mobs", CollisionMode.DEFAULT),
    NEUTRAL_MOBS("neutral", "neutrals", EntityCategory.NEUTRAL.getEntityClasses(), "Neutral Mobs", CollisionMode.DEFAULT),
    HOSTILE_MOBS("hostile", "hostiles", EntityCategory.HOSTILE.getEntityClasses(), "Hostile Mobs", CollisionMode.DEFAULT),
    TAMEABLE_MOBS("tameable", "tameables", EntityCategory.TAMEABLE.getEntityClasses(), "Tameable Mobs", CollisionMode.DEFAULT),
    UTILITY_MOBS("utility", "utilities", EntityCategory.UTILITY.getEntityClasses(), "Utility Mobs", CollisionMode.DEFAULT),
    BOSS_MOBS("boss", "bosses", EntityCategory.BOSS.getEntityClasses(), "Boss Mobs", CollisionMode.DEFAULT);

    private String mobType;
    private String friendlyMobName;
    private String pluralMobType;
    private CollisionMode defaultCollisionMode;
    private Set<Class<?>> entityClasses;

    CollisionMobCategory(String mobType, String pluralMobType, Set<Class<?>> entityClasses, String friendlyMobName, CollisionMode defaultCollisionMode) {
        this.setMobType(mobType);
        this.setFriendlyMobName(friendlyMobName);
        this.setDefaultCollisionMode(defaultCollisionMode);
        this.setEntityClasses(entityClasses);
    }

    public boolean isMobType(Entity entity) {
        if (entityClasses != null && !entityClasses.isEmpty()) {
            if (EntityGroupingUtil.isEntityTypeClass(entity, entityClasses)) {
                return true;
            }
        }
        return false;
    }

    public String getMobType() {
        return mobType;
    }

    public void setMobType(String mobType) {
        this.mobType = mobType;
    }

    /**
     * Whether this collision config mode is for a group of mobs
     * 
     * @return mobs
     */
    public boolean isMobCategory() {
        return this.name().endsWith("_MOBS");
    }

    public static CollisionMobCategory findMobType(Entity entity) {
        for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
            if (collisionConfigObject.isMobType(entity)) {
                return collisionConfigObject;
            }
        }
        return null;
    }

    public static CollisionMobCategory findMobType(String entityType) {
        for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
            if (collisionConfigObject.getMobType().equals(entityType)) {
                return collisionConfigObject;
            }
        }
        return null;
    }

    public static CollisionMobCategory findMobType(String entityType, String prefix) {
        if (prefix == null) {
            return findMobType(entityType);
        } else {
            return findMobType(entityType.substring(prefix.length()));
        }
    }

    public static CollisionMobCategory findMobType(String entityType, String prefix, String suffix) {
        if (prefix == null && suffix == null) {
            return findMobType(entityType);
        } else if (suffix == null) {
            return findMobType(entityType, prefix);
        } else {
            return findMobType(entityType.substring(0, entityType.length()-suffix.length()), prefix);
        }
    }

    public String getFriendlyMobName() {
        return friendlyMobName;
    }

    public void setFriendlyMobName(String friendlyMobName) {
        this.friendlyMobName = friendlyMobName;
    }

    public String getPluralMobType() {
        return this.pluralMobType;
    }

    public void setPluralMobType(String pluralMobType) {
        this.pluralMobType = pluralMobType;
    }

    public CollisionMode getDefaultCollisionMode() {
        return defaultCollisionMode;
    }

    public void setDefaultCollisionMode(CollisionMode defaultCollisionMode) {
        this.defaultCollisionMode = defaultCollisionMode;
    }

    public Set<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    public void setEntityClasses(Set<Class<?>> entityClasses) {
        this.entityClasses = entityClasses;
    }
}
