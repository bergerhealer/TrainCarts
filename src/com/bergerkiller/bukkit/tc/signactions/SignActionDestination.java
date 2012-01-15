package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Destination;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;

public class SignActionDestination extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("destination")) return;
		if (!info.hasMember()) return;
		if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_ENTER)) {
			Destination.getDir(info.getMember().getProperties().destination, info.getLine(2));
			if (info.getLine(3).isEmpty()) return;
			info.getMember().getProperties().destination = info.getLine(3);
		} else if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER)) {
			Destination.getDir(info.getMember().getProperties().destination, info.getLine(2));
			if (info.getLine(3).isEmpty()) return;
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
