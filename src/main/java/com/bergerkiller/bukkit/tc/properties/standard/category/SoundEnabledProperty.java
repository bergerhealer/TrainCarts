package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

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
import net.md_5.bungee.api.ChatColor;

/**
 * Whether sound plays while the train moves
 */
public final class SoundEnabledProperty extends FieldBackedStandardTrainProperty<Boolean> {

    @CommandTargetTrain
    @PropertyCheckPermission("soundenabled")
    @CommandMethod("train soundenabled|sound <enabled>")
    @CommandDescription("Sets whether the train makes sound while moving")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("enabled") boolean enabled
    ) {
        properties.set(this, enabled);
        getProperty(sender, properties);
    }

    @CommandMethod("train soundenabled|sound")
    @CommandDescription("Displays whether the train makes sound while moving")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train makes sound while moving: "
                + Localization.boolStr(properties.get(this)));
    }

    @PropertyParser("sound|soundenabled|minecartsound")
    public boolean parseSoundEnabled(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_SOUNDENABLED.has(sender);
    }

    @Override
    public Boolean getDefault() {
        return Boolean.TRUE;
    }

    @Override
    public Boolean getData(TrainInternalData data) {
        return data.soundEnabled;
    }

    @Override
    public void setData(TrainInternalData data, Boolean value) {
        data.soundEnabled = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "soundEnabled", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "soundEnabled", value);
    }
}
