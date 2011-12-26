package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.GroupManager;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;

public class TrainCommands {

	public static boolean execute(Player p, TrainProperties prop, String cmd, String[] args) {
		if (cmd.equals("linking") || cmd.equals("link")) {
			if (args.length == 1) {
				prop.allowLinking = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + " " + prop.allowLinking);
		} else if (cmd.equals("keepchunksloaded")) {
			if (args.length == 1) {
				if (p.hasPermission("train.command.keepchunksloaded")) {
					prop.keepChunksLoaded = Util.getBool(args[0]);
				} else {
					p.sendMessage(ChatColor.RED + "You are not allowed to set this property for trains!");
				}
			}
			p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + " " + prop.keepChunksLoaded);
		} else if (cmd.equals("claim")) {
			//claim as many carts as possible
			int claimed = 0;
			for (CartProperties cprop : prop.getCarts()) {
				if (!CartProperties.hasGlobalOwnership(p)) {
					if (cprop.hasOwners()) {
						if (!cprop.isOwner(p)) continue;
					}
				}
				//claim
				cprop.clearOwners();
				cprop.setOwner(p);
				claimed++;
			}
			if (claimed == prop.getCarts().size()) {
				p.sendMessage(ChatColor.YELLOW + "You claimed this entire train your own!");
			} else if (claimed == 1) {
				p.sendMessage(ChatColor.YELLOW + "You claimed one train cart your own!");
			} else if (claimed > 1) {
				p.sendMessage(ChatColor.YELLOW + "You claimed " + claimed + " train carts your own!");
			} else {
				p.sendMessage(ChatColor.RED + "You failed to claim any carts your own!");
			}
		} else if (cmd.equals("pushmobs") || cmd.equals("pushplayers") || cmd.equals("pushmisc")) {
			String secarg = null;
			if (args.length == 1) {
				secarg = args[0];
			}
			String msg = ChatColor.YELLOW + "Pushes away ";
			if (cmd.equals("pushmobs")) {
				if (secarg != null)  prop.pushMobs = Util.getBool(secarg);
				msg += "mobs: " + ChatColor.WHITE + " " + prop.pushMobs;
			}
			if (cmd.equals("pushplayers")) {
				if (secarg != null) prop.pushPlayers = Util.getBool(secarg);
				msg += "players: " + ChatColor.WHITE + " " + prop.pushPlayers;
			}
			if (cmd.equals("pushmisc")) {
				if (secarg != null) prop.pushMisc = Util.getBool(secarg);
				msg += "misc. entities: " + ChatColor.WHITE + " " + prop.pushMisc;
			}
			p.sendMessage(msg);
		} else if (cmd.equals("pushplayers")) {
			if (args.length == 1) {
				prop.pushPlayers = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Pushes away players: " + ChatColor.WHITE + " " + prop.pushPlayers);
		} else if (cmd.equals("pushmisc")) {
			if (args.length == 1) {
				prop.pushMisc = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Pushes away misc. entities: " + ChatColor.WHITE + " " + prop.pushMisc);
			//==================================================================
		} else if (cmd.equals("slowdown") || cmd.equals("slow") || cmd.equals("setslow") || cmd.equals("setslowdown")) {
			if (args.length == 1) {
				prop.slowDown = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Slow down: " + ChatColor.WHITE + prop.slowDown);
		} else if (cmd.equals("setcollide") || cmd.equals("setcollision") || cmd.equals("collision") || cmd.equals("collide")) {
			if (args.length == 1) {
				prop.trainCollision = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Can collide with other trains: " + ChatColor.WHITE + prop.trainCollision);
		} else if (cmd.equals("speedlimit") || cmd.equals("maxspeed")) {
			if (args.length == 1) {
				try {
					prop.speedLimit = Double.parseDouble(args[0]);
				} catch (NumberFormatException ex) {
					prop.speedLimit = 0.4;
				}
			}
			p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.speedLimit + " blocks/tick");
		} else if (cmd.equals("requirepoweredminecart") || cmd.equals("requirepowered")) {
			if (args.length == 1) {
				prop.requirePoweredMinecart = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Requires powered minecart to stay alive: " + ChatColor.WHITE + prop.requirePoweredMinecart);
		} else if (cmd.equals("rename") || cmd.equals("setname") || cmd.equals("name")) {
			if (args.length == 0) {
				p.sendMessage(ChatColor.RED + "You forgot to pass a name along!");
			} else {
				String newname = Util.combine(" ", args);
				if (TrainProperties.exists(newname)) {
					p.sendMessage(ChatColor.RED + "This name is already taken!");
				} else {
					GroupManager.rename(prop.getTrainName(), newname);
					p.sendMessage(ChatColor.YELLOW + "This train is now called " + ChatColor.WHITE + newname + ChatColor.YELLOW + "!");
				}
			}
		}
		return true;
	}

}
