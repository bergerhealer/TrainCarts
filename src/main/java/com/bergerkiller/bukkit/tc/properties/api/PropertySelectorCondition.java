package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be put on a method to declare it as a
 * selector condition checking method. It will become available
 * as part of the selector conditions in @train and @ptrain.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PropertySelectorConditionList.class)
public @interface PropertySelectorCondition {
    /**
     * Name of the condition
     */
    String value();
}
