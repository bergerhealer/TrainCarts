package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public abstract class SignAction {
	private static List<SignAction> actions;
	public static void init() {
		actions = new ArrayList<SignAction>();
		register(new SignActionStation());
		register(new SignActionLauncher());
		register(new SignActionSwitcher());
		register(new SignActionSpawn());
		register(new SignActionBlockChanger());
		register(new SignActionProperties());
		register(new SignActionTrigger());
		register(new SignActionTeleport());
		register(new SignActionJumper());
		register(new SignActionEject());
		register(new SignActionEnter());
		register(new SignActionDestroy());
		register(new SignActionTransfer());
		register(new SignActionFuel());
		register(new SignActionCraft());
		register(new SignActionDetector());
		register(new SignActionDestination());
		register(new SignActionBlocker());
		register(new SignActionWait());
		register(new SignActionElevator());
		register(new SignActionTicket());
		register(new SignActionAnnounce());
		register(new SignActionEffect());
	}

	public static void deinit() {
		actions = null;
	}

	/**
	 * Obtains the SignAction meant for a SignActionEvent
	 * 
	 * @param event to check
	 * @return sign action, or null if not found
	 */
	public static SignAction getSignAction(SignActionEvent event) {
		for (SignAction action : actions) {
			if (action.match(event)) {
				return action;
			}
		}
		return null;
	}

	/**
	 * Checks whether a sign action event is meant for this type of Sign Action
	 * 
	 * @param info event
	 * @return True if it matched, False if not
	 */
	public abstract boolean match(SignActionEvent info);

	/**
	 * Fired when this sign is being executed for a certain event
	 * 
	 * @param info event
	 */
	public abstract void execute(SignActionEvent info);

	/**
	 * Fired when a sign is being built
	 * 
	 * @param event containing relevant Build information
	 * @return True if building is allowed, False if not
	 */
	public abstract boolean build(SignChangeActionEvent event);

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

	/**
	 * Handles the post-destroy logic for when this sign is broken
	 * 
	 * @param info related to the destroy event
	 */
	public void destroy(SignActionEvent info) {
	}

	/**
	 * Handles a player right-clicking this action sign
	 * 
	 * @param info related to the event
	 * @param player that clicked
	 * @return True if handled, False if not
	 */
	public boolean click(SignActionEvent info, Player player) {
		return false;
	}

	public static final <T extends SignAction> T register(T action) {
		if (actions == null) return action;
		actions.add(action);
		return action;
	}

	public static final void unregister(SignAction action) {
		if (actions == null) return;
		actions.remove(action);
	}

	/**
	 * Handles right-click interaction with a Sign
	 * 
	 * @param clickedSign
	 * @param player that clicked
	 * @return Whether the click was handled (and the original interaction should be cancelled)
	 */
	public static boolean handleClick(Block clickedSign, Player player) {
		SignActionEvent info = new SignActionEvent(clickedSign);
		if (info.getSign() == null) {
			return false;
		}
		SignAction action = getSignAction(info);
		return action != null && action.click(info, player);
	}

	public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname) {
		return handleBuild(event, permission, signname, null);
	}

	public static boolean handleBuild(SignChangeActionEvent event, Permission permission, String signname, String signdescription) {
		if (permission.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use this sign")) {
			event.getPlayer().sendMessage(ChatColor.YELLOW + "You built a " + ChatColor.WHITE + signname + ChatColor.YELLOW + "!");
			if (signdescription != null) {
				event.getPlayer().sendMessage(ChatColor.GREEN + "This sign can " + signdescription + ".");
			}
			return true;
		}
		return false;
	}

	public static void handleBuild(SignChangeEvent event) {
		final SignChangeActionEvent info = new SignChangeActionEvent(event);
		SignAction action = getSignAction(info);
		if (action != null) {
			if (action.build(info)) {
				if (!action.canSupportRC() && info.isRCSign()) {
					event.getPlayer().sendMessage(ChatColor.RED + "This sign does not support remote control!");
					if (event.getLine(0).startsWith("[!")) {
						event.setLine(0, "[!train]");
					} else {
						event.setLine(0, "[train]");
					}
				}
				// Tell train above to update signs, if available
				if (info.hasRails()) {
					final MinecartMember<?> member = MinecartMemberStore.getAt(info.getRails());
					if (member != null) {
						member.getGroup().getBlockTracker().updatePosition();
					}
				}
			} else {
				event.setCancelled(true);
			}
			if (event.isCancelled()) {
				return;
			}
		}
		if (info.getMode() != SignActionMode.NONE && event.getBlock().getType() == Material.SIGN_POST) {
			//snap to fixed 45-degree angle
			BlockFace facing = BlockUtil.getFacing(event.getBlock());
			BlockUtil.setFacing(event.getBlock(), Util.snapFace(facing));
		}
	}

	public static void handleDestroy(SignActionEvent info) {
		if (info == null || info.getSign() == null) {
			return;
		}
		SignAction action = getSignAction(info);
		if (action != null) {
			// First, remove this sign from all Minecarts on the world
			for (MinecartGroup group : MinecartGroup.getGroups()) {
				group.getBlockTracker().removeSign(info.getBlock());
			}
			// Handle sign destroy logic
			action.destroy(info);
		}
	}

	public static final void executeAll(SignActionEvent info, SignActionType actiontype) {
		info.setAction(actiontype);
		executeAll(info);
	}

	public static final void executeAll(SignActionEvent info) {
		if (info == null || info.getSign() == null) {
			return;
		}
		//Event
		info.setCancelled(false);
		if (!CommonUtil.callEvent(info).isCancelled() && actions != null) {
			//facing?
			boolean facing;
			if (info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF, 
					SignActionType.MEMBER_UPDATE, SignActionType.GROUP_UPDATE)) {
				facing = true;
			} else {
				facing = info.isFacing();
			}
			SignAction action = getSignAction(info);
			if (action != null && (facing || action.overrideFacing())) {
				try {
					action.execute(info);
				} catch (Throwable t) {
					TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to execute " + info.getAction().toString() + 
							" for " + action.getClass().getSimpleName() + ":", CommonUtil.filterStackTrace(t));
				}
			}
		}
	}
}
