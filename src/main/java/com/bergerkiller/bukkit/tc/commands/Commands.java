package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;

public class Commands {

	public static void permission(CommandSender sender, String node) throws NoPermissionException {
		if (sender instanceof Player && !((Player) sender).hasPermission(node)) {
			throw new NoPermissionException();
		}
	}

	public static boolean execute(CommandSender sender, String command, String[] args) {
		if (args.length == 0) {
			Localization.COMMAND_ABOUT.message(sender, TrainCarts.plugin.getVersion());
			return true;
		}
		try {
			if (GlobalCommands.execute(sender, args)) {
				return true;
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
			String cmd = args[0];
			args = StringUtil.remove(args, 0);
			if (command.equalsIgnoreCase("train")) {
				TrainProperties prop = cprop.getTrainProperties();
				if (prop.hasOwnership(player)) {
					return TrainCommands.execute(player, prop, cmd, args);
				} else {
					Localization.EDIT_NOTOWNED.message(player);
					return true;
				}
			} else if (command.equalsIgnoreCase("cart")) {
				CartProperties prop = cprop;
				if (prop.hasOwnership(player)) {
					return CartCommands.execute(player, prop, cmd, args);
				} else {
					Localization.EDIT_NOTOWNED.message(player);
					return true;
				}
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
		if (LogicUtil.nullOrEmpty(lastName)) {
			msg.append(" is not trying to reach a destination.");
		} else {
			msg.append(" is trying to reach ").green(lastName).newLine();
			final PathNode first = PathNode.get(prop.getLastPathNode());
			if (first == null) {
				msg.yellow("It has not yet visited a routing node, so no route is available yet.");
			} else {
				PathNode last = PathNode.get(lastName);
				if (last == null) {
					msg.red("The destination position to reach can not be found!");
				} else {
					// Calculate the exact route taken from first to last
					PathNode[] route = first.findRoute(last);
					msg.yellow("Route: ");
					if (route.length == 0) {
						msg.red(first.getDisplayName() + " /=/ " + last.getDisplayName() + " (not found)");
					} else {
						msg.setSeparator(ChatColor.YELLOW, " -> ");
						for (PathNode node : route) {
							msg.green(node.getDisplayName());
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
