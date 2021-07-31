package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * List of {@link PropertySelectorCondition} conditions.
 * Each name will be registered.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertySelectorConditionList {
    PropertySelectorCondition[] value();
}
