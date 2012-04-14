package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionTrain extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isTrainSign()) {
			if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
				if (!info.isPoweredFacing()) {
					return;
				}
			}
		} else if (info.isRCSign()) {
			if (!info.isAction(SignActionType.REDSTONE_ON)) {
				return;
			}
		} else {
			return;
		}
		if (info.isType("destroy")) {
			final MinecartGroup group;
			if (info.isRCSign()) {
				group = info.getRCTrainGroup();
			} else {
				group = info.getGroup();
			}
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
		if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("destroy")) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train");
			}
		} else if (mode == SignActionMode.RCTRAIN) {
			if (type.startsWith("destroy")) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train remotely");
			}
		}
		return false;
	}

}
