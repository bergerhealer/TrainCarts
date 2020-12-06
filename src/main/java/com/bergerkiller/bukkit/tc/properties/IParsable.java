package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;

public interface IParsable {
    /**
     * Sets a property denoted by the key by parsing the args specified
     *
     * @param key   of the property (lower-cased and trimmed of surrounding spaces)
     * @param args  to set to
     * @return True if something was set, False if not
     */
    @Deprecated
    default boolean parseSet(String key, String args) {
        return parseAndSet(key, args).isSuccessful();
    }

    /**
     * Parses the property by name and attempts to parse the property. If successful,
     * applies the parsed value to these properties.
     * 
     * @param <T> Type of value the property has
     * @param name Name of the property to parse
     * @param input Input value to parse
     * @return Result of parsing, if not successful, the property will not have been set.
     *         Is never null, if parsing fails the {@link PropertyParseResult#getReason()}
     *         can be checked.
     */
    PropertyParseResult<?> parseAndSet(String name, String input);
}
