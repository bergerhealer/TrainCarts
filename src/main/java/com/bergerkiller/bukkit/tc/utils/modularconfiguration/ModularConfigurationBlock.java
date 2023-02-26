package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import java.util.List;

/**
 * A block of modules inside a configuration store. Can itself store modules,
 * or store a list of other modules.
 */
public interface ModularConfigurationBlock<T> {

    /**
     * Gets the main {@link ModularConfiguration} that is the root of this
     * configuration block.
     *
     * @return main modular configuration
     */
    ModularConfiguration<T> getMain();

    /**
     * Gets a List of module configurations that this block contains.
     * If this block is itself a module configuration, returns a singleton
     * list of itself.
     *
     * @return List of module configurations
     */
    List<? extends ModularConfigurationModule<T>> getFiles();

    /**
     * Reloads all the configurations stored inside this block. Will reload
     * the file contents from disk.
     */
    void reload();

    /**
     * Saves the underlying configurations to disk if changes have been made
     * to them. Saving is done asynchronously in the background. Ideally this
     * method is called periodically in an auto-save task.
     */
    void saveChanges();

    /**
     * Saves all underlying configurations to disk, no matter if changes were
     * made to them or not. Saving is done asynchronously in the background.
     */
    void save();
}
