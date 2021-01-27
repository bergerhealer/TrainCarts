package com.bergerkiller.bukkit.tc.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cloud.commandframework.arguments.parser.ParserParameter;
import io.leangen.geantyref.TypeToken;

/**
 * Declares that if a saved train by this name does not exist,
 * a new one should be created.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SavedTrainImplicitlyCreated {
    public static final ParserParameter<Boolean> PARAM = new ParserParameter<Boolean>(
            "savedtrain.implicitlycreated", TypeToken.get(Boolean.class));
}
