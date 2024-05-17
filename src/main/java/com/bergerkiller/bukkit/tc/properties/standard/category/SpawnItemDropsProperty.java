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
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Whether carts drop items when destroyed
 */
public final class SpawnItemDropsProperty implements ICartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("spawnitemdrops")
    @Command("train spawnitemdrops <spawn>")
    @CommandDescription("Sets whether the train drops items when destroyed")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("spawn") boolean spawn
    ) {
        properties.set(this, spawn);
        getProperty(sender, properties);
    }

    @Command("train spawnitemdrops")
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
    @Command("cart spawnitemdrops <spawn>")
    @CommandDescription("Sets whether the cart drops items when destroyed")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("spawn") boolean spawn
    ) {
        properties.set(this, spawn);
        getProperty(sender, properties);
    }

    @Command("cart spawnitemdrops")
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
