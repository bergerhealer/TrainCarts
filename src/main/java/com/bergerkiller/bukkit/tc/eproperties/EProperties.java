package com.bergerkiller.bukkit.tc.eproperties;

public interface EProperties {
	
	/**
	 * Parse the property to a cart
	 * 
	 * @param mode Property mode
	 * @param value Property value
	 */
	public void parseSet(String mode, String value);
}