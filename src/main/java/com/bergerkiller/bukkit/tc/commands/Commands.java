package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands {

    public static void permission(CommandSender sender, String node) throws NoPermissionException {
        if (sender instanceof Player && !sender.hasPermission(node)) {
            throw new NoPermissionException();
        }
    }

    public static boolean execute(CommandSender sender, String command, String[] args) {
        try {
            // Saved train properties
            if (command.equalsIgnoreCase("savedtrain")) {
                if (args.length == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain [trainname] [command] to modify saved trains");
                    sender.sendMessage("");
                    SavedTrainCommands.execute(sender, "list", new String[0]);
                    return true;
                }

                String savedTrainName = args[0];
                String[] st_args = StringUtil.remove(args, 0);
                SavedTrainCommands.execute(sender, savedTrainName, st_args);
                return true;
            }

            // Show version information when /train or /cart is used
            if (args.length == 0) {
                Localization.COMMAND_ABOUT.message(sender, TrainCarts.plugin.getDebugVersion());
                return true;
            }

            // Global commands that do not mutate properties or tickets
            if (GlobalCommands.execute(sender, args)) {
                return true;
            }

            // Commands for creating and updating ticket types, and giving tickets to players
            // Applying ticket whitelists is done for trains themselves
            if (args.length >= 2 && args[0].equals("ticket")) {
                String t_cmd = args[1];
                String[] t_args = StringUtil.remove(StringUtil.remove(args, 0), 0);
                if (TicketCommands.execute(sender, t_cmd, t_args)) {
                    return true;
                }
            }

            if (!(sender instanceof Player)) {
                return false;
            }

            Permission.COMMAND_PROPERTIES.handle(sender);
            //editing?
            Player player = (Player) sender;
            CartProperties cprop = CartProperties.getEditing(player);
            if (cprop == null) {
                Localization.EDIT_NOSELECT.message(player);
                return true;
            }

            // Only cart/train works here. Get appropriate properties
            IProperties properties;
            if (command.equalsIgnoreCase("train")) {
                properties = cprop.getTrainProperties();
            } else if (command.equalsIgnoreCase("cart")) {
                properties = cprop;
            } else {
                return false;
            }

            // Check ownership
            if (!properties.hasOwnership(player)) {
                Localization.EDIT_NOTOWNED.message(player);
                return true;
            }

            // Execute the /train route and /cart route set of commands
            if (args[0].equalsIgnoreCase("route")) {
                RouteCommands.execute(sender, properties, StringUtil.remove(args, 0));
                return true;
            }

            // Execute commands for the appropriate properties
            String cmd = args[0];
            args = StringUtil.remove(args, 0);
            if (properties instanceof TrainProperties) {
                return TrainCommands.execute(player, (TrainProperties) properties, cmd, args);
            } else if (properties instanceof CartProperties) {
                return CartCommands.execute(player, (CartProperties) properties, cmd, args);
            } else {
                return false;
            }
        } catch (NoPermissionException ex) {
            Localization.COMMAND_NOPERM.message(sender);
            return true;
        }
    }

    public static void showPathInfo(Player p, IProperties prop) {
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
        msg.send(p);
    }

    public static void info(MessageBuilder message, IProperties prop) {
        // Ownership information
        message.newLine();
        if (!prop.hasOwners() && !prop.hasOwnerPermissions()) {
            message.yellow("Owned by: ").white("Everyone");
        } else {
            if (prop.hasOwners()) {
                message.yellow("Owned by: ").white(StringUtil.combineNames(prop.getOwners()));
            }
            if (prop.hasOwnerPermissions()) {
                message.yellow("Owned by players with the permissions: ");
                message.setSeparator(ChatColor.YELLOW, " / ").setIndent(4);
                for (String ownerPerm : prop.getOwnerPermissions()) {
                    message.white(ownerPerm);
                }
                message.clearSeparator().setIndent(0);
            }
        }

        // Tags and other information
        message.newLine().yellow("Tags: ").white((prop.hasTags() ? StringUtil.combineNames(prop.getTags()) : "None"));
        if (prop.hasDestination()) {
            message.newLine().yellow("This minecart will attempt to reach: ").white(prop.getDestination());
        }
        message.newLine().yellow("Players entering trains: ").white(prop.getPlayersEnter() ? "Allowed" : "Denied");
        message.newLine().yellow("Can be exited by players: ").white(prop.getPlayersExit());
        BlockLocation loc = prop.getLocation();
        if (loc != null) {
            message.newLine().yellow("Current location: ").white("[", loc.x, "/", loc.y, "/", loc.z, "] in world ", loc.world);
        }
    }
}
