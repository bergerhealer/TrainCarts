package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementEmpty extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("empty");
	}

	@Override
	public boolean handle(MinecartMember member, String text, SignActionEvent event) {
		return !member.hasItems() && !member.hasPassenger();
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
