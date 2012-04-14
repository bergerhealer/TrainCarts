package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class SignActionDestination extends SignAction {

	public void setDestination(CartProperties prop, SignActionEvent info) {
		if (!info.isAction(SignActionType.REDSTONE_CHANGE)) {
			if (!info.getLine(2).isEmpty() && prop.hasDestination()) {
				if (!info.getLine(2).equals(prop.destination)) {
					return;
				}
			}
		}
		prop.destination = info.getLine(3);
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
			for (CartProperties prop : info.getGroup().getProperties().getCarts()) {
				setDestination(prop, info);
			}
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			TrainProperties prop = info.getRCTrainProperties();
			for (CartProperties cprop : prop.getCarts()) {
				setDestination(cprop, info);
			}
		}
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}
	
	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set a train destination and the next destination to set once it is reached");			
			}
		} else if (mode == SignActionMode.CART) {
			if (type.startsWith("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "cart destination", "set a cart destination and the next destination to set once it is reached");
			}
		} else if (mode == SignActionMode.RCTRAIN) {
			if (type.startsWith("destination")) {
				return handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set the destination on a remote train");
			}
		}
		return false;
	}

}
