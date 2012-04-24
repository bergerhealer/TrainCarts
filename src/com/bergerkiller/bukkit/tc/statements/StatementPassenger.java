package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementPassenger extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("passenger") || text.equals("passengers");
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		return member.hasPassenger();
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("p");
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] names) {
		String pname = ((Player) member.passenger.getBukkitEntity()).getName();
		for (String name : names) {
			if (pname.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

}
