package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Configures whether the train takes into account changes in tick rate
 * when updating trains. When enabled, movement is accelerated when the
 * server lags behind, and decelerated when it catches up. This attempts
 * to make trains move with less jitter, at a constant speed.
 */
public class RealtimePhysicsProperty extends FieldBackedStandardTrainProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("realtime")
    @CommandMethod("train realtime <enabled>")
    @CommandDescription("Sets whether the train updates in realtime, adjusting for server tick lag and jitter")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("enabled") boolean enabled
    ) {
        properties.set(this, enabled);
        getProperty(sender, properties);
    }

    @CommandMethod("train realtime")
    @CommandDescription("Displays whether the train updates in realtime, adjusting for server tick lag and jitter")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train updates in realtime: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("realtime")
    public boolean parseRealtime(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_REALTIME.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean getData(TrainInternalData data) {
        return data.realtimePhysics;
    }

    @Override
    public void setData(TrainInternalData data, Boolean value) {
        data.realtimePhysics = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "realtimePhysics", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "realtimePhysics", value);
    }
}
