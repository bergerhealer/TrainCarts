package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionTrigger extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("trigger");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER, SignActionType.REDSTONE_OFF)) {
			if (info.isTrainSign() || info.isCartSign()) {
				if (info.isPowered()) {
					ArrivalSigns.trigger(info.getSign(), info.getMember());
				} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
					ArrivalSigns.timeCalcStop(info.getBlock());
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			return handleBuild(event, Permission.BUILD_TRIGGER, "train trigger", "reset the arrival time, train name and destination, which can be displayed using SignLink");
		}
		return false;
	}
}
