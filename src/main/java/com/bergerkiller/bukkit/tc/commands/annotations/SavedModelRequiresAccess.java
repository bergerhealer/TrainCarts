package com.bergerkiller.bukkit.tc.commands.annotations;

import cloud.commandframework.arguments.parser.ParserParameter;
import io.leangen.geantyref.TypeToken;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the command will require write access to the saved attachment model.
 * If the sender has no such access, declines the argument.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SavedModelRequiresAccess {
    public static final ParserParameter<Boolean> PARAM = new ParserParameter<Boolean>(
            "savedmodel.requiresaccess", TypeToken.get(Boolean.class));
}
