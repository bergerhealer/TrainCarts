package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.storage.WorldGroupManager;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;

public class GlobalCommands {
	
	public static boolean execute(CommandSender sender, String[] args) throws NoPermissionException {
		if (args[0].equals("removeall") || args[0].equals("destroyall")) {
			Permission.COMMAND_DESTROYALL.handle(sender);
			if (args.length == 2) {
				String cname = args[1].toLowerCase();
				World w = null;
				for (World world : Bukkit.getServer().getWorlds()) {
					String wname = world.getName().toLowerCase();
					if (wname.equals(cname)) {
						w = world;
						break;
					}
				}
				if (w == null) {
					for (World world : Bukkit.getServer().getWorlds()) {
						String wname = world.getName().toLowerCase();
						if (wname.contains(cname)) {
							w = world;
							break;
						}
					}
				}
				if (w != null) {
					int count = WorldGroupManager.destroyAll(w);
					sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
				} else {
					sender.sendMessage(ChatColor.RED + "World not found!");
				}
			} else {
				int count = WorldGroupManager.destroyAll();
				sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
			}
			return true;
		} else if (args[0].equals("reroute")) {
			Permission.COMMAND_REROUTE.handle(sender);
			PathNode.clearAll();
			sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated.");
			return true;
		} else if (args[0].equals("reload")) {
			Permission.COMMAND_RELOAD.handle(sender);
			TrainProperties.reloadDefaults();
			TrainCarts.plugin.loadConfig();
			sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded.");
			return true;
		}
		return false;
	}

}
