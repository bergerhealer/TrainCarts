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
 * Whether mobs inside a train can set it in motion
 */
public final class AllowManualMobMovementProperty extends FieldBackedStandardTrainProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("allowmobmanual")
    @CommandMethod("train manualmovement mob <enabled>")
    @CommandDescription("Sets whether mobs seated in the train can cause the train to move")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("enabled") boolean enabled
    ) {
        properties.set(this, enabled);
        getProperty(sender, properties);
    }

    @CommandMethod("train manualmovement mob")
    @CommandDescription("Displays whether mobs seated in the train can cause the train to move")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Mobs in the train can set the train in motion: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("allowmobmanual|mobmanualmove|mobmanual|manualmovement mob")
    public boolean parseAllowMovement(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ALLOWMOBMANUALMOVEMENT.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean getData(TrainInternalData data) {
        return data.allowMobManualMovement;
    }

    @Override
    public void setData(TrainInternalData data, Boolean value) {
        data.allowMobManualMovement = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "allowMobManualMovement", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "allowMobManualMovement", value);
    }
}
