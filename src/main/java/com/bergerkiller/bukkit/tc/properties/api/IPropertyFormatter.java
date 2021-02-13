package com.bergerkiller.bukkit.tc.properties.api;

import com.bergerkiller.bukkit.tc.properties.IProperties;

/**
 * Represents an initialized {@link PropertyFormatter} method
 */
public interface IPropertyFormatter {

    /**
     * Gets the property instance this formatter is for
     * 
     * @return property
     */
    IProperty<?> getProperty();

    /**
     * Gets the name by which this property formatter was found
     * 
     * @return property formatter name
     */
    String getName();

    /**
     * Formats the property value using this formatter.
     * Return values can be anything, but generally are either:
     * <ul>
     * <li>Boxed primitives such as int, double, boolean</li>
     * <li>String</li>
     * <li>Collection of the above</li>
     * </ul>
     *
     * @param properties The properties data to format
     * @return formatted value
     */
    Object format(IProperties properties);
}
