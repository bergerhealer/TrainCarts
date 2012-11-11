package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public abstract class SignAction {
	/**
	 * Whether the remote control format is supported for this sign
	 */
	public boolean canSupportRC() {
		return false;
	}
	/**
	 * Whether this sign overrides the internal facing check
	 */
	public boolean overrideFacing() {
		return false;
	}

	private static List<SignAction> actions;
	public static void init() {
		actions = new ArrayList<SignAction>();
		register(new SignActionStation());
		register(new SignActionSwitcher());
		register(new SignActionSpawn());
		register(new SignActionProperties());
		register(new SignActionTrigger());
		register(new SignActionTeleport());
		register(new SignActionEject());
		register(new SignActionDestroy());
		register(new SignActionCollect());
		register(new SignActionDeposit());
		register(new SignActionFuel());
		register(new SignActionCraft());
		register(new SignActionDetector());
		register(new SignActionDestination());
		register(new SignActionBlock());
		register(new SignActionWait());
		register(new SignActionElevator());
		register(new SignActionTicket());
		register(new SignActionAnnounce());
		register(new SignActionEffect());
	}
	public static void deinit() {
		actions = null;
	}

	public abstract void execute(SignActionEvent info);
	public abstract boolean build(SignChangeActionEvent info);

	/**
	 * Handles a player clicking this action sign
	 * 
	 * @param info related to the event
	 * @param player that clicked
	 * @param action that was performed
	 * @return True if handled, False if not
	 */
	public boolean click(SignActionEvent info, Player player, Action action) {
		return false;
	}

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

	public static void handleClick(PlayerInteractEvent event) {
		SignActionEvent info = new SignActionEvent(event.getClickedBlock());
		if (info.getSign() == null) {
			return;
		}
		for (SignAction action : actions) {
			if (action.click(info, event.getPlayer(), event.getAction())) {
				event.setCancelled(true); // Prevent block placement
			}
			if (info.isCancelled()) break;
		}
	}
	
	public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname) {
		return handleBuild(event, permission, signname, null);
	}
	public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname, String signdescription) {
		return handleBuild(event, permission.toString(), signname, signdescription);
	}
	public static boolean handleBuild(SignChangeActionEvent event, String permission, String signname) {
		return handleBuild(event, permission, signname, null);
	}
	public static boolean handleBuild(SignChangeActionEvent event, String permission, String signname, String signdescription) {
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
		SignChangeActionEvent info = new SignChangeActionEvent(event);
		for (SignAction action : actions) {
			if (action.build(info)) {
				if (!action.canSupportRC() && info.isRCSign()) {
					event.getPlayer().sendMessage(ChatColor.RED + "This sign does not support remote control!");
					if (event.getLine(0).startsWith("[!")) {
						event.setLine(0, "[!train]");
					} else {
						event.setLine(0, "[train]");
					}
				}
			}
			if (event.isCancelled()) return;
		}
		if (info.getMode() != SignActionMode.NONE && event.getBlock().getType() == Material.SIGN_POST) {
			//snap to fixed 45-degree angle
			BlockFace facing = BlockUtil.getFacing(event.getBlock());
			BlockUtil.setFacing(event.getBlock(), Util.snapFace(facing));
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
		if (!CommonUtil.callEvent(info).isCancelled() && actions != null) {
			//facing?
			boolean facing;
			if (info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
				facing = true;
			} else {
				facing = info.isFacing();
			}
			for (SignAction action : actions) {
				if (facing || action.overrideFacing()) {
					action.execute(info);
					if (info.isCancelled()) break;
				}
			}
		}
	}
	
}
