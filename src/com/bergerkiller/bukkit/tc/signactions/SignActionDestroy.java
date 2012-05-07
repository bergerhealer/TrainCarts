package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionDestroy extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("destroy") || !info.isPowered()) return;
		if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
			info.getGroup().playLinkEffect();
			info.getGroup().destroy();
		} else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
			info.getMember().die();
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			MinecartGroup group = info.getRCTrainGroup();
			if (group != null) {
				group.playLinkEffect();
				group.destroy();
			}
		}
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (type.startsWith("destroy")) {
			if (mode == SignActionMode.CART) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "cart destructor", "destroy minecarts");
			} else if (mode == SignActionMode.TRAIN) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train");
			} else if (mode == SignActionMode.RCTRAIN) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train remotely");
			}
		}
		return false;
	}
}
