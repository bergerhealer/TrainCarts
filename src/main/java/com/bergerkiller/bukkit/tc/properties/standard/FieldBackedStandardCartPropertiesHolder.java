package com.bergerkiller.bukkit.tc.properties.standard;

import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

/**
 * Holds a <b>copy</b> of the cart properties stored in YAML configuration
 * for faster access at runtime. Properties that aren't accessed
 * very often aren't stored here.
 */
public class FieldBackedStandardCartPropertiesHolder {
    SignSkipOptions signSkipOptionsData;
}
