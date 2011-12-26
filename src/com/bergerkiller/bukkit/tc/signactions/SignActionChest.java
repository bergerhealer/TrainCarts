package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionChest extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign()) {
			if (info.isType("chest")) {
				
				
			}
		} else if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign()) {
			if (info.isType("chest")) {
				
				
			}
		}
	}

}
