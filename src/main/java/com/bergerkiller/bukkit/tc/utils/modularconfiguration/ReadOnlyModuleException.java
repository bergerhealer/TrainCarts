package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

/**
 * Exception thrown when a Module or entry inside a Module is modified that
 * does not allow for modification.
 */
public class ReadOnlyModuleException extends UnsupportedOperationException {

    public ReadOnlyModuleException() {
        super("This module is read-only");
    }
}
