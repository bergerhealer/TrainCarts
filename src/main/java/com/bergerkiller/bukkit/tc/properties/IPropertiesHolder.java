package com.bergerkiller.bukkit.tc.properties;

import org.bukkit.World;

public interface IPropertiesHolder {

    /**
     * World the holder of these properties are at.
     * Returns null if not applicable or available.
     * 
     * @return world
     */
    World getWorld();

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
