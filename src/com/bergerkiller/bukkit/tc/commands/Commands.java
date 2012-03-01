package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
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
			MinecartMember mm = MinecartMember.getEditing(player);
			if (mm == null) {
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
				TrainProperties prop = mm.getGroup().getProperties();
				if (prop.hasOwnership(player)) {
					return TrainCommands.execute(player, prop, cmd, args);
				} else {
					player.sendMessage(ChatColor.RED + "You don't own this train!");
					return true;
				}
			} else if (command.equalsIgnoreCase("cart")) {
				CartProperties prop = mm.getProperties();
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

}
