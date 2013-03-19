package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
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
			sender.sendMessage("TrainCarts " + TrainCarts.plugin.getVersion() + " - See WIKI page for more information");
			return true;
		}
		try {
			if (GlobalCommands.execute(sender, args)) return true;
			//editing?
			if (!(sender instanceof Player)) return false;
			Player player = (Player) sender;
			CartProperties cprop = CartProperties.getEditing(player);
			if (cprop == null) {
				if (CartProperties.canHaveOwnership(player)) {
					player.sendMessage(ChatColor.YELLOW + "You haven't selected a train to edit yet!");
				} else {
					player.sendMessage(ChatColor.RED + "You are not allowed to own trains!");
				}
				return true;
			}
			String cmd = args[0];
			args = StringUtil.remove(args, 0);
			if (command.equalsIgnoreCase("train")) {
				TrainProperties prop = cprop.getTrainProperties();
				if (prop.hasOwnership(player)) {
					return TrainCommands.execute(player, prop, cmd, args);
				} else {
					player.sendMessage(ChatColor.RED + "You don't own this train!");
					return true;
				}
			} else if (command.equalsIgnoreCase("cart")) {
				CartProperties prop = cprop;
				if (prop.hasOwnership(player)) {
					return CartCommands.execute(player, prop, cmd, args);
				} else {
					player.sendMessage(ChatColor.RED + "You don't own this minecart!");
					return true;
				}
			} else {
				return false;
			}
		} catch (NoPermissionException ex) {
			sender.sendMessage(ChatColor.RED + "You do not have permission, ask an admin to do this for you.");
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
						msg.red(first.name + " /=/ " + last.name + " (not found)");
					} else {
						msg.setSeparator(ChatColor.YELLOW, " -> ");
						for (PathNode node : route) {
							if (node.isNamed()) {
								msg.green(node.name);
							} else {
								BlockLocation l = node.location;
								msg.green("[" + l.x + "/" + l.y + "/" + l.z + "]");
							}
						}
					}
				}				
			}
		}
		msg.send(p);
	}

	public static void info(Player p, IProperties prop) {
		p.sendMessage(ChatColor.YELLOW + "Tags: " + ChatColor.WHITE + (prop.hasTags() ? StringUtil.combineNames(prop.getTags()) : "None"));
		if (prop.hasDestination()) {
			p.sendMessage(ChatColor.YELLOW + "This minecart will attempt to reach: " + ChatColor.WHITE + prop.getDestination());
		}
		if (prop.getPlayersEnter()) {
			p.sendMessage(ChatColor.YELLOW + "Players entering trains: " + ChatColor.WHITE + "Allowed");
		} else {
			p.sendMessage(ChatColor.YELLOW + "Players entering trains: " + ChatColor.WHITE + "Denied");
		}
		p.sendMessage(ChatColor.YELLOW + "Can be exited by players: " + ChatColor.WHITE + prop.getPlayersExit());
		BlockLocation loc = prop.getLocation();
		if (loc != null) {
			p.sendMessage(ChatColor.YELLOW + "Current location: " + ChatColor.WHITE + "[" + loc.x + "/" + loc.y + "/" + loc.z + "] in world " + loc.world);
		}
	}
}
