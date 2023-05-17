package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

import java.util.Collections;
import java.util.List;

/**
 * A single module that stores multiple configurations for entries mapped
 * by name, using YAML configuration. The origin of the configuration
 * can be anything, but as such this base class doesn't support reloading
 * and saving by default.
 *
 * @param <T> Type of object stored the configuration is for
 */
public class ModularConfigurationModule<T> implements ModularConfigurationBlock<T>,
                                                      ModularConfigurationEntry.Container<T>,
                                                      Comparable<ModularConfigurationModule<T>>
{
    protected final ModularConfiguration<T> main;
    private final ModularConfigurationEntryMap<T> entries;
    protected final String name;
    final ConfigurationNode config;
    private final boolean readOnly;
    boolean configChanged;

    ModularConfigurationModule(ModularConfiguration<T> main, String name, ConfigurationNode config, boolean readOnly) {
        this.main = main;
        this.entries = new ModularConfigurationEntryMap<>();
        this.name = name;
        this.config = config;
        this.readOnly = readOnly;

        // Load all entries for the first time and start tracking changes
        if (!readOnly) {
            this.config.addChangeListener(p -> configChanged = true);
        }
        loadConfig();
    }

    /**
     * Called to (re-)load this configuration after changes have occurred to it
     * that aren't from this api. For file configuration, this is after the
     * file was changed.
     */
    protected void loadConfig() {
        // Pre-process the loaded configuration node
        configChanged = false;
        main.preProcessModuleConfiguration(config);
        saveChanges(); // Save changes introduced above right away

        // Every key turns into an entry
        this.entries.clear();
        for (ConfigurationNode nodeConfig: config.getNodes()) {
            this.entries.set(nodeConfig.getName(), new ModularConfigurationEntry<T>(main, nodeConfig.getName(), nodeConfig, this));
        }

        // Make sure this is false after loading
        configChanged = false;
    }

    /**
     * Removes an entry and its associated configuration from this module
     *
     * @param name Name of the entry to remove
     */
    void removeInModule(String name) {
        entries.remove(name);
        config.remove(name);
    }

    /**
     * Stores an entry inside this module. Both entry and configuration are mapped
     *
     * @param entry ModularConfigurationEntry to store
     */
    void store(ModularConfigurationEntry<T> entry) {
        entry.module = this;
        entries.set(entry.getName(), entry);
        config.set(entry.getName(), entry.getConfig());
        configChanged = true;
    }

    /**
     * Gets the unique name of this Module.
     *
     * @return name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets whether this Module is read-only. Entries inside a read-only
     * module cannot be modified without first copying it to a writeable
     * module.
     *
     * @return True if read-only
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public ModularConfiguration<T> getMain() {
        return main;
    }

    @Override
    public List<? extends ModularConfigurationModule<T>> getFiles() {
        return Collections.singletonList(this);
    }

    @Override
    public void reload() {
    }

    @Override
    public void saveChanges() {
        configChanged = false; // Meh.
    }

    @Override
    public void save() {
        configChanged = false; // Meh.
    }

    @Override
    public ModularConfigurationEntry<T> add(
            final String name,
            final ConfigurationNode initialConfig
    ) throws ReadOnlyModuleException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name is null or empty");
        } else if (initialConfig == null) {
            throw new IllegalArgumentException("Initial configuration is null");
        }

        ModularConfigurationEntry<T> entry = main.get(name);
        entry.createWithConfigInModule(initialConfig, this);
        return entry;
    }

    @Override
    public boolean rename(String name, String newName) {
        // No change?
        if (name.equals(newName)) {
            return true;
        }

        // Read-only can't rename
        if (isReadOnly()) {
            return false;
        }

        // Get the entry to be renamed. Must be this module.
        ModularConfigurationEntry<T> entry = main.getIfExists(name);
        if (entry.isRemoved() || entry.getModule() != this) {
            return false;
        }

        // Add a new entry with the new name to this module
        // If an existing entry existed, maybe not in this module,
        // moves it to this module
        this.add(newName, entry.getConfig());
        return true;
    }

    @Override
    public ModularConfigurationEntry<T> getIfExists(String name) {
        return entries.getIfExists(name);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public List<String> getNames() {
        return entries.getNames();
    }

    @Override
    public List<ModularConfigurationEntry<T>> getAll() {
        return entries.getAll();
    }

    @Override
    public List<T> getAllValues() {
        return entries.getAllValues();
    }

    @Override
    public int compareTo(ModularConfigurationModule<T> tModularConfigurationFile) {
        if (this.readOnly != tModularConfigurationFile.readOnly) {
            return this.readOnly ? 1 : -1;
        } else {
            return this.name.compareTo(tModularConfigurationFile.name);
        }
    }
}
