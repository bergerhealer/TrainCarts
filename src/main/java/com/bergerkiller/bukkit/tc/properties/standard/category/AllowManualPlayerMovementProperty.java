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
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Whether players in a train can set it in motion using W/A/S/D steering controls
 */
public final class AllowManualPlayerMovementProperty extends FieldBackedStandardTrainProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("allowmanual")
    @Command("train manualmovement player <enabled>")
    @CommandDescription("Sets whether the train can be controlled by player passengers using steering controls")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("enabled") boolean enabled
    ) {
        properties.set(this, enabled);
        getProperty(sender, properties);
    }

    @Command("train manualmovement player")
    @CommandDescription("Displays whether the train can be controlled by player passengers using steering controls")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Player passengers can control train movement: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("allowmanual|manualmove|manual|manualmovement player")
    public boolean parseAllowMovement(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ALLOWPLAYERMANUALMOVEMENT.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean getData(TrainInternalData data) {
        return data.allowPlayerManualMovement;
    }

    @Override
    public void setData(TrainInternalData data, Boolean value) {
        data.allowPlayerManualMovement = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "allowManualMovement", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "allowManualMovement", value);
    }
}
