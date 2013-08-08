package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class SignActionDestination extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("destination");
	}

	@Override
	public boolean click(SignActionEvent info, Player player) {
		//get the train this player is editing
		CartProperties cprop = CartProperties.getEditing(player);
		if (cprop == null) {
			if (Permission.COMMAND_PROPERTIES.has(player)) {
				Localization.EDIT_NOSELECT.message(player);
			} else {
				Localization.EDIT_NOTALLOWED.message(player);
			}
			return true;
		}
		IProperties prop;
		if (info.isTrainSign()) {
			prop = cprop.getTrainProperties();
		} else if (info.isCartSign()) {
			prop = cprop;
		} else {
			return false;
		}
		if (!prop.hasOwnership(player)) {
			Localization.EDIT_NOTOWNED.message(player);
		} else {
			String dest = info.getLine(2);
			prop.setDestination(dest);
			Localization.SELECT_DESTINATION.message(player, dest);
		}
		return true;
	}
	
	@Override
	public void execute(SignActionEvent info) {
		if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_ENTER)) {
			if (!info.hasRailedMember()) return;
			PathNode.getOrCreate(info);
			if (info.getLine(3).isEmpty() || !info.isPowered()) return;
			setDestination(info.getMember().getProperties(), info);
		} else if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER)) {
			if (!info.hasRailedMember()) return;
			PathNode.getOrCreate(info);
			if (info.getLine(3).isEmpty() || !info.isPowered()) return;
			for (CartProperties prop : info.getGroup().getProperties()) {
				setDestination(prop, info);
			}
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			for (TrainProperties prop : info.getRCTrainProperties()) {
				for (CartProperties cprop : prop) {
					// Set the cart destination
					setDestination(cprop, info);
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		boolean succ = false;
		if (event.isTrainSign()) {
			succ = handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set a train destination and the next destination to set once it is reached");
		} else if (event.isCartSign()) {
			succ = handleBuild(event, Permission.BUILD_DESTINATION, "cart destination", "set a cart destination and the next destination to set once it is reached");
		} else if (event.isRCSign()) {
			succ = handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set the destination on a remote train");
		}
		if (succ && !event.getLine(2).isEmpty()) {
			PathNode node = PathNode.get(event.getLine(2));
			if (node != null) {
				Player p = event.getPlayer();
				p.sendMessage(ChatColor.RED + "Another destination with the same name already exists!");
				p.sendMessage(ChatColor.RED + "Please remove either sign and use /train reroute to fix");

				// Send location message
				BlockLocation loc = node.location;
				StringBuilder locMsg = new StringBuilder(100);
				locMsg.append(ChatColor.RED).append("Other sign is ");
				if (loc.getWorld() != event.getPlayer().getWorld()) {
					locMsg.append("on world ").append(ChatColor.WHITE).append(node.location.world);
					locMsg.append(' ').append(ChatColor.RED);
				}
				locMsg.append("at ").append(ChatColor.WHITE);
				locMsg.append('[').append(loc.x).append('/').append(loc.y);
				locMsg.append('/').append(loc.z).append(']');
				p.sendMessage(locMsg.toString());
			}
		}
		return succ;
	}

	@Override
	public void destroy(SignActionEvent event) {
		String name = event.getLine(2);
		if (!LogicUtil.nullOrEmpty(name)) {
			PathNode node = PathNode.get(name);
			if (node != null) {
				node.removeName(name);
			}
		}
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	/**
	 * Sets the destination for a single minecart. Only sets a destination if:<br>
	 * - Redstone change was the cause<br>
	 * - No destination requirement is set on the sign<br>
	 * - The destination requirement equals the current train destination
	 * 
	 * @param prop of the minecart
	 * @param info to use to set the destination
	 */
	public void setDestination(CartProperties prop, SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_CHANGE) || info.getLine(2).isEmpty() 
				|| !prop.hasDestination() || info.getLine(2).equals(prop.getDestination())) {
			prop.setDestination(info.getLine(3));
		}
	}
}
