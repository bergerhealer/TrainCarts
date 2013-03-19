package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementOwners extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("owner") || text.equals("owners");
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return Util.evaluate(member.getProperties().getOwners().size(), text);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return Util.evaluate(group.getProperties().getOwners().size(), text);
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("o");
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] owners, SignActionEvent event) {
		for (String owner : owners) {
			if (member.getProperties().isOwner(owner.toLowerCase())) {
				return true;
			}
		}
		return false;
	}
}
