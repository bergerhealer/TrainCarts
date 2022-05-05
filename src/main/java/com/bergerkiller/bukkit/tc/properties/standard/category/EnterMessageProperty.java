package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Quoted;

/**
 * Displays a message to the players when they enter this particular cart
 */
public final class EnterMessageProperty implements ICartProperty<String> {

    @CommandTargetTrain
    @PropertyCheckPermission("entermessage")
    @CommandMethod("train entermessage <message>")
    @CommandDescription("Sets the message displayed to players when they enter the train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Quoted @Argument("message") String message
    ) {
        properties.set(this, message);
        getProperty(sender, properties);
    }

    @CommandMethod("train entermessage")
    @CommandDescription("Displays the message that will be displayed to players when they enter the train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        String message = properties.get(this);
        if (message.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Message displayed: "
                    + ChatColor.RED + "NONE");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Message displayed: "
                    + ChatColor.WHITE + message);
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("entermessage")
    @CommandMethod("cart entermessage <message>")
    @CommandDescription("Sets the message displayed to players when they enter the cart")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Quoted @Argument("message") String message
    ) {
        properties.set(this, message);
        getProperty(sender, properties);
    }

    @CommandMethod("cart entermessage")
    @CommandDescription("Displays the message that will be displayed to players when they enter the cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        String message = properties.get(this);
        if (message.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Message displayed: "
                    + ChatColor.RED + "NONE");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Message displayed: "
                    + ChatColor.WHITE + message);
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ENTER_MESSAGE.has(sender);
    }

    @PropertyParser("entermessage|entermsg")
    public String parseMessage(String input) {
        return input;
    }

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "enterMessage", String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, "enterMessage", value);
    }
}
