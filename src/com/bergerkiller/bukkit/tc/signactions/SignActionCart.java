package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionCart extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
			if (!info.isCartSign()) return;
			if (info.isPowered()) {
				if (info.isType("destroy")) {
					if (!info.hasRailedMember()) return;
					info.getMember().die();
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("destroy")) {
				return handleBuild(event, Permission.BUILD_DESTRUCTOR, "cart destructor", "destroy minecarts");
			}
		}
		return false;
	}

}
