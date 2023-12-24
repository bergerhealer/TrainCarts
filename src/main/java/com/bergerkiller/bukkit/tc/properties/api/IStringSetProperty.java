package com.bergerkiller.bukkit.tc.properties.api;

import java.util.Set;

/**
 * Properties that are a Set of strings. These are special in how values
 * can be matched against match expressions. Interface is primarily
 * a marker interface for use with the property input function.
 */
public interface IStringSetProperty extends IProperty<Set<String>> {
}
