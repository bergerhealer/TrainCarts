package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class StatementCartCount extends Statement {

	@Override
	public boolean match(String text) {
		return text.startsWith("cartcount") || text.startsWith("trainsize");
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
	
	@Override
	public boolean handle(MinecartGroup group, String text) {
		return Util.evaluate(group.size(), text);
	}
}
