package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

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
import net.md_5.bungee.api.ChatColor;

/**
 * Whether carts drop items when destroyed
 */
public final class SpawnItemDropsProperty implements ICartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("spawnitemdrops")
    @CommandMethod("train spawnitemdrops <spawn>")
    @CommandDescription("Sets whether the train drops items when destroyed")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("spawn") boolean spawn
    ) {
        properties.set(this, spawn);
        getProperty(sender, properties);
    }

    @CommandMethod("train spawnitemdrops")
    @CommandDescription("Displays whether the train drops items when destroyed")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train drops items when destroyed: "
                + Localization.boolStr(properties.get(this)));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("spawnitemdrops")
    @CommandMethod("cart spawnitemdrops <spawn>")
    @CommandDescription("Sets whether the cart drops items when destroyed")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("spawn") boolean spawn
    ) {
        properties.set(this, spawn);
        getProperty(sender, properties);
    }

    @CommandMethod("cart spawnitemdrops")
    @CommandDescription("Displays whether the cart drops items when destroyed")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Cart drops items when destroyed: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("spawnitemdrops|spawndrops|killdrops")
    public boolean parseSpawnItemDrops(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_SPAWNITEMDROPS.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.TRUE;
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "spawnItemDrops", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "spawnItemDrops", value);
    }
}
