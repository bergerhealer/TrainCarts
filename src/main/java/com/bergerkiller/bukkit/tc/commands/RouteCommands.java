package com.bergerkiller.bukkit.tc.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Commands to alter the route of a train, load a route from routes.yml, or save one to it
 */
public class RouteCommands {
    private static final String ROUTE_SEP = " \u2192 "; // " > "

    public static void execute(CommandSender sender, IProperties properties, String[] args) throws NoPermissionException {
        String cmd_prefix = ChatColor.YELLOW + ((properties instanceof TrainProperties) ? "/train route " : "/cart route ");
        if (args.length == 0) {
            // Show current route set
            // Shows the current destination in green and others in yellow
            List<String> route = properties.getDestinationRoute();
            if (route.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No route is currently set!");
                sender.sendMessage(ChatColor.RED + "For help, use " + cmd_prefix + "help");
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
        } else if (args[0].equalsIgnoreCase("add") && args.length > 1) {
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Added the destinations to the end of the route:");
            builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
            for (int i = 1; i < args.length; i++) {
                builder.green(args[i]);
                properties.addDestinationToRoute(args[i]);
            }
            builder.send(sender);
        } else if (args[0].equalsIgnoreCase("set") && args.length > 1) {
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Discarded the previous route and set the destinations:");
            builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
            properties.clearDestinationRoute();
            for (int i = 1; i < args.length; i++) {
                builder.green(args[i]);
                properties.addDestinationToRoute(args[i]);
            }
            builder.send(sender);
        } else if (args[0].equalsIgnoreCase("remove") && args.length > 1) {
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Removed the destinations from the route:");
            builder.newLine().setSeparator(ChatColor.WHITE, " ");
            for (int i = 1; i < args.length; i++) {
                builder.green(args[i]);
                properties.removeDestinationFromRoute(args[i]);
            }
            builder.send(sender);
        } else if (args[0].equalsIgnoreCase("clear")) {
            properties.clearDestination();
            sender.sendMessage(ChatColor.YELLOW + "Route cleared!");
        } else if (args[0].equalsIgnoreCase("save") && args.length > 1) {
            TrainCarts.plugin.getRouteManager().storeRoute(args[1], properties.getDestinationRoute());
            sender.sendMessage(ChatColor.YELLOW + "Route saved as '" + ChatColor.WHITE + args[1] + ChatColor.YELLOW + "'!");
        } else if (args[0].equalsIgnoreCase("load") && args.length > 1) {
            List<String> newRoute = TrainCarts.plugin.getRouteManager().findRoute(args[1]);
            properties.setDestinationRoute(newRoute);
            if (newRoute.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Route '"  + args[1] + "' is empty or does not exist!");
            } else {
                MessageBuilder builder = new MessageBuilder();
                builder.yellow("Loaded route '").white(args[1]).yellow("':");
                builder.newLine().setSeparator(ChatColor.WHITE, ROUTE_SEP);
                for (String destination : newRoute) {
                    builder.green(destination);
                }
                builder.send(sender);
            }
        } else {
            // Invalid syntax / help command
            if (!args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.RED + "Invalid syntax!");
            }
            sender.sendMessage(cmd_prefix + "- shows current route");
            sender.sendMessage(cmd_prefix + "add/set [destination...] - adds or sets the destinations of the route");
            sender.sendMessage(cmd_prefix + "remove [destination...] - remove destinations from the route");
            sender.sendMessage(cmd_prefix + "clear - remove all destinations from the route, disabling it");
            sender.sendMessage(cmd_prefix + "save [name] - save routes to a global route directory by name");
            sender.sendMessage(cmd_prefix + "load [name] - load routes from a global route directory by name");
        }
    }
}
