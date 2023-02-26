package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

/**
 * Listener notified of changes made to entries
 *
 * @param <T> Type of object stored the configuration is for
 */
@FunctionalInterface
public interface ModularConfigurationChangeListener<T> {
    /**
     * Called when the entry this listener was registered for is changed.
     * A change includes fundamental changes to the configuration, and
     * entry removal (configuration cleared)
     *
     * @param entry entry that changed
     */
    void onChanged(ModularConfigurationEntry<T> entry);
}
