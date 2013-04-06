package com.bergerkiller.bukkit.tc.properties;

public interface IParsable {
	/**
	 * Sets a property denoted by the key by parsing the args specified
	 * 
	 * @param key of the property (lower-cased and trimmed of surrounding spaces)
	 * @param value to set to
	 * @return True if something was set, False if not
	 */
	public boolean parseSet(String key, String args);
}
