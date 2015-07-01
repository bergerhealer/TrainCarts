package com.bergerkiller.bukkit.tc.properties;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.utils.EntityGroupingUtil;
import com.bergerkiller.bukkit.common.utils.EntityGroupingUtil.EntityCategory;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TrainCarts;

/*
 * This enum contains entity types that a minecart could collide with.
 * This allows the user to configure collision modes in a very granular
 * fashion. The categories here are based on the minecraft wiki
 * (http://minecraft.gamepedia.com/Mobs#List_of_mobs) and in
 * com.bergerkiller.bukkit.common.utils.EntityGroupingUtil.
 */

public enum CollisionConfig {
    /*
     * Short Name, Plural Name, EntityCategory or method name, Legacy Mob, Display String, Default Collision Mode
     * Legacy Mob = In versions of TrainCarts for Minecraft <= 1.7
     */
    PETS("pet", "pets", EntityCategory.TAMED, false, "Pets", null),
    JOCKEYS("jockey", "jockeys", EntityCategory.JOCKEY, false, "Jockeys", null),
    KILLER_BUNNIES("killer_bunny", "killer_bunnies", EntityCategory.KILLER_BUNNY, false, "Killer Bunnies", null),
    NPCS("npc", "npcs", EntityCategory.NPC, false, "NPCs", null),
    ANIMALS("animal", "animals", EntityCategory.ANIMAL, false, "Animals", null),
    MONSTERS("monster", "monsters", EntityCategory.MONSTER, false, "Monsters", null),
    PASSIVE_MOBS("passive", "passives", EntityCategory.PASSIVE, true, "Passive Mobs", CollisionMode.DEFAULT),
    NEUTRAL_MOBS("neutral", "neutrals", EntityCategory.NEUTRAL, true, "Neutral Mobs", CollisionMode.DEFAULT),
    HOSTILE_MOBS("hostile", "hostiles", EntityCategory.HOSTILE, true, "Hostile Mobs", CollisionMode.DEFAULT),
    TAMEABLE_MOBS("tameable", "tameables", EntityCategory.TAMEABLE, true, "Tameable Mobs", CollisionMode.DEFAULT),
    UTILITY_MOBS("utility", "utilities", EntityCategory.UTILITY, true, "Utility Mobs", CollisionMode.DEFAULT),
    BOSS_MOBS("boss", "bosses", EntityCategory.BOSS, true, "Boss Mobs", CollisionMode.DEFAULT);

    private String mobType;
    private Method method;
    private boolean addToConfigFile;
    private String friendlyMobName;
    private String pluralMobType;
    private CollisionMode defaultCollisionMode;
    private Set<Class<?>> entityClasses;

    CollisionConfig(String mobType, String pluralMobType, EntityCategory entityCategory, boolean addToConfigFile, String friendlyMobName, CollisionMode defaultCollisionMode) {
        this.setMobType(mobType);
        this.setMethod(null);
        this.setAddToConfigFile(addToConfigFile);
        this.setFriendlyMobName(friendlyMobName);
        this.setDefaultCollisionMode(defaultCollisionMode);
        this.setEntityClasses(entityCategory.getEntityClasses());
    }

    public boolean isMobType(Entity entity) {
        if (entityClasses != null && entityClasses.size() > 0) {
            if (EntityGroupingUtil.isEntityTypeClass(entity, entityClasses)) {
                return true;
            }
        }
        if (method != null) {
            try {
                Object result = method.invoke(null, entity);
                if (result == null) {
                    TrainCarts.plugin.log(Level.WARNING, "Invoke method (" + method.getName() + ") returned null!");
                    return false;
                }
                return (boolean) result;
            } catch (Exception e) {
                TrainCarts.plugin.log(Level.WARNING, "Method (" + method.getName() + ") threw exception: " + e.toString());
                return false;
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

    public boolean isAddToConfigFile() {
        return addToConfigFile;
    }

    public void setAddToConfigFile(boolean addToConfigFile) {
        this.addToConfigFile = addToConfigFile;
    }

    public static CollisionConfig findMobType(Entity entity) {
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (collisionConfigObject.isMobType(entity)) {
                return collisionConfigObject;
            }
        }
        return null;
    }

    public static CollisionConfig findMobType(String entityType) {
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (collisionConfigObject.getMobType().equals(entityType)) {
                return collisionConfigObject;
            }
        }
        return null;
    }

    public static CollisionConfig findMobType(String entityType, String prefix) {
        if (prefix == null) {
            return findMobType(entityType);
        } else {
            return findMobType(entityType.substring(prefix.length()));
        }
    }

    public static CollisionConfig findMobType(String entityType, String prefix, String suffix) {
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

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Set<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    public void setEntityClasses(Set<Class<?>> entityClasses) {
        this.entityClasses = entityClasses;
    }

}
