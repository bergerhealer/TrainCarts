package com.bergerkiller.bukkit.tc.commands.annotations;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.parser.ParserParameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that if a saved attachment model by this name does not exist,
 * a new one should be created.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SavedModelImplicitlyCreated {
    public static final ParserParameter<Boolean> PARAM = new ParserParameter<Boolean>(
            "savedmodel.implicitlycreated", TypeToken.get(Boolean.class));
}
