package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Applies a factor to the effects gravity has on a train.
 * Lower gravity will allow for slower speeding up of the train.
 */
public final class GravityProperty extends FieldBackedStandardTrainProperty.StandardDouble {

    @CommandTargetTrain
    @PropertyCheckPermission("gravity")
    @CommandMethod("train gravity <multiplier>")
    @CommandDescription("Sets a gravity effect multiplier for the train")
    private void trainSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("multiplier") double multiplier
    ) {
        properties.setGravity(multiplier);
        trainGetProperty(sender, properties);
    }

    @CommandMethod("train gravity")
    @CommandDescription("Displays the gravity multiplier currently set for the train")
    private void trainGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getGravity() == 1.0) {
            sender.sendMessage(ChatColor.YELLOW + "Gravity multiplier: " + ChatColor.WHITE
                    + "1 X (default)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Gravity multiplier: " + ChatColor.WHITE
                    + properties.getGravity() + " X");
        }
    }

    @PropertyParser("gravity")
    public double parseGravity(PropertyParseContext<Double> context) {
        return context.inputDouble();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_GRAVITY.has(sender);
    }

    @Override
    public double getDoubleDefault() {
        return 1.0;
    }

    @Override
    public double getDoubleData(TrainInternalData data) {
        return data.gravity;
    }

    @Override
    public void setDoubleData(TrainInternalData data, double value) {
        data.gravity = value;
    }

    @Override
    public Optional<Double> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "gravity", double.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
        Util.setConfigOptional(config, "gravity", value);
    }
}
