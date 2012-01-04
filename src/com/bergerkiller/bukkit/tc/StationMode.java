package com.bergerkiller.bukkit.tc;

public enum StationMode {
	LEFT, RIGHT, CONTINUE, REVERSE, NONE;
	
	public static StationMode fromString(String value) {
		for (StationMode mode : values()) {
			if (mode.toString().equalsIgnoreCase(value)) return mode;
		}
		return NONE;
	}
}
