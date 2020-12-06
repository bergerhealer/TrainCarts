package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a member method of an {@link IProperty} as being
 * a property parser. The regex defined by this annotation
 * is matched with the name of the property,
 * and when matched, the method is called to parse
 * the value into a new value for the property.<br>
 * <br>
 * By default the name matched is pre-processed, eliminating
 * extra spaces and converting the name to lower-case.
 * To disable this behavior, override {@link #preProcess()}
 * to return <i>false</i> instead.<br>
 * <br>
 * The method must not be static, must use either a single
 * {@link PropertyParseContext} or {@link String} as argument,
 * and must return the value result. Inside the method a
 * {@link PropertyInvalidInputException} can be thrown to
 * indicate a value cannot be parsed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyParser {

    /**
     * Defines the regex pattern to match the property name
     * against.
     * 
     * @return property name regex pattern
     */
    String value();

    /**
     * Whether to pre-process the property name to eliminate
     * surrounding whitespace and converting it to lower-case.
     * 
     * @return True to pre-process the property name, False to match it unchanged
     */
    boolean preProcess() default true;
}
