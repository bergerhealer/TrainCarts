package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

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
	
}
