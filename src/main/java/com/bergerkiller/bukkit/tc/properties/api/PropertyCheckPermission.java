package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be put on annotated command method handlers
 * to run a permission check before executing the handler.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyCheckPermission {

    /**
     * Name of the property being modified by the command,
     * which matches syntax of the parsers.
     * 
     * @return property name
     */
    String value();
}
