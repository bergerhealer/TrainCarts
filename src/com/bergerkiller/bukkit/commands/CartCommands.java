package com.bergerkiller.bukkit.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class CartCommands {
	
	public static boolean execute(Player p, CartProperties prop, String cmd, String[] args) {
		if (cmd.equals("info") || cmd.equals("i")) {
			info(p, prop);
		} else if (cmd.equals("mobenter") || cmd.equals("mobsenter")) {
			if (args.length == 1) {
				prop.allowMobsEnter = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Can be entered by mobs: " + ChatColor.WHITE + " " + prop.allowMobsEnter);
		} else if (cmd.equals("playerenter")) {
			if (args.length == 1) {
				prop.allowPlayerEnter = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Players can enter this minecart: " + ChatColor.WHITE + " " + prop.allowPlayerEnter);
		} else if (cmd.equals("playerleave") || cmd.equals("playerexit")) {
			if (args.length == 1) {
				if (p.hasPermission("train.command.playerexit")) {
					prop.allowPlayerExit = Util.getBool(args[0]);
				} else {
					p.sendMessage(ChatColor.RED + "You don't have permission, ask an admin to do this.");
				}
			}
			p.sendMessage(ChatColor.YELLOW + "Players can exit this minecart: " + ChatColor.WHITE + " " + prop.allowPlayerExit);
		} else if (cmd.equals("claim")) {
			prop.clearOwners();
			prop.setOwner(p, true);
			p.sendMessage(ChatColor.YELLOW + "You claimed this minecart your own!");
		} else if (cmd.equals("addowner") || cmd.equals("addowners")) {
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "Please specify the player names to set as owner!");
			} else {
				for (String owner : args) {
					prop.setOwner(owner.toLowerCase());
				}
				p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
			}
		} else if (cmd.equals("setowner") || cmd.equals("setowners")) {
			prop.clearOwners();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All owners for this minecart have been cleared!");
			} else {
				for (String owner : args) {
					prop.setOwner(owner.toLowerCase());
				}
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
			}
		} else if (cmd.equals("addtags") || cmd.equals("addtag")) {
			if (args.length == 0) {
				p.sendMessage(ChatColor.RED + "You need to give at least one tag to add!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for this minecart!");
			}
		} else if (cmd.equals("settags") || cmd.equals("settag") || cmd.equals("tags") || cmd.equals("tag")) {
			prop.clearTags();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All tags for this minecart have been cleared!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for this minecart!");
			}
		} else if (cmd.equals("dest") || cmd.equals("destination")) {
			if (args.length == 0) {
				prop.destination = "";
				p.sendMessage(ChatColor.YELLOW + "The destination for this minecart has been cleared!");
			} else {
				prop.destination = Util.combine(" ", args[0]);
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + args[0] + ChatColor.YELLOW + " as destination for this minecart!");
			}
		} else if (cmd.equals("remove") || cmd.equals("destroy")) {
			MinecartMember mm = prop.getMember();
			if (mm != null) mm.destroy();
			p.sendMessage(ChatColor.YELLOW + "The selected minecart has been destroyed!");
		} else {
			p.sendMessage(ChatColor.RED + "Unknown cart command: '" + cmd + "'!");
		}
		return true;
	}
	

	public static void info(Player player, CartProperties prop) {
		//warning message not taken
		if (!prop.hasOwners()) {
			player.sendMessage(ChatColor.YELLOW + "Note: This minecart is not owned, claim it using /cart claim!");
		}
		if (prop.allowMobsEnter) {
			if (prop.allowPlayerEnter) {
				player.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Mobs and Players");
			} else {
				player.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Mobs");
			}
		} else if (prop.allowPlayerEnter) {
			player.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Players");
		} else {
			player.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.RED + " No one");
		}
		player.sendMessage(ChatColor.YELLOW + "Enter message: " + ChatColor.WHITE + (prop.enterMessage == null ? "None" : prop.enterMessage));
		if (prop.hasTags()) {
			player.sendMessage(ChatColor.YELLOW + "Tags: " + ChatColor.WHITE + " " + Util.combineNames(prop.getTags()));
		} else {
			player.sendMessage(ChatColor.YELLOW + "Tags: " + ChatColor.WHITE + "None");
		}
		if (prop.hasDestination()){					
			player.sendMessage(ChatColor.YELLOW + "This minecart will ignore tag switchers and will attempt to reach " + ChatColor.WHITE + prop.destination);
		}
		if (prop.hasOwners()) {
			player.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + " " + Util.combineNames(prop.getOwners()));
		} else {
			player.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + "Everyone");
		}
	}
	
}
