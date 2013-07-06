package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

public class GlobalCommands {

	public static boolean execute(CommandSender sender, String[] args) throws NoPermissionException {
		if (args[0].equals("msg") || args[0].equals("message")) {
			Permission.COMMAND_MESSAGE.handle(sender);
			if (args.length == 1) {
				sender.sendMessage(ChatColor.YELLOW + "/train message [name] [text...]");
			} else if (args.length == 2) {
				String value = TrainCarts.messageShortcuts.get(args[1]);
				if (value == null) {
					sender.sendMessage(ChatColor.RED + "No shortcut is set for key '" + args[1] + "'");
				} else {
					sender.sendMessage(ChatColor.GREEN + "Shortcut value of '" + args[1] + "' = " + ChatColor.WHITE + value);
				}
			} else {
				StringBuilder valueBuilder = new StringBuilder(100);
				for (int i = 2; i < args.length; i++) {
					if (i != 2) {
						valueBuilder.append(' ');
					}
					valueBuilder.append(args[i]);
				}
				String value = StringUtil.ampToColor(valueBuilder.toString());
				TrainCarts.messageShortcuts.remove(args[1]);
				TrainCarts.messageShortcuts.add(args[1], value);
				TrainCarts.plugin.saveShortcuts();
				sender.sendMessage(ChatColor.GREEN + "Shortcut '" + args[1] + "' set to: " + ChatColor.WHITE + value);
			}
			return true;
		} else if (args[0].equals("removeall") || args[0].equals("destroyall")) {
			Permission.COMMAND_DESTROYALL.handle(sender);
			if (args.length == 1) {
				// Destroy all trains on the entire server
				int count = OfflineGroupManager.destroyAll();
				sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
			} else {
				// Destroy the trains on a single world
				String cname = args[1].toLowerCase();
				World w = Bukkit.getWorld(cname);
				if (w == null) {
					for (World world : Bukkit.getServer().getWorlds()) {
						if (world.getName().toLowerCase().contains(cname)) {
							w = world;
							break;
						}
					}
				}
				if (w != null) {
					int count = OfflineGroupManager.destroyAll(w);
					sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
				} else {
					sender.sendMessage(ChatColor.RED + "World not found!");
				}
			}
			return true;
		} else if (args[0].equals("reroute")) {
			Permission.COMMAND_REROUTE.handle(sender);
			if (args.length >= 2 && args[1].equalsIgnoreCase("lazy")) {
				PathNode.clearAll();
				sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated when needed");
			} else {
				PathNode.reroute();
				sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated");
			}
			return true;
		} else if (args[0].equals("reload")) {
			Permission.COMMAND_RELOAD.handle(sender);
			TrainProperties.loadDefaults();
			TrainCarts.plugin.loadConfig();
			sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded.");
			return true;
		} else if (args[0].equals("saveall")) {
			Permission.COMMAND_SAVEALL.handle(sender);
			TrainCarts.plugin.save();
			sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
			return true;
		} else if (args[0].equals("fixbugged")) {
			Permission.COMMAND_FIXBUGGED.handle(sender);
			for (World world : WorldUtil.getWorlds()) {
				OfflineGroupManager.removeBuggedMinecarts(world);
			}
			sender.sendMessage(ChatColor.YELLOW + "Bugged minecarts have been forcibly removed.");
			return true;
		} else if (args[0].equals("list")) {
			int count = 0, moving = 0;
			for (MinecartGroup group : MinecartGroupStore.getGroups()) {
				count++;
				if (group.isMoving()) {
					moving++;
				}
				// Get properties: ensures that ALL trains are listed
				group.getProperties();
			}
			count += OfflineGroupManager.getStoredCount();
			int minecartCount = 0;
			for (World world : WorldUtil.getWorlds()) {
				for (org.bukkit.entity.Entity e : WorldUtil.getEntities(world)) {
					if (e instanceof Minecart) {
						minecartCount++;
					}
				}
			}
			MessageBuilder builder = new MessageBuilder();
			builder.green("There are ").yellow(count).green(" trains on this server (of which ");
			builder.yellow(moving).green(" are moving)");
			builder.newLine().green("There are ").yellow(minecartCount).green(" minecart entities");
			builder.send(sender);
			// Show additional information about owned trains to players
			if (sender instanceof Player) {
				StringBuilder statement = new StringBuilder();
				for (int i = 1; i < args.length; i++) {
					if (i > 1) {
						statement.append(' ');
					}
					statement.append(args[i]);
				}
				list((Player) sender, statement.toString());
			}
			return true;
		} else if (args[0].equals("edit")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Can not edit a train through the console!");
				return true;
			}
			if (args.length == 2) {
				String name = args[1];
				TrainProperties prop = TrainProperties.exists(name) ? TrainProperties.get(name) : null;
				if (prop != null && !prop.isEmpty()) {
					if (prop.hasOwnership((Player) sender)) {
						CartPropertiesStore.setEditing((Player) sender, prop.get(0));
						sender.sendMessage(ChatColor.GREEN + "You are now editing train '" + prop.getTrainName() + "'!");	
					} else {
						sender.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
					}
					return true;
				} else {
					sender.sendMessage(ChatColor.RED + "Could not find a valid train named '" + name + "'!");	
				}
			} else {
				sender.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");	
			}
			list((Player) sender, "");
			return true;
		}
		return false;
	}

	public static void list(Player player, String statement) {
		MessageBuilder builder = new MessageBuilder();
		builder.yellow("You are the proud owner of the following trains:");
		builder.newLine().setSeparator(ChatColor.WHITE, " / ");
		boolean found = false;
		for (TrainProperties prop : TrainProperties.getAll()) {
			if (!prop.hasOwnership(player)) {
				continue;
			}
			if (prop.hasHolder() && statement.length() > 0) {
				MinecartGroup group = prop.getHolder();
				SignActionEvent event = new SignActionEvent(null, group);
				if (!Statement.has(group, statement, event)) {
					continue;
				}
			}
			found = true;
			if (prop.isLoaded()) {
				builder.green(prop.getTrainName());
			} else {
				builder.red(prop.getTrainName());
			}
		}
		if (found) {
			builder.send(player);
		} else {
			Localization.EDIT_NONEFOUND.message(player);
		}
	}
}
