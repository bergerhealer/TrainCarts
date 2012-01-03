package com.bergerkiller.bukkit.tc.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.GroupManager;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.permissions.NoPermissionException;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.utils.ItemUtil;

public class TrainCommands {

	public static boolean execute(Player p, TrainProperties prop, String cmd, String[] args) throws NoPermissionException {
		if (cmd.equals("info") || cmd.equals("i")) {
			info(p, prop);
		} else if (cmd.equals("linking") || cmd.equals("link")) {
			if (args.length == 1) {
				Permission.COMMAND_SETLINKING.handle(p);
				prop.allowLinking = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + " " + prop.allowLinking);
		} else if (cmd.equals("keepchunksloaded")) {
			if (args.length == 1) {
				Permission.COMMAND_KEEPCHUNKSLOADED.handle(p);
				prop.keepChunksLoaded = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + " " + prop.keepChunksLoaded);
		} else if (cmd.equals("claim") || cmd.equals("addowner") || cmd.equals("setowner") || cmd.equals("addowners") || cmd.equals("setowners")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			//claim as many carts as possible
			int changed = 0;
			boolean clear = !cmd.equals("addowner") && !cmd.equals("addowners");
			List<String> toadd = new ArrayList<String>();
			if (cmd.equals("claim")) {
				toadd.add(p.getName().toLowerCase());
			} else if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "Please specify the player names to make owner!");
				return true;
			} else {
				for (String player : args) {
					toadd.add(player.toLowerCase());
				}
			}
			for (CartProperties cprop : prop.getCarts()) {
				if (!CartProperties.hasGlobalOwnership(p)) {
					if (cprop.hasOwners()) {
						if (!cprop.isOwner(p)) continue;
					}
				}
				//claim
				if (clear) cprop.clearOwners();
				for (String owner : toadd) {
					cprop.setOwner(owner);
				}
				changed++;
			}
			if (changed == prop.getCarts().size()) {
				p.sendMessage(ChatColor.YELLOW + "Owners updated for This entire train!");
			} else if (changed == 1) {
				p.sendMessage(ChatColor.YELLOW + "Owners updated for one train cart your own!");
			} else if (changed > 1) {
				p.sendMessage(ChatColor.YELLOW + "Owners updated for " + changed + " train carts your own!");
			} else {
				p.sendMessage(ChatColor.RED + "You failed to set any owners: you don't own any carts!");
			}
		} else if (cmd.equals("pushmobs") || cmd.equals("pushplayers") || cmd.equals("pushmisc")) {
			Permission.COMMAND_PUSHING.handle(p);
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
			Permission.COMMAND_PUSHING.handle(p);
			if (args.length == 1) {
				prop.pushPlayers = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Pushes away players: " + ChatColor.WHITE + " " + prop.pushPlayers);
		} else if (cmd.equals("pushmisc")) {
			Permission.COMMAND_PUSHING.handle(p);
			if (args.length == 1) {
				prop.pushMisc = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Pushes away misc. entities: " + ChatColor.WHITE + " " + prop.pushMisc);
			//==================================================================
		} else if (cmd.equals("slowdown") || cmd.equals("slow") || cmd.equals("setslow") || cmd.equals("setslowdown")) {
			Permission.COMMAND_SLOWDOWN.handle(p);
			if (args.length == 1) {
				prop.slowDown = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Slow down: " + ChatColor.WHITE + prop.slowDown);
		} else if (cmd.equals("setcollide") || cmd.equals("setcollision") || cmd.equals("collision") || cmd.equals("collide")) {
			Permission.COMMAND_SETCOLLIDE.handle(p);
			if (args.length == 1) {
				prop.trainCollision = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Can collide with other trains: " + ChatColor.WHITE + prop.trainCollision);
		} else if (cmd.equals("speedlimit") || cmd.equals("maxspeed")) {
			Permission.COMMAND_SETSPEEDLIMIT.handle(p);
			if (args.length == 1) {
				try {
					prop.speedLimit = Double.parseDouble(args[0]);
				} catch (NumberFormatException ex) {
					prop.speedLimit = 0.4;
				}
			}
			p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.speedLimit + " blocks/tick");
		} else if (cmd.equals("requirepoweredminecart") || cmd.equals("requirepowered")) {
			Permission.COMMAND_SETPOWERCARTREQ.handle(p);
			if (args.length == 1) {
				prop.requirePoweredMinecart = Util.getBool(args[0]);
			}
			p.sendMessage(ChatColor.YELLOW + "Requires powered minecart to stay alive: " + ChatColor.WHITE + prop.requirePoweredMinecart);
		} else if (cmd.equals("rename") || cmd.equals("setname") || cmd.equals("name")) {
			Permission.COMMAND_RENAME.handle(p);
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
		} else if (cmd.equals("addtags") || cmd.equals("addtag")) {
			Permission.COMMAND_SETTAGS.handle(p);
			if (args.length == 0) {
				p.sendMessage(ChatColor.RED + "You need to give at least one tag to add!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for all minecarts in this train!");
			}
		} else if (cmd.equals("settags") || cmd.equals("settag") || cmd.equals("tags") || cmd.equals("tag")) {
			Permission.COMMAND_SETTAGS.handle(p);
			prop.clearTags();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All tags of all minecarts in this train have been cleared!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for all minecarts in this train!");
			}
		} else if (cmd.equals("dest") || cmd.equals("destination")) {
			Permission.COMMAND_SETDESTINATION.handle(p);
			if (args.length == 0) {
				prop.clearDestination();
				p.sendMessage(ChatColor.YELLOW + "The destination for all minecarts in this train has been cleared!");
			} else {
				String dest = Util.combine(" ", args);
				prop.setDestination(dest);
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + dest + ChatColor.YELLOW + " as destination for all the minecarts in this train!");
			}
		} else if (cmd.equals("remove") || cmd.equals("destroy")) {
			Permission.COMMAND_DESTROY.handle(p);
			Commands.permission(p, "train.command.destroy");
			MinecartGroup group = prop.getGroup();
			if (group == null) {
				prop.remove();
			} else {
				group.destroy();
			}
			p.sendMessage(ChatColor.YELLOW + "The selected train has been destroyed!");
		} else if (cmd.equals("public")) {
			Permission.COMMAND_SETPUBLIC.handle(p);
			boolean pub;
			if (args.length == 0) {
				pub = true;
			} else {
				pub = Util.getBool(args[0]);
			}
			for (CartProperties cprop : prop.getCarts()) cprop.isPublic = pub;
			p.sendMessage(ChatColor.YELLOW + "The selected train can be used by everyone: " + ChatColor.WHITE + pub);
		} else if (cmd.equals("private") || cmd.equals("locked") || cmd.equals("lock")) {
			Permission.COMMAND_SETPUBLIC.handle(p);
			boolean pub;
			if (args.length == 0) {
				pub = false;
			} else {
				pub = !Util.getBool(args[0]);
			}
			for (CartProperties cprop : prop.getCarts()) cprop.isPublic = pub;
			p.sendMessage(ChatColor.YELLOW + "The selected train can only be used by the respective owners: " + ChatColor.WHITE + !pub);
		} else if (cmd.equals("pickup")) {
			Permission.COMMAND_PICKUP.handle(p);
			boolean mode = true;
			if (args.length > 0) mode = Util.getBool(args[0]);
			prop.setPickup(mode);
			p.sendMessage(ChatColor.YELLOW + "The selected train picks up nearby items: " + ChatColor.WHITE + mode);
		} else if (cmd.equals("break")) {
			Permission.COMMAND_BREAKBLOCK.handle(p);
			if (args.length == 0) {
				Set<Material> types = new HashSet<Material>();
				for (CartProperties cprop : prop.getCarts()) types.addAll(cprop.blockBreakTypes);
				p.sendMessage(ChatColor.YELLOW + "This train breaks: " + ChatColor.WHITE + Util.combineNames(types));
			} else {
				if (Util.isBool(args[0]) && !Util.getBool(args[0])) {
					for (CartProperties cprop : prop.getCarts()) cprop.blockBreakTypes.clear();
					p.sendMessage(ChatColor.YELLOW + "Train block break types have been cleared!");
				} else {
					boolean asBreak = true;
					boolean lastIsBool = Util.isBool(args[args.length - 1]);
				    if (lastIsBool) asBreak = Util.getBool(args[args.length - 1]);
				    int count = lastIsBool ? args.length - 1 : args.length;
				    Set<Material> mats = new HashSet<Material>();
					for (int i = 0; i < count; i++) {
						Material mat = ItemUtil.getMaterial(args[i]);
						if (mat != null) {
							if (p.hasPermission("train.command.break.admin") || TrainCarts.canBreak(mat)) {
								mats.add(mat);
							} else {
								p.sendMessage(ChatColor.RED + "You are not allowed to make this train break '" + mat.toString() + "'!");
							}
						}
					}
					if (mats.isEmpty()) {
						p.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
						return true;
					}
					if (asBreak) {
						for (CartProperties cprop : prop.getCarts()) {
							cprop.blockBreakTypes.addAll(mats);
						}
						p.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + Util.combineNames(mats));
					} else {
						for (CartProperties cprop : prop.getCarts()) {
							cprop.blockBreakTypes.removeAll(mats);
						}
						p.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + Util.combineNames(mats));
					}
				}
			}
		} else {
			p.sendMessage(ChatColor.RED + "Unknown cart command: '" + cmd + "'!");
		}
		return true;
	}
	
	public static void info(Player p, TrainProperties prop) {
		if (!prop.isDirectOwner(p)) {
			if (!prop.hasOwners()) {
				p.sendMessage(ChatColor.YELLOW + "Note: This train is not owned, claim it using /train claim!");
			}
		}
		p.sendMessage(ChatColor.YELLOW + "Train name: " + ChatColor.WHITE + prop.getTrainName());
		p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + " " + prop.allowLinking);
		p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + " " + prop.keepChunksLoaded);
		p.sendMessage(ChatColor.YELLOW + "Can collide with other trains: " + ChatColor.WHITE + " " + prop.trainCollision);
		//push away
		ArrayList<String> pushlist = new ArrayList<String>();
		if (prop.pushMobs) pushlist.add("Mobs");
		if (prop.pushPlayers) pushlist.add("Players");
		if (prop.pushMisc) pushlist.add("Misc");
		if (pushlist.size() == 0) {
			p.sendMessage(ChatColor.YELLOW + "This train will never push anything.");
		} else {
			p.sendMessage(ChatColor.YELLOW + "Is pushing away " + ChatColor.WHITE + Util.combineNames(pushlist));
		}
		p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.speedLimit + " blocks/tick");
		if (prop.hasOwners()) {
			p.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + " " + Util.combineNames(prop.getOwners()));
		} else {
			p.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + "Everyone");
		}
	}
	
}
