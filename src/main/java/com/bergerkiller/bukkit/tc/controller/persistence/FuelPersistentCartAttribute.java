package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;

/**
 * The fuel ticks remaining in a furnace minecart
 */
public class FuelPersistentCartAttribute implements PersistentCartAttribute<CommonMinecartFurnace> {
    @Override
    public void save(CommonMinecartFurnace entity, ConfigurationNode data) {
        if (entity.getFuelTicks() > 0) {
            data.set("fuel", entity.getFuelTicks());
        } else {
            data.remove("fuel");
        }
    }

    @Override
    public void load(CommonMinecartFurnace entity, ConfigurationNode data) {
        if (data.contains("fuel")) {
            entity.setFuelTicks(data.get("fuel", 0));
        } else {
            entity.setFuelTicks(0);
        }
        entity.setSmoking(entity.getFuelTicks() > 0);
    }
}
