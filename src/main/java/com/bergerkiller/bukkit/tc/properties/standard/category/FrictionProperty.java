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
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Applies a factor to the effects friction has on a train.
 * Friction controls the gradual slowing down effects of trains while
 * moving on rails.
 */
public final class FrictionProperty extends FieldBackedStandardTrainProperty.StandardDouble {

    @CommandTargetTrain
    @PropertyCheckPermission("friction")
    @CommandMethod("train friction <multiplier>")
    @CommandDescription("Sets a friction effect multiplier for the train")
    private void trainSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("multiplier") double multiplier
    ) {
        properties.setFriction(multiplier);
        trainGetProperty(sender, properties);
    }

    @CommandMethod("train friction")
    @CommandDescription("Displays the friction multiplier currently set for the train")
    private void trainGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getFriction() == 1.0) {
            sender.sendMessage(ChatColor.YELLOW + "Friction multiplier: " + ChatColor.WHITE
                    + "1 X (default)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Friction multiplier: " + ChatColor.WHITE
                    + properties.getFriction() + " X");
        }
    }

    @PropertyParser("friction")
    public double parseFriction(PropertyParseContext<Double> context) {
        return context.inputDouble();
    }

    @PropertySelectorCondition("friction")
    public double selectorgetValue(TrainProperties properties) {
        return getDouble(properties);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_FRICTION.has(sender);
    }

    @Override
    public double getDoubleDefault() {
        return 1.0;
    }

    @Override
    public double getDoubleData(TrainInternalData data) {
        return data.friction;
    }

    @Override
    public void setDoubleData(TrainInternalData data, double value) {
        data.friction = value;
    }

    @Override
    public Optional<Double> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "friction", double.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
        Util.setConfigOptional(config, "friction", value);
    }
}
