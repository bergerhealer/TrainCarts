package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Whether players can exit from a cart
 */
public final class PlayerExitProperty implements ICartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("playerexit")
    @CommandMethod("train playerexit|allowplayerexit|playerleave <allow>")
    @CommandDescription("Sets whether players can exit from carts of this train")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("allow") boolean allow
    ) {
        properties.setPlayersExit(allow);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train playerexit|allowplayerexit|playerleave")
    @CommandDescription("Gets whether players can exit from carts of this train")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Players can exit the train: "
                + Localization.boolStr(properties.getPlayersExit()));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("playerexit")
    @CommandMethod("cart playerexit|allowplayerexit|playerleave <allow>")
    @CommandDescription("Sets whether players can exit the cart")
    private void commandSetProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("allow") boolean allow
    ) {
        properties.setPlayersExit(allow);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("cart playerexit|allowplayerexit|playerleave")
    @CommandDescription("Gets whether players can exit the cart")
    private void commandGetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Players can exit the cart: "
                + Localization.boolStr(properties.getPlayersExit()));
    }

    @PropertyParser("allowplayerexit|playerexit")
    public boolean parsePlayerExit(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_PLAYEREXIT.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.TRUE;
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "allowPlayerExit", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "allowPlayerExit", value);
    }
}
