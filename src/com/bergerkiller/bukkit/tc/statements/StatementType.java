package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementType extends Statement {

	@Override
	public boolean match(String text) {
		return getType(text) >= 0;
	}
	
	public int getType(String text) {
		if (text.equalsIgnoreCase("powered")) {
			return 2;
		} else if (text.equalsIgnoreCase("storage")) {
			return 1;
		} else if (text.equalsIgnoreCase("minecart")) {
			return 0;
		} else {
			return -1;
		}
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		return member.type == getType(text);
	}
	
	@Override
	public boolean handle(MinecartGroup group, String text) {
		return group.size(getType(text)) > 0;
	}
	
	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
