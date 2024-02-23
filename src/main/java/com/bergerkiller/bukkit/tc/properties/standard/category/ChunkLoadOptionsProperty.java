package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import cloud.commandframework.annotations.Flag;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.properties.standard.type.ChunkLoadOptions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Controls whether trains keep chunks around them loaded, preventing the train
 * from unloading. Automatically loads the train when enabled for a currently
 * unloaded train. The options also include how the chunks are simulated, and the
 * additional radius of chunks around the carts kept loaded.
 */
public final class ChunkLoadOptionsProperty extends FieldBackedStandardTrainProperty<ChunkLoadOptions> {

    @CommandTargetTrain
    @PropertyCheckPermission("keeploaded")
    @CommandMethod("train keepchunksloaded|keeploaded|loadchunks <mode>")
    @CommandDescription("Sets whether the train keeps chunks loaded, how and optionally with what radius")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") ChunkLoadOptions.Mode mode,
            final @Flag("radius") Integer radius
    ) {
        ChunkLoadOptions options = properties.getChunkLoadOptions();
        options = options.withMode(mode);
        if (radius != null) {
            int radInt = radius.intValue();
            if (radInt > TCConfig.maxKeepChunksLoadedRadius) {
                sender.sendMessage(ChatColor.RED + "Radius " + radInt + " is too big (max: " + TCConfig.maxKeepChunksLoadedRadius + ")");
                radInt = TCConfig.maxKeepChunksLoadedRadius;
            }
            options = options.withRadius(radInt);
        }
        properties.setChunkLoadOptions(options);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train keepchunksloaded|keeploaded|loadchunks")
    @CommandDescription("Gets the chunk loader configuration of the train")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        ChunkLoadOptions options = properties.getChunkLoadOptions();
        int rad = Math.min(TCConfig.maxKeepChunksLoadedRadius, options.radius()) * 2 + 1;
        String radInfo = options.keepLoaded() ? ("" + ChatColor.WHITE + " (" + rad + " x " + rad + " chunks)") : "";
        sender.sendMessage(ChatColor.YELLOW + "Train keeps nearby chunks loaded: "
                + Localization.boolStr(options.keepLoaded()) + radInfo);
        if (options.keepLoaded()) {
            switch (options.mode()) {
                case FULL:
                    sender.sendMessage(ChatColor.YELLOW + "The loaded chunks will simulate entities and redstone");
                    break;
                case REDSTONE:
                    sender.sendMessage(ChatColor.YELLOW + "The loaded chunks will only simulate redstone, not entities");
                    break;
                case MINIMAL:
                    sender.sendMessage(ChatColor.YELLOW + "The loaded chunks will " + ChatColor.RED + "not"
                            + ChatColor.YELLOW + " simulate redstone and entities");
                    break;
            }
        }
    }

    @PropertyParser("keepchunksloaded|keeploaded|keepcloaded|loadchunks")
    public ChunkLoadOptions parseChunkLoadOptions(PropertyParseContext<ChunkLoadOptions> context) {
        ChunkLoadOptions options = context.current();
        for (String word : context.input().split(" ")) {
            // Attempt parsing a new mode to set
            Optional<ChunkLoadOptions.Mode> newMode = ChunkLoadOptions.Mode.fromName(word);
            if (newMode.isPresent()) {
                options = options.withMode(newMode.get());
                continue;
            }

            // Attempt parsing a radius
            Integer radius = ParseUtil.parseInt(word, null);
            if (radius != null) {
                options = options.withRadius(radius.intValue());
            }
        }

        return options;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_KEEPCHUNKSLOADED.has(sender);
    }

    @PropertySelectorCondition("keepchunksloaded")
    public boolean selectorMatchesKeepChunksLoaded(TrainProperties properties, SelectorCondition condition) {
        return condition.matchesBoolean(properties.isKeepingChunksLoaded());
    }

    @Override
    public ChunkLoadOptions getDefault() {
        return ChunkLoadOptions.DEFAULT;
    }

    @Override
    public ChunkLoadOptions getData(TrainInternalData data) {
        return data.chunkLoadOptions;
    }

    @Override
    public void setData(TrainInternalData data, ChunkLoadOptions value) {
        data.chunkLoadOptions = value;
    }

    @Override
    public Optional<ChunkLoadOptions> readFromConfig(ConfigurationNode config) {
        if (config.contains("keepChunksLoaded")) {
            if (config.isNode("keepChunksLoaded")) {
                ConfigurationNode node = config.getNode("keepChunksLoaded");
                ChunkLoadOptions.Mode mode = ChunkLoadOptions.Mode.fromName(node.get("mode", "disabled"))
                        .orElse(ChunkLoadOptions.Mode.DISABLED);
                int radius = Math.min(TCConfig.maxKeepChunksLoadedRadius, node.get("radius", 2));
                return Optional.of(ChunkLoadOptions.of(mode, radius));
            } else {
                // true/false
                return Optional.of(config.get("keepChunksLoaded", false)
                        ? ChunkLoadOptions.LEGACY_TRUE : ChunkLoadOptions.LEGACY_FALSE);
            }
        }

        return Optional.empty();
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<ChunkLoadOptions> value) {
        if (value.isPresent()) {
            ChunkLoadOptions options = value.get();
            if (options.equals(ChunkLoadOptions.LEGACY_TRUE)) {
                config.set("keepChunksLoaded", true);
            } else if (options.equals(ChunkLoadOptions.LEGACY_FALSE)) {
                config.set("keepChunksLoaded", false);
            } else {
                ConfigurationNode node = config.getNode("keepChunksLoaded");
                node.set("mode", options.mode().getNames().get(0));
                node.set("radius", options.radius());
            }
        } else {
            config.remove("keepChunksLoaded");
        }
    }

    @Override
    public void onConfigurationChanged(TrainProperties properties) {
        super.onConfigurationChanged(properties);
        updateState(properties, this.get(properties));
    }

    @Override
    public void set(TrainProperties properties, ChunkLoadOptions value) {
        super.set(properties, value);
        updateState(properties, value);
    }

    private void updateState(TrainProperties properties, ChunkLoadOptions options) {
        // When turning keep chunks loaded on, load the train if presently unloaded
        // We don't have to wait for that
        if (options.keepLoaded()) {
            // If kept loaded, load in the train and once loaded (or already loaded),
            // apply the change to start keeping the chunks loaded
            properties.restore().thenAccept(result -> {
                if (result) {
                    MinecartGroup group = properties.getHolder();
                    if (group != null) {
                        group.keepChunksLoaded(group.getProperties().getChunkLoadOptions().mode());
                    }
                }
            });
        } else {
            // If loaded, tell group to no longer keep loaded
            MinecartGroup group = properties.getHolder();
            if (group != null) {
                group.keepChunksLoaded(options.mode());
            }
        }
    }
}
