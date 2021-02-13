package com.bergerkiller.bukkit.tc.properties.api.format;

/**
 * The value of a property, passed through a PropertyFormatter method.
 * Wraps the raw value, providing extra methods to stringify or compare
 * it.
 */
public interface PropertyFormattedValue {

    /**
     * Gets the text representation of this formatted value.
     * This is used when displaying the value somewhere.
     *
     * @return display text
     */
    String getDisplayText();
}
