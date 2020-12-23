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
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Whether players can enter carts of a train
 */
public final class PlayerEnterProperty implements ICartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("playerenter")
    @CommandMethod("train playerenter|allowplayerenter <allow>")
    @CommandDescription("Sets whether players can enter carts of this train")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("allow") boolean allow
    ) {
        properties.setPlayersEnter(allow);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train playerenter|allowplayerenter")
    @CommandDescription("Gets whether players can enter carts of this train")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Players can enter the train: "
                + Localization.boolStr(properties.getPlayersEnter()));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("playerenter")
    @CommandMethod("cart playerenter|allowplayerenter <allow>")
    @CommandDescription("Sets whether players can enter the cart")
    private void commandSetProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("allow") boolean allow
    ) {
        properties.setPlayersEnter(allow);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("cart playerenter|allowplayerenter")
    @CommandDescription("Gets whether players can enter the cart")
    private void commandGetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Players can enter the cart: "
                + Localization.boolStr(properties.getPlayersEnter()));
    }

    @PropertyParser("allowplayerenter|playerenter")
    public boolean parsePlayerEnter(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_PLAYERENTER.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.TRUE;
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "allowPlayerEnter", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "allowPlayerEnter", value);
    }
}
