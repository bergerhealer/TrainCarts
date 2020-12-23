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
 * Whether the train can be destroyed using normal damage or
 * that it is invincible to such.
 */
public final class InvincibleProperty implements ICartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("invincible")
    @CommandMethod("train invincible|godmode <invincible>")
    @CommandDescription("Sets whether the train is invincible to damage")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("invincible") boolean invincible
    ) {
        properties.set(this, invincible);
        getProperty(sender, properties);
    }

    @CommandMethod("train invincible|godmode")
    @CommandDescription("Displays whether the train is invincible to damage")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train is invincible to damage: "
                + Localization.boolStr(properties.get(this)));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("invincible")
    @CommandMethod("cart invincible|godmode <invincible>")
    @CommandDescription("Sets whether the cart is invincible to damage")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("invincible") boolean invincible
    ) {
        properties.set(this, invincible);
        getProperty(sender, properties);
    }

    @CommandMethod("cart invincible|godmode")
    @CommandDescription("Displays whether the cart is invincible to damage")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Cart is invincible to damage: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("invincible|godmode")
    public boolean parseInvincible(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_INVINCIBLE.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "invincible", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "invincible", value);
    }
}
