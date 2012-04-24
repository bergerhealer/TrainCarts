package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementBoolean extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("true") || text.equals("false");
	}
		
	@Override
	public boolean handle(MinecartMember member, String text) {
		return text.equalsIgnoreCase("true");
	}
	
	@Override
	public boolean handle(MinecartGroup group, String text) {
		return text.equalsIgnoreCase("true");
	}
	
	@Override
	public boolean matchArray(String text) {
		return false;
	}

}
