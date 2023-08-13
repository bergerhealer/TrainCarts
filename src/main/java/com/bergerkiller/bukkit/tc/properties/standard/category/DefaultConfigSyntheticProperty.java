package com.bergerkiller.bukkit.tc.properties.standard.category;

import com.bergerkiller.bukkit.tc.properties.defaults.DefaultProperties;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.ISyntheticProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Makes it possible to apply defaults from configuration to trains
 */
public final class DefaultConfigSyntheticProperty implements ISyntheticProperty<DefaultProperties> {

    @CommandTargetTrain
    @PropertyCheckPermission("setdefault")
    @CommandMethod("train defaults apply <defaultname>")
    @CommandDescription("Applies defaults from DefaultTrainProperties to a train")
    private void commandApplyDefaults(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("defaultname") String defaultName
    ) {
        DefaultProperties defaults = TrainPropertiesStore.getDefaultsByName(defaultName);
        if (defaults == null) {
            sender.sendMessage(ChatColor.RED + "Train Property Defaults by key "
                    + ChatColor.BLUE + "'" + defaultName + "' "
                    + ChatColor.RED + " does not exist!");
            return;
        }

        properties.apply(defaults);
        sender.sendMessage(ChatColor.GREEN + "Default properties '" + defaultName + "' applied!");
    }

    @PropertyParser("applydefault|setdefault|default")
    public DefaultProperties parseDefaultConfig(String defaultName) {
        DefaultProperties defaults = TrainPropertiesStore.getDefaultsByName(defaultName);
        if (defaults == null) {
            throw new PropertyInvalidInputException("Train Property Defaults by key '" + defaultName + "' does not exist");
        }
        return defaults;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_APPLYDEFAULTS.has(sender);
    }

    @Override
    public DefaultProperties getDefault() {
        return TrainPropertiesStore.getDefaultsByName("default");
    }

    @Override
    public DefaultProperties get(CartProperties properties) {
        return getDefault();
    }

    @Override
    public DefaultProperties get(TrainProperties properties) {
        return getDefault();
    }

    @Override
    public void set(CartProperties properties, DefaultProperties config) {
        // Go by all properties and apply them to the cart
        // Do note: if properties are for trains, they are applied too!
        if (config != null) {
            config.applyTo(properties);
        }
    }

    @Override
    public void set(TrainProperties properties, DefaultProperties config) {
        if (config != null) {
            config.applyTo(properties);
        }
    }
}
