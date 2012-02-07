package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class SignActionDestination extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("destination")) return;
		if (!info.hasMember()) return;
		if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_ENTER)) {
		    PathNode.getOrCreate(info);
			if (info.getLine(3).isEmpty() || !info.isPowered()) return;
			info.getMember().getProperties().destination = info.getLine(3);
		} else if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER)) {
			PathNode.getOrCreate(info);
			if (info.getLine(3).isEmpty() || !info.isPowered()) return;
			info.getGroup().getProperties().setDestination(info.getLine(3));
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("destination")) {
				handleBuild(event, Permission.BUILD_DESTINATION, "train destination", "set a train destination and the next destination to set once it is reached");			
			}
		} else if (mode == SignActionMode.CART) {
			if (type.startsWith("destination")) {
				handleBuild(event, Permission.BUILD_DESTINATION, "cart destination", "set a cart destination and the next destination to set once it is reached");
			}
		}
	}

}
