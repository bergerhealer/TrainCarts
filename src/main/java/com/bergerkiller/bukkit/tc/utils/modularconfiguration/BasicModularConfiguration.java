package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * A base implementation of {@link ModularConfiguration} that supports three
 * locations for storing data:
 * <ul>
 *     <li>A single default writable YAML file</li>
 *     <li>A directory of named read-only/writable modules</li>
 *     <li>Read-only modules inside a folder inside the server-configured resource pack</li>
 * </ul>
 * Some convenience functions are added to easily add new entries or migrate
 * them between named modules.
 *
 * @param <T> Type of object stored the configuration is for
 */
public abstract class BasicModularConfiguration<T> extends ModularConfiguration<T> {
    /**
     * The default (main) YAML file module. New entries are added to this one
     * by default.
     */
    public final ModularConfigurationFile<T> DEFAULT;

    /**
     * A directory of YAML files. Entries can be assigned to a file module by name.
     */
    public final ModularConfigurationDirectory<T> MODULES;

    /**
     * Initializes a new BasicModularConfiguration. The main file path and module
     * directory path are used for both file-system paths (in the plugin's data
     * folder) and in for relative resource pack paths (in the resource pack
     * 'TrainCarts' folder)
     *
     * @param plugin TrainCarts plugin instance
     * @param mainFilePath Path to the main YAML file. Newly created entries are
     *                     added to this file by default.
     * @param moduleDirectoryPath Path to a directory storing multiple YAML files,
     *                            as 'modules'. Entries can be moved to these
     *                            modules.
     */
    public BasicModularConfiguration(
            final Plugin plugin,
            final String mainFilePath,
            final String moduleDirectoryPath
    ) {
        super(plugin.getLogger());

        File data = plugin.getDataFolder();

        // Main YAML file - highest priority
        this.DEFAULT = this.addFileModule("DEFAULT", new File(data, mainFilePath), false);
        // YAML files in the module directory path
        this.MODULES = this.addDirectoryModule(new File(data, moduleDirectoryPath));
        // Read-only YAML files found in the server resource pack
        // TODO: Add!
    }

    @Override
    public ModularConfigurationModule<T> getDefaultModule() {
        return DEFAULT;
    }

    /**
     * Gets or creates a new module to store entries inside. If the name specified
     * is null or empty, returns the {@link #DEFAULT} module.
     *
     * @param name Name of the module to get/create. If null or empty, returns
     *             the {@link #DEFAULT} module.
     * @return module
     */
    public ModularConfigurationModule<T> createModule(String name) {
        if (name == null || name.isEmpty()) {
            return DEFAULT;
        } else {
            return MODULES.createFile(name);
        }
    }

    /**
     * Updates or adds the configuration for an entry and assigns it to the module
     * by the name specified. If the module name is null, the entry is assigned to
     * the {@link #DEFAULT} module.
     *
     * @param name Name of the entry
     * @param config Configuration to store for the (new) entry
     * @param moduleName Name of the module to store in. <i>null</i> stores it in the
     *                   DEFAULT module.
     * @return ModularConfigurationEntry of the entry modified
     * @throws ReadOnlyModuleException If the module by the name specified is read-only
     */
    public ModularConfigurationEntry<T> add(
            final String name,
            final ConfigurationNode config,
            final String moduleName) throws ReadOnlyModuleException
    {
        ModularConfigurationEntry<T> entry = get(name);
        entry.createWithConfigInModule(config, createModule(moduleName));
        return entry;
    }
}
