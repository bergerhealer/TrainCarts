package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.List;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * The current destination a train is going for. May also update the
 * {@link StandardProperties#DESTINATION_ROUTE_INDEX} when new destinations
 * are set.
 */
public final class DestinationProperty implements ICartProperty<String> {

    @CommandTargetTrain
    @CommandMethod("train destination|dest none")
    @CommandDescription("Clears the destination set for a train")
    private void commandClearProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        commandSetProperty(sender, properties, "");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("destination")
    @CommandMethod("train destination|dest <destination>")
    @CommandDescription("Sets a new destination for the train to go to")
    private void commandSetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="destination", suggestions="destinations") String destination
    ) {
        properties.setDestination(destination);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("train destination|dest")
    @CommandDescription("Displays the current destination set for the train")
    private void commandGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.hasDestination()) {
            sender.sendMessage(ChatColor.YELLOW + "Train destination is set to: "
                    + ChatColor.WHITE + properties.getDestination());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train destination is set to: "
                    + ChatColor.RED + "None");
        }
    }

    @CommandTargetTrain
    @CommandMethod("cart destination|dest none")
    @CommandDescription("Clears the destination set for a cart")
    private void commandClearProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        commandSetProperty(sender, properties, "");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("destination")
    @CommandMethod("cart destination|dest <destination>")
    @CommandDescription("Sets a new destination for the cart to go to")
    private void commandSetProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="destination", suggestions="destinations") String destination
    ) {
        properties.setDestination(destination);
        commandGetProperty(sender, properties);
    }

    @CommandMethod("cart destination|dest")
    @CommandDescription("Displays the current destination set for the cart")
    private void commandGetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        if (properties.hasDestination()) {
            sender.sendMessage(ChatColor.YELLOW + "Cart destination is set to: "
                    + ChatColor.WHITE + properties.getDestination());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Cart destination is set to: "
                    + ChatColor.RED + "None");
        }
    }

    @PropertyParser("destination")
    public String parseDestination(String input) {
        return input;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_DESTINATION.has(sender);
    }

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "destination", String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, "destination", value);
    }

    @Override
    public void set(CartProperties properties, String value) {
        // Save current index before the destination was changed
        int prior_route_index = properties.getCurrentRouteDestinationIndex();

        // Update destination
        ICartProperty.super.set(properties, value);

        // If a destination is now set, increment the route index if it matches the next one in the list
        if (!value.isEmpty() && prior_route_index != -1) {
            List<String> route = StandardProperties.DESTINATION_ROUTE.get(properties);
            int nextIndex = (prior_route_index + 1) % route.size();
            if (value.equals(route.get(nextIndex))) {
                StandardProperties.DESTINATION_ROUTE_INDEX.set(properties, nextIndex);
            }
        }
    }

    @Override
    public String get(TrainProperties properties) {
        // Return first cart from index=0 that has a destination
        for (CartProperties cprop : properties) {
            String destination = get(cprop);
            if (!destination.isEmpty()) {
                return destination;
            }
        }
        return "";
    }
}
