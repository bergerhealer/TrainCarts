package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class StatementOwners extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("owner") || text.equals("owners");
	}

	@Override
	public boolean handle(MinecartMember member, String text) {
		return Util.evaluate(member.getProperties().getOwners().size(), text);
	}

	@Override
	public boolean handle(MinecartGroup group, String text) {
		return Util.evaluate(group.getProperties().getOwners().size(), text);
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("o");
	}

	@Override
	public boolean handleArray(MinecartMember member, String[] owners) {
		for (String owner : owners) {
			if (member.getProperties().isOwner(owner.toLowerCase())) {
				return true;
			}
		}
		return false;
	}
}
