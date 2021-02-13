package com.bergerkiller.bukkit.tc.properties.api.context;

import com.bergerkiller.bukkit.tc.properties.IProperties;

/**
 * Argument passed to {@link PropertyFormatter} annotated methods
 * to perform parsing. Instead of this class a String
 * class can be used as well.
 */
public class PropertyFormatContext extends PropertyContext {

    public PropertyFormatContext(IProperties properties) {
        super(properties);
    }
}
