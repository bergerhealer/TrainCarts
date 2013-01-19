package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class SignActionProperties extends SignAction {

	@Override
	public boolean canSupportRC() {
		return true;
	}
	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.isCartSign()) {
			if (info.isType("property")) {
				if (info.isPowered()) {
					if (info.hasRailedMember()) {
						String mode = info.getLine(2).toLowerCase().trim();
						info.getMember().getProperties().parseSet(mode, info.getLine(3));
					}
				}
			}
		} else if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.isTrainSign()) {
			if (info.isType("property")) {
				if (info.isPowered()) {
					if (info.hasRailedMember()) {
						String mode = info.getLine(2).toLowerCase().trim();
						info.getGroup().getProperties().parseSet(mode, info.getLine(3));
					}
				}
			}
		} else if (info.isAction(SignActionType.REDSTONE_ON) && info.isRCSign()) {
			if (info.isType("property")) {
				String mode = info.getLine(2).toLowerCase().trim();
				for (TrainProperties prop : info.getRCTrainProperties()) {
					prop.parseSet(mode, info.getLine(3));
				}
			}
		}
	}
	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isType("property")) {
			if (event.isCartSign()) {
				return handleBuild(event, Permission.BUILD_PROPERTY, "cart property setter", "set properties on the cart above");
			} else if (event.isTrainSign()) {
				return handleBuild(event, Permission.BUILD_PROPERTY, "train property setter", "set properties on the train above");
			} else if (event.isRCSign()) {
				return handleBuild(event, Permission.BUILD_PROPERTY, "train property setter", "remotely set properties on the train specified");
			}
		}
		return false;
	}
}
