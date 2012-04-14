package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionTrigger extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER, SignActionType.REDSTONE_OFF)) {
			if (info.isTrainSign() || info.isCartSign()) {
				if (info.isType("trigger")) {
					if (info.isAction(SignActionType.REDSTONE_ON) || info.isFacing()) {
						ArrivalSigns.trigger(info.getSign(), info.getMember());
					} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
						ArrivalSigns.timeCalcStop(info.getLocation());
					}
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("trigger")) {
				return handleBuild(event, Permission.BUILD_TRIGGER, "train trigger", "reset the arrival time, train name and destination, which can be displayed using SignLink");
			}
		}
		return false;
	}
	
}
