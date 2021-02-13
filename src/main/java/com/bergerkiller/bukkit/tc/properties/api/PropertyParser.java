package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;

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
 * By default when parsing properties for trains, the value
 * is parsed once and then applied to all carts of the train.
 * If the parse function should be executed and applied for
 * each cart individually instead, override
 * {@link #processPerCart()} to return <i>true</i> instead.<br>
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
     * against. Anchors are automatically included and do not
     * need to be specified.
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

    /**
     * Whether to process parsing of trains by processing each
     * cart individually, rather than parsing once for the entire train
     * and setting that value to all carts.
     * 
     * @return True to process per cart, False to parse once and set to all
     */
    boolean processPerCart() default false;
}
