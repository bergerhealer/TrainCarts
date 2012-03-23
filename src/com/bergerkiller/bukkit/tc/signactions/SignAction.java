package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public abstract class SignAction {
	
	public static void init() {
		actions = new ArrayList<SignAction>();
		register(new SignActionStation());
		register(new SignActionSwitcher());
		register(new SignActionSpawn());
		register(new SignActionProperties());
		register(new SignActionTrigger());
		register(new SignActionTeleport());
		register(new SignActionEject());
		register(new SignActionCart());
		register(new SignActionTrain());
		register(new SignActionCollect());
		register(new SignActionDeposit());
		register(new SignActionCraft());
		register(new SignActionDetector());
		register(new SignActionDestination());
		register(new SignActionBlock());
		register(new SignActionWait());
	}
	public static void deinit() {
		actions = null;
	}
		
	public abstract void execute(SignActionEvent info);
	public abstract void build(SignChangeEvent event, String type, SignActionMode mode);
	
	private static List<SignAction> actions;
	public static final <T extends SignAction> T register(T action) {
		if (actions == null) return action;
		actions.add(action);
		return action;
	}
	public final void register() {
		register(this);
	}
	public static final void unregister(SignAction action) {
		if (actions == null) return;
		actions.remove(action);
	}
	
	public static boolean handleBuild(SignChangeEvent event, Permission permission, String signname) {
		return handleBuild(event, permission, signname, null);
	}
	public static boolean handleBuild(SignChangeEvent event, Permission permission, String signname, String signdescription) {
		return handleBuild(event, permission.toString(), signname, signdescription);
	}
	public static boolean handleBuild(SignChangeEvent event, String permission, String signname) {
		return handleBuild(event, permission, signname, null);
	}
	public static boolean handleBuild(SignChangeEvent event, String permission, String signname, String signdescription) {
		if (event.getPlayer().hasPermission(permission)) {
			event.getPlayer().sendMessage(ChatColor.YELLOW + "You built a " + ChatColor.WHITE + signname + ChatColor.YELLOW + "!");
			if (signdescription != null) {
				event.getPlayer().sendMessage(ChatColor.GREEN + "This sign can " + signdescription + ".");
			}
			return true;
		} else {
			event.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to use this sign");
			event.setCancelled(true);
			return false;
		}
	}
	
	public static void handleBuild(SignChangeEvent event) {
		SignActionMode mode = SignActionMode.fromEvent(event);
		String type = event.getLine(1).toLowerCase();
		for (SignAction action : actions) {
			action.build(event, type, mode);
			if (event.isCancelled()) return;
		}
		if (mode != SignActionMode.NONE && event.getBlock().getType() == Material.SIGN_POST) {
			//snap to fixed 90-degree angle
			BlockFace facing = BlockUtil.getFacing(event.getBlock());
			switch (facing) {
			case EAST_NORTH_EAST:
			case EAST_SOUTH_EAST:
			case NORTH_EAST:
				facing = BlockFace.EAST; break;
			case NORTH_NORTH_EAST:
			case NORTH_NORTH_WEST:
			case NORTH_WEST:
				facing = BlockFace.NORTH; break;
			case SOUTH_SOUTH_EAST:
			case SOUTH_SOUTH_WEST:
			case SOUTH_EAST:
				facing = BlockFace.SOUTH; break;
			case WEST_NORTH_WEST:
			case WEST_SOUTH_WEST:
			case SOUTH_WEST:
				facing = BlockFace.WEST; break;
			}
			BlockUtil.setFacing(event.getBlock(), facing);
		}
	}
	public static final void executeAll(SignActionEvent info, SignActionType actiontype) {
		info.setAction(actiontype);
		executeAll(info);
	}
	public static final void executeAll(SignActionEvent info) {
		if (info.getSign() == null) return;
		//Event
		info.setCancelled(false);
		Bukkit.getServer().getPluginManager().callEvent(info);
		if (!info.isCancelled() && actions != null) {
			for (SignAction action : actions) {
				action.execute(info);
				if (info.isCancelled()) break;
			}
		}
	}
	
}
