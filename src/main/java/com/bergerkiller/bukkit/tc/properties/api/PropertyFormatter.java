package com.bergerkiller.bukkit.tc.properties.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a member method of an {@link IProperty} as being
 * a property formatter. A formatter method is used to turn a
 * compound property value into a single number, boolean or String.<br>
 * <br>
 * When no property formatter methods are declared, names used on
 * declared parsers are used instead. If at least one property formatter
 * method is declared, then none of the parsers are used.<br>
 * <br>
 * If the <i>get(prop)</i> method of the property already returns
 * this, then there is no need to declare an additional formatter.
 * In that case, the name(s) used by declared {@link PropertyParser}
 * methods can be used, instead.<br>
 * <br>
 * The method must not be static, must use a single
 * {@link PropertyFormatContext} as argument,
 * and must return the value result.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyFormatter {

    /**
     * Defines the name of the property that, if retrieved, will
     * result in this method being called.
     *
     * @return property name
     */
    String value();
}
