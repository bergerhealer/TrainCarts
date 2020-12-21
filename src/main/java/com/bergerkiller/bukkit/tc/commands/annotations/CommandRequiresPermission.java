package com.bergerkiller.bukkit.tc.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.bergerkiller.bukkit.tc.Permission;

/**
 * Declares a cloud-annotated method requires a given TrainCarts permission
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandRequiresPermission {

    /**
     * Permission to check
     * 
     * @return permission
     */
    Permission value();
}
