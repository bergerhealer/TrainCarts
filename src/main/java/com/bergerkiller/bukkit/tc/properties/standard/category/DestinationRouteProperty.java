package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;

/**
 * Stores a list of destinations a cart traversed. When it reaches the next
 * destination in this list, it automatically advances to the next one.
 * This property and {@link StandardProperties#DESTINATION_ROUTE_INDEX}
 * are used together.
 */
public final class DestinationRouteProperty implements ICartProperty<List<String>> {
    private static final String ROUTE_SEP = " \u2192 "; // " > "

    public void showDestinationPathInfo(CommandSender sender, IProperties prop) {
        MessageBuilder msg = new MessageBuilder();
        msg.yellow("This ").append(prop.getTypeName());
        final String lastName = prop.getDestination();
        IPropertiesHolder holder;
        if (LogicUtil.nullOrEmpty(lastName)) {
            msg.append(" is not trying to reach a destination.");
        } else if ((holder = prop.getHolder()) == null) {
            msg.append(" is not currently loaded.");
        } else {
            msg.append(" is trying to reach ").green(lastName).newLine();

            PathWorld pathWorld = TrainCarts.plugin.getPathProvider().getWorld(holder.getWorld());
            final PathNode first = pathWorld.getNodeByName(prop.getLastPathNode());
            if (first == null) {
                msg.yellow("It has not yet visited a routing node, so no route is available yet.");
            } else {
                PathNode last = pathWorld.getNodeByName(lastName);
                if (last == null) {
                    msg.red("The destination position to reach can not be found!");
                } else {
                    // Calculate the exact route taken from first to last
                    PathConnection[] route = first.findRoute(last);
                    msg.yellow("Route: ");
                    if (route.length == 0) {
                        msg.red(first.getDisplayName() + " /=/ " + last.getDisplayName() + " (not found)");
                    } else {
                        msg.setSeparator(ChatColor.YELLOW, " -> ");
                        for (PathConnection connection : route) {
                            msg.green(connection.destination.getDisplayName());
                        }
                    }
                }
            }
        }
        msg.send(sender);
    }

    public void showRoute(String cmd_prefix, CommandSender sender, IProperties properties) {
        // Show current route set
        // Shows the current destination in green and others in yellow
        List<String> route = properties.getDestinationRoute();
        if (route.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No route is currently set!");
            sender.sendMessage(ChatColor.RED + "For help, use " + cmd_prefix + " help");
        } else {
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("The following route is currently set:");
            builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
            int currentRouteIndex = properties.getCurrentRouteDestinationIndex();
            for (int i = 0; i < route.size(); i++) {
                if (i == currentRouteIndex) {
                    builder.green(route.get(i));
                } else {
                    builder.yellow(route.get(i));
                }
            }
            builder.send(sender);
        }

        // Show the current route taken to the next destination
        if (Permission.COMMAND_PATHINFO.has(sender)) {
            showDestinationPathInfo(sender, properties);
        }
    }

    @CommandMethod("cart route")
    @CommandDescription("Displays the current route set for a cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        showRoute("/cart route", sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("cart route add <destinations>")
    @CommandDescription("Adds one or more destinations to the route set for a cart")
    private void setPropertyAdd(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertyAddGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("cart route set <destinations>")
    @CommandDescription("Resets the route to one or more destinations for a cart")
    private void setPropertySet(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertySetGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("cart route remove <destinations>")
    @CommandDescription("Removes one or more destinations from the route of a cart")
    private void setPropertyRemove(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertyRemoveGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("cart route clear")
    @CommandDescription("Clears the route set for a cart")
    private void setPropertyClear(
            final CommandSender sender,
            final CartProperties properties
    ) {
        properties.clearDestinationRoute();
        sender.sendMessage(ChatColor.YELLOW + "Route cleared!");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_SAVE_ROUTE)
    @CommandMethod("cart route save <route_name>")
    @CommandDescription("Saves the current route of the cart with a name, which can then be loaded by that name")
    private void getPropertySaveRoute(
            final TrainCarts plugin,
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("route_name") @Greedy String routeName
    ) {
        plugin.getRouteManager().storeRoute(routeName, properties.getDestinationRoute());
        sender.sendMessage(ChatColor.YELLOW + "Route saved as '" + ChatColor.WHITE + routeName + ChatColor.YELLOW + "'!");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("cart route load <route_name>")
    @CommandDescription("Resets the route and loads a new route by name for a cart")
    private void setPropertyLoadRoute(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="route_name", suggestions="savedRouteNames") @Greedy String routeName
    ) {
        setPropertyLoadRouteGeneric(sender, properties, routeName);
    }

    @CommandMethod("train route")
    @CommandDescription("Displays the current route set for a train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showRoute("/train route", sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("train route add <destinations>")
    @CommandDescription("Adds one or more destinations to the route set for a train")
    private void setPropertyAdd(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertyAddGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("train route set <destinations>")
    @CommandDescription("Resets the route to one or more destinations for a train")
    private void setPropertySet(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertySetGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("train route remove <destinations>")
    @CommandDescription("Removes one or more destinations from the route of a train")
    private void setPropertyRemove(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="destinations", suggestions="destinations") String[] destinations
    ) {
        setPropertyRemoveGeneric(sender, properties, destinations);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("train route clear")
    @CommandDescription("Clears the route set for a train")
    private void setPropertyClear(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.clearDestinationRoute();
        sender.sendMessage(ChatColor.YELLOW + "Route cleared!");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_SAVE_ROUTE)
    @CommandMethod("train route save <route_name>")
    @CommandDescription("Saves the current route of the train with a name, which can then be loaded by that name")
    private void getPropertySaveRoute(
            final TrainCarts plugin,
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("route_name") @Greedy String routeName
    ) {
        plugin.getRouteManager().storeRoute(routeName, properties.getDestinationRoute());
        sender.sendMessage(ChatColor.YELLOW + "Route saved as '" + ChatColor.WHITE + routeName + ChatColor.YELLOW + "'!");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("route")
    @CommandMethod("train route load <route_name>")
    @CommandDescription("Resets the route and loads a new route by name for a train")
    private void setPropertyLoadRoute(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="route_name", suggestions="savedRouteNames") @Greedy String routeName
    ) {
        setPropertyLoadRouteGeneric(sender, properties, routeName);
    }

    @Suggestions("savedRouteNames")
    public List<String> getSavedRouteNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return plugin.getRouteManager().getRouteNames();
    }

    private void setPropertySetGeneric(CommandSender sender, IProperties properties, String[] destinations) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("Discarded the previous route and set the destinations:");
        builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
        properties.clearDestinationRoute();
        for (String destination : destinations) {
            builder.green(destination);
            properties.addDestinationToRoute(destination);
        }
        builder.send(sender);
    }

    private void setPropertyAddGeneric(CommandSender sender, IProperties properties, String[] destinations) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("Added the destinations to the end of the route:").newLine();
        builder.setSeparator(ChatColor.WHITE, ROUTE_SEP);
        builder.green("");
        for (String destination : destinations) {
            builder.green(destination);
            properties.addDestinationToRoute(destination);
        }
        builder.clearSeparator().newLine();

        afterSetBuildCurrentRoute(builder, properties);

        builder.send(sender);
    }

    private void setPropertyRemoveGeneric(CommandSender sender, IProperties properties, String[] destinations) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("Removed the destinations from the route:").newLine();
        builder.setSeparator(ChatColor.WHITE, " ");
        for (String destination : destinations) {
            builder.green(destination);
            properties.removeDestinationFromRoute(destination);
        }
        builder.clearSeparator().newLine();

        afterSetBuildCurrentRoute(builder, properties);

        builder.send(sender);
    }

    private void setPropertyLoadRouteGeneric(CommandSender sender, IProperties properties, String routeName) {
        List<String> newRoute = TrainCarts.plugin.getRouteManager().findRoute(routeName);
        properties.setDestinationRoute(newRoute);
        if (newRoute.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Route '"  + routeName + "' is empty or does not exist!");
        } else {
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Loaded route '").white(routeName).yellow("':");
            builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
            for (String destination : newRoute) {
                builder.green(destination);
            }
            builder.send(sender);
        }
    }

    private void afterSetBuildCurrentRoute(MessageBuilder builder, IProperties properties) {
        builder.newLine().yellow("New route: ");
        builder.setSeparator(ChatColor.WHITE, ROUTE_SEP);
        for (String destination : properties.getDestinationRoute()) {
            builder.green(destination);
        }
    }

    @PropertyParser("clearroute|route clear")
    public List<String> parseClear(String input) {
        return Collections.emptyList();
    }

    @PropertyParser("setroute|route set")
    public List<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptyList() : Collections.singletonList(input);
    }

    @PropertyParser("loadroute|route load")
    public List<String> parseLoad(String input) {
        return TrainCarts.plugin.getRouteManager().findRoute(input);
    }

    @PropertyParser(value="addroute|route add", processPerCart = true)
    public List<String> parseAdd(PropertyParseContext<List<String>> context) {
        if (context.input().isEmpty()) {
            return context.current();
        } else if (context.current().isEmpty()) {
            return Collections.singletonList(context.input());
        } else {
            ArrayList<String> newRoute = new ArrayList<String>(context.current());
            newRoute.add(context.input());
            return Collections.unmodifiableList(newRoute);
        }
    }

    @PropertyParser(value="remroute|removeroute|route rem|route remove", processPerCart = true)
    public List<String> parseRemove(PropertyParseContext<List<String>> context) {
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        } else {
            ArrayList<String> newRoute = new ArrayList<String>(context.current());
            while (newRoute.remove(context.input())); // remove all instances
            return Collections.unmodifiableList(newRoute);
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_ROUTE.has(sender);
    }

    @Override
    public List<String> getDefault() {
        return Collections.emptyList();
    }

    @Override
    public Optional<List<String>> readFromConfig(ConfigurationNode config) {
        if (config.contains("route")) {
            return Optional.of(Collections.unmodifiableList(new ArrayList<String>(
                    config.getList("route", String.class)
            )));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<List<String>> value) {
        if (value.isPresent()) {
            config.set("route", value.get());
        } else {
            config.remove("route");
        }
    }

    @Override
    public void set(CartProperties properties, List<String> value) {
        // Update route itself
        ICartProperty.super.set(properties, value);

        // Keep routing towards the same destination
        // This allows for a seamless transition between routes
        if (!value.isEmpty() && properties.hasDestination()) {
            int new_index = value.indexOf(properties.getDestination());
            if (new_index == -1) {
                new_index = 0;
            }
            properties.set(StandardProperties.DESTINATION_ROUTE_INDEX, new_index);
        } else {
            properties.set(StandardProperties.DESTINATION_ROUTE_INDEX, 0);
        }
    }

    @Override
    public List<String> get(TrainProperties properties) {
        for (CartProperties cprop : properties) {
            List<String> route = get(cprop);
            if (!route.isEmpty()) {
                return route;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Stores the current index of the route a train is moving towards.
     * Used together with {@link DestinationRouteProperty}.
     */
    public static final class IndexProperty implements ICartProperty<Integer> {
        private final Integer DEFAULT = 0;

        @Override
        public Integer getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Integer> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "routeIndex", int.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
            Util.setConfigOptional(config, "routeIndex", value);
        }
    }
}
