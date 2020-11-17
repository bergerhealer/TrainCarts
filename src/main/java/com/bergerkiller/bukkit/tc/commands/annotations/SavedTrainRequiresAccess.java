package com.bergerkiller.bukkit.tc.commands.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import cloud.commandframework.arguments.parser.ParserParameter;
import io.leangen.geantyref.TypeToken;

/**
 * Declares that the command will require write access to the saved train.
 * If the sender has no such access, declines the argument.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SavedTrainRequiresAccess {
    public static final ParserParameter<Boolean> PARAM = new ParserParameter<Boolean>(
            "savedtrain.requiresaccess", TypeToken.get(Boolean.class));
}
