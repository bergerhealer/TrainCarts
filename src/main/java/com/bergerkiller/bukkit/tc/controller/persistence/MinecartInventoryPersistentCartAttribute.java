package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartInventory;
import com.bergerkiller.bukkit.tc.Util;

/**
 * The items stored in a Minecart that supports an inventory
 */
public class MinecartInventoryPersistentCartAttribute implements PersistentCartAttribute<CommonMinecartInventory<?>> {
    @Override
    public void save(CommonMinecartInventory<?> entity, ConfigurationNode data) {
        Util.saveInventoryToConfig(entity.getInventory(), data);
    }

    @Override
    public void load(CommonMinecartInventory<?> entity, ConfigurationNode data) {
        Util.loadInventoryFromConfig(entity.getInventory(), data);
    }
}
