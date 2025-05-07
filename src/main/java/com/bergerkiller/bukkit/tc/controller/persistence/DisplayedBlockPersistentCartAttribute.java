package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Persists the displayed block and block offset set for the Minecart entity
 */
@SuppressWarnings("deprecation")
public class DisplayedBlockPersistentCartAttribute implements PersistentCartAttribute<CommonMinecart<?>>  {
    @Override
    public void load(CommonMinecart<?> entity, ConfigurationNode data) {
        if (data.isNode("displayedBlock")) {
            ConfigurationNode displayedBlock = data.getNode("displayedBlock");
            if (displayedBlock.contains("offset")) {
                entity.setBlockOffset(displayedBlock.get("offset", Util.getDefaultDisplayedBlockOffset()));
            }
            if (displayedBlock.contains("type")) {
                BlockData type = BlockData.fromCombinedId(displayedBlock.get("type", 0));
                if (type != null && type != BlockData.AIR) {
                    entity.setBlock(type);
                }
            }
        }
    }

    @Override
    public void save(CommonMinecart<?> entity, ConfigurationNode data) {
        int offset = entity.getBlockOffset();
        BlockData block = entity.getBlock();
        boolean hasOffset = (offset != Util.getDefaultDisplayedBlockOffset());
        boolean hasBlock = (block != null && block != BlockData.AIR);
        if (hasOffset || hasBlock) {
            // Save displayed block information
            ConfigurationNode displayedBlock = data.getNode("displayedBlock");
            displayedBlock.set("offset", hasOffset ? offset : null);
            displayedBlock.set("type", hasBlock ? block.getCombinedId() : null);
        } else {
            data.remove("displayedBlock");
        }
    }
}
