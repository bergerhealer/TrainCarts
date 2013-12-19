package com.bergerkiller.bukkit.tc.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;

public class CartCommands {
	
	public static boolean execute(Player p, CartProperties prop, String cmd, String[] args) throws NoPermissionException {
		if (cmd.equals("info") || cmd.equals("i")) {
			info(p, prop);
		} else if (cmd.equals("playerenter")) {
			if (args.length == 1) {
				Permission.COMMAND_PLAYERENTER.handle(p);
				prop.setPlayersEnter(ParseUtil.parseBool(args[0]));
			}
			p.sendMessage(ChatColor.YELLOW + "Players can enter this minecart: " + ChatColor.WHITE + " " + prop.getPlayersEnter());
		} else if (cmd.equals("playerleave") || cmd.equals("playerexit")) {
			if (args.length == 1) {
				Permission.COMMAND_PLAYEREXIT.handle(p);
				prop.setPlayersExit(ParseUtil.parseBool(args[0]));
			}
			p.sendMessage(ChatColor.YELLOW + "Players can exit this minecart: " + ChatColor.WHITE + " " + prop.getPlayersExit());
		} else if (cmd.equals("claim")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			prop.clearOwners();
			prop.setOwner(p, true);
			p.sendMessage(ChatColor.YELLOW + "You claimed this minecart your own!");
		} else if (cmd.equals("addowner") || cmd.equals("addowners")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "Please specify the player names to set as owner!");
			} else {
				for (String owner : args) {
					prop.setOwner(owner.toLowerCase());
				}
				p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
			}
		} else if (cmd.equals("setowner") || cmd.equals("setowners")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			prop.clearOwners();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All owners for this minecart have been cleared!");
			} else {
				for (String owner : args) {
					prop.setOwner(owner.toLowerCase());
				}
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
			}
		} else if (cmd.equals("setownerperm") || cmd.equals("setownerpermission") || cmd.equals("setownerpermissions")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			prop.clearOwnerPermissions();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All owner permissions for this minecart have been cleared!");
			} else {
				for (String ownerPerm : args) {
					prop.getOwnerPermissions().add(ownerPerm);
				}
				p.sendMessage(ChatColor.YELLOW + "You set the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " for this minecart");
				p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this Minecart");
			}
		} else if (cmd.equals("addownerperm") || cmd.equals("addownerpermission") || cmd.equals("addownerpermissions")) {
			Permission.COMMAND_SETOWNERS.handle(p);
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "Please specify the permission nodes to add!");
			} else {
				for (String ownerPerm : args) {
					prop.getOwnerPermissions().add(ownerPerm);
				}
				p.sendMessage(ChatColor.YELLOW + "You added the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " to this minecart");
				p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this Minecart");
			}
		} else if (cmd.equals("addtags") || cmd.equals("addtag")) {
			Permission.COMMAND_SETTAGS.handle(p);
			if (args.length == 0) {
				p.sendMessage(ChatColor.RED + "You need to give at least one tag to add!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as tags for this minecart!");
			}
		} else if (cmd.equals("settags") || cmd.equals("settag") || cmd.equals("tags") || cmd.equals("tag")) {
			Permission.COMMAND_SETTAGS.handle(p);
			prop.clearTags();
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "All tags for this minecart have been cleared!");
			} else {
				prop.addTags(args);
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as tags for this minecart!");
			}
		} else if (cmd.equals("dest") || cmd.equals("destination")) {
			Permission.COMMAND_SETDESTINATION.handle(p);
			if (args.length == 0) {
				prop.clearDestination();
				p.sendMessage(ChatColor.YELLOW + "The destination for this minecart has been cleared!");
			} else {
				prop.setDestination(StringUtil.join(" ", args[0]));
				p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + args[0] + ChatColor.YELLOW + " as destination for this minecart!");
			}
		} else if (cmd.equals("remove") || cmd.equals("destroy")) {
			Permission.COMMAND_DESTROY.handle(p);
			MinecartMember<?> mm = prop.getHolder();
			if (mm == null) {
				CartPropertiesStore.remove(prop.getUUID());
				OfflineGroupManager.removeMember(prop.getUUID());
			} else {
				mm.onDie();
			}
			p.sendMessage(ChatColor.YELLOW + "The selected minecart has been destroyed!");
		} else if (cmd.equals("public")) {
			Permission.COMMAND_SETPUBLIC.handle(p);
			if (args.length == 0) {
				prop.setPublic(true);
			} else {
				prop.setPublic(ParseUtil.parseBool(args[0]));
			}
			p.sendMessage(ChatColor.YELLOW + "The selected minecart can be used by everyone: " + ChatColor.WHITE + prop.isPublic());
		} else if (cmd.equals("private") || cmd.equals("locked") || cmd.equals("lock")) {
			Permission.COMMAND_SETPUBLIC.handle(p);
			if (args.length == 0) {
				prop.setPublic(false);
			} else {
				prop.setPublic(!ParseUtil.parseBool(args[0]));
			}
			p.sendMessage(ChatColor.YELLOW + "The selected minecart can only be used by you: " + ChatColor.WHITE + !prop.isPublic());
		} else if (cmd.equals("pickup")) {
			Permission.COMMAND_PICKUP.handle(p);
			if (args.length == 0) {
				prop.setPickup(true);
			} else {
				prop.setPickup(ParseUtil.parseBool(args[0]));
			}
			p.sendMessage(ChatColor.YELLOW + "The selected minecart picks up nearby items: " + ChatColor.WHITE + prop.canPickup());
		} else if (cmd.equals("break")) {
			Permission.COMMAND_BREAKBLOCK.handle(p);
			if (args.length == 0) {
				p.sendMessage(ChatColor.YELLOW + "This cart breaks: " + ChatColor.WHITE + StringUtil.combineNames(prop.getBlockBreakTypes()));
			} else {
				if (ParseUtil.isBool(args[0]) && !ParseUtil.parseBool(args[0])) {
					prop.clearBlockBreakTypes();
					p.sendMessage(ChatColor.YELLOW + "Block break types have been cleared!");
				} else {
					boolean asBreak = true;
					boolean lastIsBool = ParseUtil.isBool(args[args.length - 1]);
					if (lastIsBool) asBreak = ParseUtil.parseBool(args[args.length - 1]);
					int count = lastIsBool ? args.length - 1 : args.length;
					Set<Material> mats = new HashSet<Material>();
					for (int i = 0; i < count; i++) {
						Material mat = ParseUtil.parseMaterial(args[i], null);
						if (mat != null) {
							if (p.hasPermission("train.command.break.admin") || TrainCarts.canBreak(mat)) {
								mats.add(mat);
							} else {
								p.sendMessage(ChatColor.RED + "You are not allowed to make this cart break '" + mat.toString() + "'!");
							}
						}
					}
					if (mats.isEmpty()) {
						p.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
						return true;
					}
					if (asBreak) {
						prop.getBlockBreakTypes().addAll(mats);
						p.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
					} else {
						prop.getBlockBreakTypes().removeAll(mats);
						p.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
					}
				}
			}
		} else if (cmd.equals("path") || cmd.equals("route") || cmd.equals("pathinfo")) {
			Permission.COMMAND_PATHINFO.handle(p);
			Commands.showPathInfo(p, prop);
		} else if (cmd.equals("teleport") || cmd.equals("tp")) {
			Permission.COMMAND_TELEPORT.handle(p);
			if (!prop.restore()) {
				p.sendMessage(ChatColor.RED + "Cart location could not be found: Cart is lost");
			} else {
				BlockLocation bloc = prop.getLocation();
				World world = bloc.getWorld();
				if (world == null) {
					p.sendMessage(ChatColor.RED + "Cart is on a world that is not loaded (" + bloc.world + ")");
				} else {
					EntityUtil.teleport(p, new Location(world, bloc.x + 0.5, bloc.y + 0.5, bloc.z + 0.5));
				}
			}
		} else if (LogicUtil.contains(cmd, "setblock", "setblocks", "changeblock", "changeblocks", "blockchanger")) {
			Permission.COMMAND_CHANGEBLOCK.handle(p);
			MinecartMember<?> member = prop.getHolder();
			if (member == null) {
				p.sendMessage(ChatColor.RED + "The selected minecart is unloaded: we can not change it at this time!");
			} else if (args.length == 0) {
				member.getEntity().setBlock(Material.AIR);
				p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block cleared!");
			} else {
				List<MinecartMember<?>> members = new ArrayList<MinecartMember<?>>(1);
				members.add(member);
				SignActionBlockChanger.setBlocks(members, StringUtil.join(" ", args));
				p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block updated!");
			}
		} else if (LogicUtil.contains(cmd, "setblockoffset", "changeblockoffset", "blockoffset")) {
			Permission.COMMAND_CHANGEBLOCK.handle(p);
			MinecartMember<?> member = prop.getHolder();
			if (member == null) {
				p.sendMessage(ChatColor.RED + "The selected minecart is unloaded: we can not change it at this time!");
			} else if (args.length == 0) {
				member.getEntity().setBlockOffset(9);
				p.sendMessage(ChatColor.YELLOW + "The selected minecart has its block offset reset!");
			} else {
				int offset = ParseUtil.parseInt(args[0], 9);
				member.getEntity().setBlockOffset(offset);
				p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block offset updated!");
			}
		} else if (args.length == 1 && Util.parseProperties(prop, cmd, args[0])) {
			p.sendMessage(ChatColor.GREEN + "Property has been updated!");
			return true;
		} else {
			// Show help
			if (!cmd.equals("help") && !cmd.equals("?")) {
				p.sendMessage(ChatColor.RED + "Unknown cart command: '" + cmd + "'!");
			}
			help(new MessageBuilder()).send(p);
			return true;
		}
		prop.tryUpdate();
		return true;
	}
	
	public static MessageBuilder help(MessageBuilder builder) {
		builder.green("Available commands: ").yellow("/cart ").red("[info");
		builder.setSeparator(ChatColor.WHITE, "/").setIndent(10);
		builder.red("mobenter").red("playerenter").red("playerexit").red("claim").red("addowners").red("setowners");
		builder.red("addtags").red("settags").red("destination").red("destroy").red("public").red("private");
		builder.red("pickup").red("break");
		return builder.setSeparator(null).red("]");
	}

	public static void info(Player p, CartProperties prop) {
		MessageBuilder message = new MessageBuilder();
	
		//warning message not taken
		if (!prop.hasOwners()) {
			message.newLine().yellow("Note: This minecart is not owned, claim it using /cart claim!");
		}
		message.yellow("Picks up nearby items: ").white(prop.canPickup());
		if (prop.hasBlockBreakTypes()) {
			message.newLine().yellow("Breaks blocks: ").white(StringUtil.combineNames(prop.getBlockBreakTypes()));
		}
		message.newLine().yellow("Enter message: ").white((prop.hasEnterMessage() ? prop.getEnterMessage() : "None"));

		// Remaining common info
		Commands.info(message, prop);

		// Loaded?
		if (prop.getHolder() == null) {
			message.newLine().red("The train of this cart is unloaded! To keep it loaded, use:");
			message.newLine().yellow("   /train keepchunksloaded true");
		}

		// Send
		p.sendMessage(" ");
		message.send(p);
	}
	
}
