package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class StatementType extends Statement {

	@Override
	public boolean match(String text) {
		return getType(text) >= 0;
	}
	
	public int getType(String text) {
		text = text.toLowerCase();
		if (text.startsWith("powered")) {
			return 2;
		} else if (text.startsWith("storage")) {
			return 1;
		} else if (text.startsWith("minecart")) {
			return 0;
		} else if (text.startsWith("cartcount") || text.startsWith("trainsize")) {
			return 3;
		} else {
			return -1;
		}
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		int type = getType(text);
		return type == 3 || member.type == type;
	}
	
	@Override
	public boolean handle(MinecartGroup group, String text) {
		int type = getType(text);
		return Util.evaluate(type == 3 ? group.size() : group.size(type), text);
	}
	
	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
