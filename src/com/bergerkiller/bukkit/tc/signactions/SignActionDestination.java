package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class SignActionDestination extends SignAction {

	public void setDestination(CartProperties prop, SignActionEvent info) {
		if (!info.isAction(SignActionType.REDSTONE_CHANGE)) {
			if (!info.getLine(2).isEmpty() && prop.hasDestination()) {
				if (!info.getLine(2).equals(prop.getDestination())) {
					return;
				}
			}
		}
		prop.setDestination(info.getLine(3));
	}

	@Override
	public boolean click(SignActionEvent info, Player player, Action action) {
		if (!info.isType("destination")) {
			return false;
		}

		//get the train this player is editing
		CartProperties cprop = CartProperties.getEditing(player);
		if (cprop == null) {
			if (CartProperties.canHaveOwnership(player)) {
				player.sendMessage(ChatColor.YELLOW + "You haven't selected a train to edit yet!");
			} else {
				player.sendMessage(ChatColor.RED + "You are not allowed to own trains!");
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
		if (!prop.isOwner(player)) {
			player.sendMessage(ChatColor.RED + "You don't own this train!");
		} else {
			String dest = info.getLine(2);
			prop.setDestination(dest);
			player.sendMessage(ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + dest + ChatColor.YELLOW + " as your destination!");
		}
		return true;
	}
	
	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("destination")) return;
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
					setDestination(cprop, info);
				}
			}
		}
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}
	
	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isTrainSign()) {
			if (event.isType("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set a train destination and the next destination to set once it is reached");			
			}
		} else if (event.isCartSign()) {
			if (event.isType("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "cart destination", "set a cart destination and the next destination to set once it is reached");
			}
		} else if (event.isRCSign()) {
			if (event.isType("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set the destination on a remote train");
			}
		}
		return false;
	}

}
