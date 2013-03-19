package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementEmpty extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("empty");
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return !group.hasItems() && !group.hasPassenger();
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		if (member instanceof MinecartMemberChest) {
			return !((MinecartMemberChest) member).hasItems();
		}
		return !member.getEntity().hasPassenger();
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
