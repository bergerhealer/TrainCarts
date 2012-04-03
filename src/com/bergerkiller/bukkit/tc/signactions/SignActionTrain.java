package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionTrain extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			if (!info.isTrainSign()) return;
			if (info.isPoweredFacing()) {
				if (info.isType("destroy")) {
					if (!info.hasRailedMember()) return;
					info.getGroup().playLinkEffect();
					info.getGroup().destroy();
				}
			}
		}
	}
	
	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("destroy")) {
				handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train");
			}
		}
	}

}
