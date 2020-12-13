package com.bergerkiller.bukkit.tc.exception.command;

/**
 * Exception thrown when a player has no permission to modify a given property
 */
public class NoPermissionForPropertyException extends RuntimeException {
    private static final long serialVersionUID = -39392068705811986L;
    private final String name;

    public NoPermissionForPropertyException(String name) {
        super("No permission to modify property '" + name + "'");
        this.name = name;
    }

    /**
     * Name of the property matched that was attempted to be modified
     * 
     * @return property matched name
     */
    public String getName() {
        return this.name;
    }
}
