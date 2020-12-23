package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Whether players take carts with them when they leave the server
 */
public final class AllowPlayerTakeProperty implements ITrainProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("allowplayertake")
    @CommandMethod("train allowplayertake|playertake <allow>")
    @CommandDescription("Sets whether players take carts of the train with them when they leave the server")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("allow") boolean allow
    ) {
        properties.set(this, allow);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train allowplayertake|playertake")
    @CommandDescription("Displays whether players take carts of the train with them when they leave the server")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Players can take carts with them when they"
                + " leave the server: " + Localization.boolStr(properties.get(this)));
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ALLOWPLAYERTAKE.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "allowPlayerTake", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "allowPlayerTake", value);
    }
}
