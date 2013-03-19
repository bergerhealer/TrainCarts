package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementDestination extends Statement {
	
	@Override
	public boolean match(String text) {
		return text.equals("destination");
	}
	
	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return member.getProperties().hasDestination();
	}
	
	@Override
	public boolean matchArray(String text) {
		return text.equals("d");
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
		String dest = member.getProperties().getDestination();
		for (String elem : text) {
			if (dest == null ? elem.length() == 0 : elem.equals(dest)) {
				return true;
			}
		}
		return false;
	}
}
