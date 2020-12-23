package com.bergerkiller.bukkit.tc.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a command targets a train or cart of a train.
 * This adds extra flags at the end of the method to specify what
 * train or cart to target.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandTargetTrain {
}
