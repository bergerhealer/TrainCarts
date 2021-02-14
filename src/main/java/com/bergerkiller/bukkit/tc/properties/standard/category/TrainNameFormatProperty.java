package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;

/**
 * Stores the name format that is used when renaming trains. When the format
 * is updated, automatically renames the train to that format using
 * {@link TrainProperties#setTrainName(String)}. Adds a parser which allows
 * the train name to be changed using property signs.
 */
public final class TrainNameFormatProperty implements ITrainProperty<TrainNameFormat> {

    @CommandTargetTrain
    @PropertyCheckPermission("name")
    @CommandMethod("train rename|setname|name <new_name>")
    @CommandDescription("Renames a train")
    private void trainSetNameFormat(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("new_name") TrainNameFormat newName,
            final @Flag(value="generate", description="Append a number to make the name unique, if needed") boolean generate
    ) {
        if (!newName.hasOptionalNumber()
                && !generate
                && TrainPropertiesStore.exists(newName.toString())
                && !newName.matches(properties.getTrainName())
        ) {
            // Must not already be in use
            sender.sendMessage(ChatColor.RED + "This name is already taken! Some suggestions:");
            sender.sendMessage(ChatColor.RED + "- Include # somewhere in the name to insert a random number");
            sender.sendMessage(ChatColor.RED + "- Pass --generate to append on if needed");
            return;
        }

        properties.set(this, newName);

        MessageBuilder builder = new MessageBuilder();
        appendNameInfo(builder, properties, "Train renamed to: ");
        builder.send(sender);
    }

    @CommandMethod("train rename|setname|name")
    @CommandDescription("Displays the current name of the train being edited")
    private void trainGetNameFormat(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder builder = new MessageBuilder();
        appendNameInfo(builder, properties, "Train name: ");
        builder.send(sender);
    }

    /**
     * Appends the name and format currently set for a train to a message
     *
     * @param builder Message builder to append the information to
     * @param properties Properties to read the name from
     * @param prefix Prefix for the information message
     */
    public void appendNameInfo(MessageBuilder builder, TrainProperties properties, String prefix) {
        TrainNameFormat format = properties.get(this);
        if (TrainNameFormat.DEFAULT.equals(format)) {
            // Train was not renamed
            builder.yellow(prefix).white(properties.getTrainName());
            builder.red(" (Default)");
        } else if (format.toString().equals(properties.getTrainName())) {
            // No digits used in format, probably some unique name was given
            builder.yellow(prefix).white(properties.getTrainName());
        } else {
            // Show name and format
            builder.yellow(prefix).white(properties.getTrainName());
            builder.yellow(" (Format: ").blue(format).yellow(")");
        }
    }

    @PropertyParser("name|rename|setname|settrainname")
    public TrainNameFormat parseRename(String nameFormat) {
        return TrainNameFormat.parse(nameFormat);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_NAME.has(sender);
    }

    @Override
    public TrainNameFormat getDefault() {
        return TrainNameFormat.DEFAULT;
    }

    @Override
    public Optional<TrainNameFormat> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "name", String.class)
                .map(TrainNameFormat::parse);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<TrainNameFormat> value) {
        Util.setConfigOptional(config, "name",
                value.map(TrainNameFormat::toString));
    }

    @Override
    public TrainNameFormat get(TrainProperties properties) {
        // First try from configuration
        Optional<TrainNameFormat> fromConfig = this.readFromConfig(properties.getConfig());
        if (fromConfig.isPresent()) {
            return fromConfig.get();
        }

        // If train name matches the default, then no renaming happened
        // In that case, just return the default.
        // This should be the most common case!
        if (TrainNameFormat.DEFAULT.matches(properties.getTrainName())) {
            return TrainNameFormat.DEFAULT;
        }

        // Migration: in the past, the name format was not stored
        // alongside the train name. Try to guess a suitable name format
        // based on the name of the train that is currently set.
        return TrainNameFormat.guess(properties.getTrainName());
    }

    @Override
    public void set(TrainProperties properties, TrainNameFormat value) {
        ITrainProperty.super.set(properties, value);

        // Besides storing in the configuration, also rename the train
        // Only actually rename if the format used is different
        if (!value.matches(properties.getTrainName())) {
            properties.setTrainName(value.search(TrainPropertiesStore::isUseableName));
        }
    }
}
