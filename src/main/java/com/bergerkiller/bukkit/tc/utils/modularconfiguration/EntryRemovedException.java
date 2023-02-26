package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

/**
 * Exception thrown when an operation is performed on an entry that was
 * removed from all modules and the operation cannot succeed because of that.
 * Callers should check {@link ModularConfigurationEntry#isRemoved()} before
 * performing illegal operations with them.
 */
public class EntryRemovedException extends UnsupportedOperationException {

    public EntryRemovedException() {
        super("Entry was removed from all modules");
    }
}
