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
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Configures whether all players can enter a cart, or that only players set as owner
 * of the cart can
 */
public final class OnlyOwnersCanEnterProperty extends FieldBackedStandardCartProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("onlyownerscanenter")
    @CommandMethod("train onlyownerscanenter <state>")
    @CommandDescription("Sets whether only owners can enter the train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("state") boolean state
    ) {
        properties.setCanOnlyOwnersEnter(state);
        getProperty(sender, properties);
    }

    @CommandMethod("train onlyownerscanenter")
    @CommandDescription("Displays whether only owners can enter the train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Only owners can enter the train: "
                + Localization.boolStr(properties.getCanOnlyOwnersEnter()));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("onlyownerscanenter")
    @CommandMethod("cart onlyownerscanenter <state>")
    @CommandDescription("Sets whether only owners can enter the cart")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("state") boolean state
    ) {
        properties.setCanOnlyOwnersEnter(state);
        getProperty(sender, properties);
    }

    @CommandMethod("cart onlyownerscanenter")
    @CommandDescription("Displays whether only owners can enter the cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Only owners can enter the cart: "
                + Localization.boolStr(properties.getCanOnlyOwnersEnter()));
    }

    @PropertyParser("onlyownerscanenter")
    public boolean parseCanEnter(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ONLYOWNERSCANENTER.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean getData(CartInternalData data) {
        return data.canOnlyOwnersEnter;
    }

    @Override
    public void setData(CartInternalData data, Boolean value) {
        data.canOnlyOwnersEnter = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        // Legacy
        if (config.contains("public")) {
            return Optional.of(!config.get("public", true));
        }

        return Util.getConfigOptional(config, "onlyOwnersCanEnter", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        config.remove("public"); // legacy
        Util.setConfigOptional(config, "onlyOwnersCanEnter", value);
    }

    @Override
    public Boolean get(TrainProperties properties) {
        for (CartProperties cProp : properties) {
            if (!get(cProp)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }
}
