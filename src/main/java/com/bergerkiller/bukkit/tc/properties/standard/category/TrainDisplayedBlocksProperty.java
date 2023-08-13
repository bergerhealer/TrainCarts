package com.bergerkiller.bukkit.tc.properties.standard.category;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainDisplayedBlocks;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import org.bukkit.command.CommandSender;

import java.util.Optional;

/**
 * Legacy property which was used to instantly set all the displayed blocks (block changer)
 * of a train when spawned, or when using a setdefault property sign. This property stores
 * a ;-separated list of block types to set ('pattern') and the offset to put the block at.
 * When applying this property it updates the metadata of the live train. No ingame accessible
 * way exists to set this property, it's only used with DefaultTrainProperties.<br>
 * <br>
 * This is not to be confused with the data.displayedBlock information stored in saved train
 * cart configurations.
 */
public class TrainDisplayedBlocksProperty implements ITrainProperty<TrainDisplayedBlocks> {

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.BUILD_BLOCKCHANGER.has(sender);
    }

    @Override
    public TrainDisplayedBlocks getDefault() {
        return TrainDisplayedBlocks.DEFAULT;
    }

    @Override
    public void set(TrainProperties properties, TrainDisplayedBlocks value) {
        ITrainProperty.super.set(properties, value);

        // This only works if the train is loaded, so, when being spawned
        // After that the minecart metadata should keep it all consistent
        if (value != null) {
            MinecartGroup group = properties.getHolder();
            if (group != null) {
                SignActionBlockChanger.setBlocks(group, value);
            }
        }
    }

    @Override
    public Optional<TrainDisplayedBlocks> readFromConfig(ConfigurationNode config) {
        if (config.contains("blockTypes") || config.contains("blockOffset")) {
            String blockTypes = config.getOrDefault("blockTypes", "");
            int blockOffset = config.getOrDefault("blockOffset", TrainDisplayedBlocks.BLOCK_OFFSET_NONE);
            return Optional.of(TrainDisplayedBlocks.of(blockTypes, blockOffset));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<TrainDisplayedBlocks> value) {
        if (value.isPresent()) {
            TrainDisplayedBlocks displayedBlocks = value.get();
            config.set("blockTypes", displayedBlocks.getBlockTypesPattern());
            config.set("blockOffset", displayedBlocks.getOffset());
        } else {
            config.remove("blockTypes");
            config.remove("blockOffset");
        }
    }
}
