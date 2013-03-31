package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class SignActionProperties extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("property");
	}

	@Override
	public void execute(SignActionEvent info) {
		final boolean powerChange = info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF);
		if ((powerChange || info.isAction(SignActionType.MEMBER_ENTER)) && info.isCartSign() && info.hasMember()) {
			parseSet(info.getMember().getProperties(), info);
		} else if ((powerChange || info.isAction(SignActionType.GROUP_ENTER)) && info.isTrainSign() && info.hasGroup()) {
			parseSet(info.getGroup().getProperties(), info);
		} else if (powerChange && info.isRCSign()) {
			for (TrainProperties prop : info.getRCTrainProperties()) {
				parseSet(prop, info);
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isCartSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "cart property setter", "set properties on the cart above");
		} else if (event.isTrainSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "train property setter", "set properties on the train above");
		} else if (event.isRCSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "train property setter", "remotely set properties on the train specified");
		}
		return false;
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	private static void parseSet(IProperties properties, SignActionEvent info) {
		String mode = info.getLine(2).toLowerCase().trim();
		String[] args = Util.splitBySeparator(info.getLine(3));
		if (args.length >= 2) {
			properties.parseSet(mode, info.isPowered() ? args[0] : args[1]);
		} else if (args.length == 1 && info.isPowered()) {
			properties.parseSet(mode, args[0]);
		}
	}
}
