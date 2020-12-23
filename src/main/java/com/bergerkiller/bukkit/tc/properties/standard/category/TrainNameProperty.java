package com.bergerkiller.bukkit.tc.properties.standard.category;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.ISyntheticProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;

/**
 * Accesses {@link TrainProperties#setTrainName(String)} as if it were a property.
 * Adds a parser which allows the train name to be changed using property signs.
 */
public final class TrainNameProperty implements ISyntheticProperty<String> {

    @CommandTargetTrain
    @PropertyCheckPermission("name")
    @CommandMethod("train rename|setname|name <new_name>")
    @CommandDescription("Renames a train")
    private void trainSetSpeedLimit(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("new_name") String newName,
            final @Flag(value="generate", description="Generate a unique name") boolean generate
    ) {
        if (generate) {
            properties.setTrainName(TrainPropertiesStore.generateTrainName(newName));
            sender.sendMessage(ChatColor.YELLOW + "A name was generated! Train renamed to: "
                    + ChatColor.WHITE + properties.getTrainName());
        } else if (TrainPropertiesStore.exists(newName) && !newName.equals(properties.getTrainName())) {
            sender.sendMessage(ChatColor.RED + "This name is already taken!");
        } else {
            properties.setTrainName(newName);
            sender.sendMessage(ChatColor.YELLOW + "The train is now called: "
                    + ChatColor.WHITE + newName);
        }
    }

    @CommandMethod("train rename|setname|name")
    @CommandDescription("Displays the current name of the train being edited")
    private void trainGetSpeedLimit(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.isTrainRenamed()) {
            sender.sendMessage(ChatColor.YELLOW + "Train name: "
                    + ChatColor.WHITE + properties.getTrainName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train name: "
                    + ChatColor.WHITE + properties.getTrainName()
                    + ChatColor.RED + " (Default)");
        }
    }

    @PropertyParser("name|rename|setname|settrainname")
    public String parseRename(String nameFormat) {
        return TrainPropertiesStore.generateTrainName(nameFormat);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_NAME.has(sender);
    }

    @Override
    public String getDefault() {
        return "train";
    }

    @Override
    public String get(CartProperties properties) {
        return get(properties.getTrainProperties());
    }

    @Override
    public void set(CartProperties properties, String value) {
        set(properties.getTrainProperties(), value);
    }

    @Override
    public String get(TrainProperties properties) {
        return properties.getTrainName();
    }

    @Override
    public void set(TrainProperties properties, String value) {
        properties.setTrainName(value);
    }
}
