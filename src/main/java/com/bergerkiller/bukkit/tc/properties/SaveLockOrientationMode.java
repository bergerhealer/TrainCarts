package com.bergerkiller.bukkit.tc.properties;

/**
 * A mode specified when saving a train, of whether or not to preserve the
 * lock the orientation of the train. If locked, then future saves will always
 * have the same train orientation.
 */
public enum SaveLockOrientationMode {
    /**
     * Lock orientation is disabled. If it was enabled before, this is disabled.
     */
    DISABLED,
    /**
     * Lock orientation is enabled. If it was not enabled before, locks flipped state.
     * If it was already enabled and the train is currently flipped, will save the
     * train flipped around.
     */
    ENABLED,
    /**
     * Same as {@link #ENABLED} but will override whatever the current locked orientation is
     * with the current train orientation.
     */
    ENABLED_OVERRIDE,
    /**
     * Locks orientation if the train has carts that use it currently, otherwise leaves disabled.
     * Will flip the saved configuration if the train is flipped according to past locked state.
     */
    AUTOMATIC
}
