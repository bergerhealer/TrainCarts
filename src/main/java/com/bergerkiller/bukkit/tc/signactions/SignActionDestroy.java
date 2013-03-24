package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionDestroy extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("destroy");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isPowered()) return;
		if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
			info.getGroup().playLinkEffect();
			info.getGroup().destroy();
		} else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
			info.getMember().onDie();
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			for (MinecartGroup group : info.getRCTrainGroups()) {
				group.playLinkEffect();
				group.destroy();
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isCartSign()) {
			return handleBuild(event, Permission.BUILD_DESTRUCTOR, "cart destructor", "destroy minecarts");
		} else if (event.isTrainSign()) {
			return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train");
		} else if (event.isRCSign()) {
			return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train remotely");
		}
		return false;
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}
}
