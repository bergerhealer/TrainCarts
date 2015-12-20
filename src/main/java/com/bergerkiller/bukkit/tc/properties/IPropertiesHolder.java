package com.bergerkiller.bukkit.tc.properties;

public interface IPropertiesHolder extends IParsable {

    /**
     * Gets the properties
     *
     * @return the Properties
     */
    IProperties getProperties();

    /**
     * Called when certain properties have changed
     */
    void onPropertiesChanged();
}
