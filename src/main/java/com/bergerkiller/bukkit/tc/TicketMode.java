package com.bergerkiller.bukkit.tc;

public enum TicketMode {
	ADD, CHECK, BUY;
	
	public static TicketMode parse(String text) {
		text = text.toLowerCase();
		if (text.contains("add")) return ADD;
		if (text.contains("check")) return CHECK;
		return BUY;
	}
}
