package com.bergerkiller.bukkit.tc.properties;

public interface IPropertiesHolder extends IParsable {

	/**
	 * Gets the properties
	 * 
	 * @return the Properties
	 */
	public IProperties getProperties();

	/**
	 * Called when certain properties have changed
	 */
	public void onPropertiesChanged();
}
