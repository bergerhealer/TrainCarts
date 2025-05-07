package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartCommandBlock;

/**
 * The command string set in a command block minecart
 */
public class CommandPersistentCartAttribute implements PersistentCartAttribute<CommonMinecartCommandBlock> {
    @Override
    public void save(CommonMinecartCommandBlock entity, ConfigurationNode data) {
        data.set("command", entity.metaCommand.get());
    }

    @Override
    public void load(CommonMinecartCommandBlock entity, ConfigurationNode data) {
        if (data.contains("command")) {
            entity.metaCommand.set(data.get("command", ""));
        }
    }
}
