package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A single configuration entry including information about the
 * module it is stored inside. The entry and Name are rightly coupled,
 * so renaming an entry will result in a new entry being created
 * and the old one being removed.
 *
 * @param <T> Type of object stored the configuration is for
 */
public class ModularConfigurationEntry<T> implements Comparable<ModularConfigurationEntry<T>> {
    private final ModularConfiguration<T> main;
    ModularConfigurationModule<T> module;
    final List<ModularConfigurationModule<T>> shadowModules;
    private final String name;
    private final ConfigurationNode config;
    private T cachedValue;

    ModularConfigurationEntry(ModularConfiguration<T> main, String name) {
        this(main, name, new ConfigurationNode(), null);
    }

    ModularConfigurationEntry(ModularConfiguration<T> main, String name, ConfigurationNode config, ModularConfigurationModule<T> module) {
        this.main = main;
        this.module = module;
        this.shadowModules = new ArrayList<>(2);
        this.name = name;
        this.config = config;
        this.cachedValue = null;
    }

    /**
     * Gets the decoded Value this entry represents. Is lazily created.
     *
     * @return Value of this entry
     * @see ModularConfiguration#decodeConfig(ModularConfigurationEntry)
     */
    public T get() {
        T value = this.cachedValue;
        if (value == null) {
            this.cachedValue = value = main.decodeConfig(this);
        }
        return value;
    }

    /**
     * Gets the main {@link ModularConfiguration} this entry is part of
     *
     * @return main module configuration
     */
    public ModularConfiguration<T> getMain() {
        return main;
    }

    /**
     * Gets the Module this entry is part of
     *
     * @return module
     */
    public ModularConfigurationModule<T> getModule() {
        return module;
    }

    /**
     * Gets whether this entry was removed from all modules. If removed,
     * operations like {@link #setConfig(ConfigurationNode)} and
     * {@link #setModule(ModularConfigurationModule)} will no longer
     * function.<br>
     * <br>
     * To create the entry, use either
     * {@link #createWithConfigInModule(ConfigurationNode, ModularConfigurationModule) createWithConfigInModule(config, module)}
     * or {@link ModularConfigurationModule#add(String, ConfigurationNode) ModularConfigurationModule.add(name, config)}
     *
     * @return True if this entry was removed
     */
    public boolean isRemoved() {
        return module == null;
    }

    /**
     * Gets whether this entry is read-only. These are entries inside a
     * read-only {@link #getModule() module}, or those that have been
     * {@link #isRemoved() removed}.
     *
     * @return True if read-only
     */
    public boolean isReadOnly() {
        return module == null || module.isReadOnly();
    }

    /**
     * Gets the name of this entry
     *
     * @return entry name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the configuration of this entry. Is empty if removed. The
     * returned instance will never be changed, and so it is safe to
     * register configuration change listeners on it. Those will remain
     * functional even when the modules are reloaded.<br>
     * <br>
     * If the entry was removed and is later created, then the same
     * configuration instance is re-used and populated with the new configuration.
     * Change listeners can in this way be notified of this.
     *
     * @return configuration
     */
    public ConfigurationNode getConfig() {
        return config;
    }

    /**
     * Gets the configuration of this entry. Throws an error if the
     * entry is removed, and the configuration cannot be written to. The
     * returned instance will never be changed, and so it is safe to
     * register configuration change listeners on it. Those will remain
     * functional even when the modules are reloaded.
     *
     * @return writable configuration
     * @throws EntryRemovedException If this entry is {@link #isRemoved()} removed
     */
    public ConfigurationNode getWritableConfig() {
        if (isRemoved()) {
            throw new EntryRemovedException();
        }
        return config;
    }

    /**
     * Updates the configuration of this entry. Listeners registered on
     * this entry's configuration will be notified of these changes.
     * The input configuration is copied, no reference to it is stored.
     *
     * @param config Configuration to set to
     * @throws ReadOnlyModuleException If this entry is currently stored
     *                                 inside a read-only module
     * @throws EntryRemovedException If this entry was removed and cannot
     *                               have its configuration updated.
     */
    public void setConfig(ConfigurationNode config) throws ReadOnlyModuleException {
        if (this.isRemoved()) {
            throw new EntryRemovedException();
        } else if (this.module.isReadOnly()) {
            throw new ReadOnlyModuleException();
        }

        this.config.setTo(config);
        this.main.postProcessEntryConfiguration(this);
    }

    /**
     * Updates the configuration of this entry. Listeners registered on
     * this entry's configuration will be notified of these changes.
     * The input configuration is copied, no reference to it is stored.<br>
     * <br>
     * In addition to updating the configuration, will also migrate the entry
     * to be stored in the module specified. If the entry was
     * {@link #isRemoved() removed}, then it is created as a new entry in this
     * module.
     *
     * @param config Configuration to set to
     * @param module Module to store the entry's configuration inside of
     * @see #setConfig(ConfigurationNode) 
     * @see #setModule(ModularConfigurationModule)
     * @see ModularConfigurationModule#add(String, ConfigurationNode)
     * @throws ReadOnlyModuleException If the module specified is read-only
     */
    public void createWithConfigInModule(
            final ConfigurationNode config,
            final ModularConfigurationModule<T> module) throws ReadOnlyModuleException
    {
        if (module.isReadOnly()) {
            throw new ReadOnlyModuleException();
        }

        if (isRemoved()) {
            // Add this entry to this module
            module.store(this);

            // Also create in the main module
            main.removedEntries.remove(name);
            main.entries.set(name, this);
        } else {
            setModule(module);
        }

        this.config.setTo(config);
        this.main.postProcessEntryConfiguration(this);
    }

    /**
     * Attempts to remove this entry. If the entry exists inside a
     * read-only module, and the current module is shadowing it, then
     * the entry configuration from the read-only module is restored.
     * As such, {@link #isRemoved()} might not return true after
     * removal succeeds.<br>
     * <br>
     * To avoid any error being thrown, check {@link #isReadOnly()}
     * before calling this method.
     *
     * @throws ReadOnlyModuleException If this entry is currently stored
     *                                 inside a read-only module
     * @throws EntryRemovedException If this entry was removed from all
     *                               modules already.
     */
    public void remove() throws ReadOnlyModuleException {
        if (this.isRemoved()) {
            throw new EntryRemovedException();
        } else if (this.module.isReadOnly()) {
            throw new ReadOnlyModuleException();
        }

        // Remove the configuration from the current module
        module.removeInModule(name);
        module = null;

        // Handle the file module being detached
        onFileModuleDetached();
    }

    /**
     * Called when a module this entry might be using was removed/unloaded.
     * If this entry was currently declared inside the module, then it
     * falls back on another module it shadowed if possible. Otherwise, it
     * marks itself as removed.
     *
     * @param module ModuleConfiguration that was removed
     */
    void onModuleRemoved(ModularConfigurationModule<T> module) {
        if (this.module == module) {
            this.module = null;
            this.onFileModuleDetached();
        } else {
            this.shadowModules.remove(module);
        }
    }

    /**
     * Called after this entry was detached from the current file module.
     * If another file module is available that it shadowed, that one's
     * configuration is loaded in. If not, this entry is marked as removed
     * in the modular configuration and the configuration is cleared.
     */
    private void onFileModuleDetached() {
        // If a shadow (read-only) module exists, restore that module's
        // entry and set this entry to it.
        //
        // If removed completely, also remove it from the main module itself.
        // If nobody references this entry anymore then the entry is automatically
        // garbage-collected.
        if (!shadowModules.isEmpty()) {
            loadFromModule(shadowModules.remove(shadowModules.size() - 1));
        } else {
            config.clear();
            main.entries.remove(name);
            main.removedEntries.put(name, this);
        }
    }

    /**
     * Moves this entry to a different module. Does nothing if this entry
     * is already inside this module. The new module must be writable
     * and be part of the same {@link ModularConfiguration}<br>
     * <br>
     * If the entry is currently stored inside a read-only Module, then a
     * shadow copy is created. The original configuration will still be
     * available in the original read-only module, but only made visible
     * again once this Entry is removed.
     *
     * @param module New module to store the entry in
     * @throws IllegalArgumentException If the module is null or the module
     *                                  isn't part of the same modular configuration
     * @throws ReadOnlyModuleException If the new module is read-only
     * @throws EntryRemovedException If this entry was removed from all
     *                               modules and has no linkage
     */
    public void setModule(ModularConfigurationModule<T> module) throws ReadOnlyModuleException {
        // Validation
        if (this.module == module) {
            return;
        } else if (this.isRemoved()) {
            throw new EntryRemovedException();
        } else if (module == null) {
            throw new IllegalArgumentException("Module is null");
        } else if (main != module.getMain()) {
            throw new IllegalArgumentException("Module is in a different modular configuration");
        } else if (module.isReadOnly()) {
            throw new ReadOnlyModuleException();
        }

        // If the old module is writable, remove it from there
        // If not, store it as a shadow module where the entry will
        // be moved to when 'deleted'.
        if (this.module.isReadOnly()) {
            this.shadowModules.add(this.module);
            detachAsShadowCopy();
        } else {
            this.module.removeInModule(name);
        }

        // Assign this entry to the new Module
        this.shadowModules.remove(module);
        module.store(this);
    }

    /**
     * Copies the configuration of this entry into a different entry
     * with a different name. Configuration change listeners are not
     * transferred to the other entry.
     *
     * @param targetEntry Other entries to copy this entry's configuration to
     * @throws IllegalArgumentException If the target entry is null or the
     *                                  target entry isn't part of the same modular
     *                                  configuration
     * @throws ReadOnlyModuleException If the target entry is inside a
     *                                 read-only module and can't be changed.
     * @throws EntryRemovedException If this entry or the target entry was removed
     *                               from all modules and has no linkage
     */
    public void copyTo(ModularConfigurationEntry<T> targetEntry) throws ReadOnlyModuleException {
        if (this == targetEntry) {
            return;
        } else if (targetEntry == null) {
            throw new IllegalArgumentException("Target entry is null");
        } else if (targetEntry.main != main) {
            throw new IllegalArgumentException("Target entry is in a different modular configuration");
        } else if (this.isRemoved() || targetEntry.isRemoved()) {
            throw new EntryRemovedException();
        } else if (targetEntry.module.isReadOnly()) {
            throw new ReadOnlyModuleException();
        }

        targetEntry.config.setTo(this.config);
        this.main.postProcessEntryConfiguration(targetEntry);
    }

    /**
     * Removes this entry without notifying the changes. Used by
     * {@link ModularConfiguration#clear()}
     */
    void removeSilent() {
        // Prevent modifying read-only File Configurations
        detachAsShadowCopy();

        shadowModules.clear();
        module = null;
        config.clear();
    }

    /**
     * Creates a read-only copy of this entry in the current module. This
     * entry's module is set to null to indicate its removal. Does nothing
     * if already detached.
     */
    void detachAsShadowCopy() {
        final ModularConfigurationModule<T> module = this.module;
        if (module != null) {
            boolean wasChanged = module.configChanged;
            module.store(new ModularConfigurationEntry<>(main, name, this.config.clone(), this.module));
            module.configChanged = wasChanged;

            this.module = null;
        }
    }

    /**
     * Loads a new configuration for this entry from a different Module.
     * This entry is assigned to the Module so that changes made to the
     * configuration are saved in the Module automatically.
     *
     * @param module Module to assign this entry to and load the entry
     *               configuration from.
     */
    void loadFromModule(ModularConfigurationModule<T> module) {
        if (this.module == module) {
            return;
        }

        boolean wasRemoved = isRemoved();
        if (!wasRemoved) {
            this.shadowModules.add(this.module);
            detachAsShadowCopy();
        }

        boolean wasChanged = module.configChanged;
        {
            this.config.setTo(module.config.getNode(name));
            module.store(this);
        }
        module.configChanged = wasChanged;

        // Add to the main store if it was previously removed
        if (wasRemoved) {
            main.removedEntries.remove(name);
            main.entries.set(name, this);
        }
    }

    @Override
    public int compareTo(@NotNull ModularConfigurationEntry<T> tModularConfigurationEntry) {
        return name.compareTo(tModularConfigurationEntry.name);
    }

    /**
     * Provides access to entries that are mapped by name
     *
     * @param <T> Type of object stored the configuration is for
     */
    public interface Container<T> {

        /**
         * Gets the unique name of this container.
         *
         * @return name
         */
        String getName();

        /**
         * Looks up an entry by name.<br>
         * <br>
         * If the entry does not exist, <i>null</i> is returned.
         *
         * @param name Name for the entry
         * @return Existing entry, or <i>null</i> if it does not exist
         */
        ModularConfigurationEntry<T> getIfExists(String name);

        /**
         * Creates this entry in this configuration module. If this entry was
         * already assigned to a (different) module, then
         * {@link ModularConfigurationEntry#setModule(ModularConfigurationModule)}
         * is called to migrate it.
         *
         * @param name Name of the entry to add/create inside this module
         * @param initialConfig Initial configuration to set for the added entry
         * @throws IllegalArgumentException If the name is null or empty, or the
         *                                  initialConfig is null
         * @throws ReadOnlyModuleException If this module is read-only
         */
        ModularConfigurationEntry<T> add(
                final String name,
                final ConfigurationNode initialConfig
        ) throws ReadOnlyModuleException;

        /**
         * Tries to remove an entry from this module. If the entry does not exist,
         * or exists in a different module, returns <i>null</i>.
         *
         * @param name Name of the entry to find and remove
         * @return removed entry, or null if removing wasn't successful
         * @throws ReadOnlyModuleException If the entry exists inside a read-only
         *                                 module and can't be removed
         */
        default ModularConfigurationEntry<T> remove(final String name) throws ReadOnlyModuleException {
            ModularConfigurationEntry<T> entry = getIfExists(name);
            if (entry == null) {
                return null;
            } else {
                entry.remove();
                return entry;
            }
        }

        /**
         * Renames an entry. This is done by copying the configuration to a new name,
         * then deleting the original entry. If the original entry is read-only,
         * then it is not removed and only a copy is created.
         *
         * @param name Name of the entry to rename
         * @param newName New name to copy the entry to
         * @return true if successful (found, and the renaming is possible)
         */
        boolean rename(String name, String newName);

        /**
         * Gets an unmodifiable snapshot copy of all the names of all the entries
         * that exist inside this modular configuration. The names are sorted
         * alphabetically. The returned List cannot be modified.
         *
         * @return Unmodifiable List of entry names, sorted alphabetically
         */
        List<String> getNames();

        /**
         * Gets an unmodifiable snapshot copy of all the entries that exist inside
         * this modular configuration. The entries are sorted alphabetically
         * by name. The returned List cannot be modified.
         *
         * @return Unmodifiable name-sorted List of all modular configuration entries
         */
        List<ModularConfigurationEntry<T>> getAll();

        /**
         * Gets whether this module does not store any entries
         *
         * @return True if this module is empty
         */
        boolean isEmpty();
    }
}
