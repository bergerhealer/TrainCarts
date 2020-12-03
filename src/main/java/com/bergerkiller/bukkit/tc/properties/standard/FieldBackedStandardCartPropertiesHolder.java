package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Set;

import org.bukkit.Material;

import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

/**
 * Holds a <b>copy</b> of the cart properties stored in YAML configuration
 * for faster access at runtime. Properties that aren't accessed
 * very often aren't stored here.
 */
public class FieldBackedStandardCartPropertiesHolder {
    SignSkipOptions signSkipOptionsData;
    Set<String> tags;
    Set<String> owners;
    Set<String> ownerPermissions;
    Set<Material> blockBreakTypes;
    boolean pickUpItems;
    boolean isPublic;
}
