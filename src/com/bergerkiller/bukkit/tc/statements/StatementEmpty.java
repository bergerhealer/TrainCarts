package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementEmpty extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("empty");
	}

	@Override
	public boolean handle(MinecartMember member, String text) {
		return !member.hasItems() && !member.hasPassenger();
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
