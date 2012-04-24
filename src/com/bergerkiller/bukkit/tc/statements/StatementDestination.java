package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementDestination extends Statement {
	
	@Override
	public boolean match(String text) {
		return text.equals("destination");
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		return member.getProperties().hasDestination();
	}
	
	@Override
	public boolean matchArray(String text) {
		return text.equals("d");
	}
		
	@Override
	public boolean handleArray(MinecartMember member, String text[]) {
		String dest = member.getProperties().destination;
		for (String elem : text) {
			if (dest == null ? elem.length() == 0 : elem.equals(dest)) {
				return true;
			}
		}
		return false;
	}
}
