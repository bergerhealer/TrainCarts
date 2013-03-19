package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementVelocity extends Statement {

	@Override
	public boolean match(String text) {
		return text.startsWith("vel") || text.startsWith("speed");
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return Util.evaluate(member.getForce(), text);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return Util.evaluate(group.getAverageForce(), text);
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
