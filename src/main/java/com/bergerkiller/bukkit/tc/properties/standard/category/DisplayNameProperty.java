package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;
import net.md_5.bungee.api.ChatColor;

/**
 * Configures the display name of a train, which is currently
 * used for trigger signs to display a custom name on signs.
 */
public final class DisplayNameProperty implements ITrainProperty<String> {

    @CommandTargetTrain
    @PropertyCheckPermission("displayname")
    @CommandMethod("train displayname <name>")
    @CommandDescription("Sets the display name of the train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("name") @Greedy String name
    ) {
        properties.set(this, name);
        getProperty(sender, properties);
    }

    @CommandMethod("train displayname")
    @CommandDescription("Displays whether the current display name of the train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Display name of the train: "
                + ChatColor.WHITE + properties.get(this));
    }

    @PropertyParser("dname|displayname|setdisplayname|setdname")
    public String parseName(String input) {
        return input;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_DISPLAYNAME.has(sender);
    }

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "displayName", String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, "displayName", value);
    }
}
