package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementPassenger extends Statement {

	@Override
	public boolean match(String text) {
		return text.startsWith("passenger") || text.startsWith("player");
	}
	
	@Override
	public boolean handle(MinecartMember member, String text, SignActionEvent event) {
		return text.toLowerCase().startsWith("player") ? member.hasPlayerPassenger() : member.hasPassenger();
	}
	
	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		int count = 0;
		boolean playermode = text.toLowerCase().startsWith("player");
		for (MinecartMember member : group) {
			if (playermode ? member.hasPlayerPassenger() : member.hasPassenger()) {
				count++;
			}
		}
		return Util.evaluate(count, text);
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("p");
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] names, SignActionEvent event) {
		if (member.hasPlayerPassenger()) {
			String pname = ((Player) member.passenger.getBukkitEntity()).getName();
			for (String name : names) {
				if (pname.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}
		return false;
	}
}
