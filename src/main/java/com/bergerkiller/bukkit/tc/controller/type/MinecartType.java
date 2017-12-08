package com.bergerkiller.bukkit.tc.controller.type;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.EntityType;
import com.bergerkiller.bukkit.tc.Permission;

/**
 * A type of minecart, which has certain default properties attached
 */
public class MinecartType {
    public static final MinecartType DEFAULT;
    private static final Map<String, MinecartType> typeMap = new HashMap<String, MinecartType>();
    private final String _name;
    private final EntityType _entityType;
    private final Permission _permission;

    static {
        DEFAULT = register("m", EntityType.MINECART, Permission.SPAWNER_REGULAR);
        register("s", EntityType.MINECART_CHEST, Permission.SPAWNER_STORAGE);
        register("p", EntityType.MINECART_FURNACE, Permission.SPAWNER_POWERED);
        register("h", EntityType.MINECART_HOPPER, Permission.SPAWNER_HOPPER);
        register("t", EntityType.MINECART_TNT, Permission.SPAWNER_TNT);
        register("e", EntityType.MINECART_MOB_SPAWNER, Permission.SPAWNER_SPAWNER);
        register("c", EntityType.MINECART_COMMAND, Permission.SPAWNER_COMMAND);
    }

    public MinecartType(String name, EntityType entityType, Permission permission) {
        this._name = name;
        this._entityType = entityType;
        this._permission = permission;
    }

    /**
     * The length of this Minecart entity on the tracks
     * This distance is required when spawning
     * 
     * @return length of the cart
     */
    public double getLength() {
        return 1.0;
    }

    public String getName() {
        return this._name;
    }

    public EntityType getEntityType() {
        return this._entityType;
    }

    public Permission getPermission() {
        return this._permission;
    }

    public static MinecartType get(String name) {
        return typeMap.get(name);
    }
    
    private static MinecartType register(String name, EntityType entityType, Permission permission) {
        MinecartType type = new MinecartType(name, entityType, permission);
        typeMap.put(name.toLowerCase(Locale.ENGLISH), type);
        typeMap.put(name.toUpperCase(Locale.ENGLISH), type);
        return type;
    }

    @Deprecated
    public static MinecartType fromEntityType(EntityType entityType) {
        for (MinecartType type : typeMap.values()) {
            if (type.getEntityType() == entityType) {
                return type;
            }
        }
        return DEFAULT;
    }
}
