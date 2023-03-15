package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.google.common.collect.MapMaker;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Base implementation of a YAML configuration store that supports
 * modules to store the data across multiple files. This includes
 * support for read-only access to YAML containers.<br>
 * <br>
 * Each entry is mapped to its key in the YAML configuration structure.
 * The keys (names) are the unique identifier of an entry.<br>
 * <br>
 * If multiple modules store the same configuration, then the first
 * encountered module is dominating. Removing these Entries will
 * migrate it to the next priority module until it no longer exists
 * in any module, or the current module is read-only.<br>
 * <br>
 * Changes made to an entry's configuration automatically update
 * the configuration in the file Module that is providing it.
 * This also facilitates auto-saving of updated configurations.
 *
 * @param <T> Type of object stored the configuration is for
 */
public abstract class ModularConfiguration<T>
        extends ModularConfigurationBlockList<T>
        implements ModularConfigurationEntry.Container<T>
{
    final Logger logger;
    /** All entries that are still backed by a Module */
    final ModularConfigurationEntryMap<T> entries = new ModularConfigurationEntryMap<T>();
    /** All entries that have change listeners. Prevents them from being garbage collected. */
    final Set<ModularConfigurationEntry<T>> entriesWithListeners = new HashSet<>();
    /** Stores removed Entries until they get garbage-collected because nobody uses them */
    final ConcurrentMap<String, ModularConfigurationEntry<T>> removedEntries = new MapMaker()
            .weakValues()
            .makeMap();
    /** Temporarily stores entry changes that have occurred while changes are frozen */
    Map<String, FrozenEntryChanges<T>> cachedChanges = null; // null if not frozen

    /**
     * Initializes a new modular configuration
     *
     * @param logger Logger used for loading warnings and errors
     */
    public ModularConfiguration(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ModularConfiguration<T> getMain() {
        return this;
    }

    /**
     * Can be overridden to pre-process the full configuration of a module.
     * In here any sort of data migration or validation can be performed.<br>
     * <br>
     * This is called before any module entries are decoded.
     *
     * @param moduleConfig Module configuration
     */
    protected void preProcessModuleConfiguration(ConfigurationNode moduleConfig) {
    }

    /**
     * Can be overridden to post-process configurations of entries after their
     * configurations are updated. Is not called for entries that have been
     * removed.
     *
     * @param entry Entry whose configuration changed
     */
    protected void postProcessEntryConfiguration(ModularConfigurationEntry<T> entry) {
    }

    /**
     * Should be implemented to decode a single entry stored in
     * a module. The method should return the final object accessible
     * through the API.<br>
     * <br>
     * Please note that this method is called lazily, that is, only once
     * the entry's information is actually asked. After that the decoded
     * information will remain cached.<br>
     * <br>
     * It is acceptable, if not recommended, that the input entry is stored
     * directly inside the returned object. That way the returned object
     * can register change notifiers and directly rename or migrate the
     * underlying configuration between modules.
     *
     * @param entry The entry to decode. Will have the raw configuration and
     *              the module information already available.
     * @return Decoded representation
     * @see ModularConfigurationEntry#get()
     */
    protected abstract T decodeConfig(ModularConfigurationEntry<T> entry);

    /**
     * Gets the default writable configuration module where new entries are
     * added if no explicit module is specified. This module is also used
     * when trying to modify an entry that is inside a read-only module.
     *
     * @return default writable module
     */
    public abstract ModularConfigurationModule<T> getDefaultModule();

    /**
     * Called when a new module is added to this modular configuration. Will
     * register the entries found inside into this instance. It's important
     * that this module is already registered inside the base block list
     * somewhere before this method is called.
     *
     * @param module ModuleConfiguration that was added
     */
    void onModuleAdded(final ModularConfigurationModule<T> module) {
        for (ModularConfigurationEntry<T> newEntry : module.getAll()) {
            ModularConfigurationEntry<T> existing = entries.getIfExists(newEntry.getName());
            if (existing == null) {
                existing = removedEntries.remove(newEntry.getName());
            }

            // Directly store the module's entry. No change notification as there's
            // no listeners registered yet.
            if (existing == null) {
                this.entries.set(newEntry.getName(), newEntry);
                continue;
            }

            // Check whether the existing entry's module overrides the module that
            // we are adding to this store. If so, the entry isn't loaded.
            // Instead it's added to the "shadow modules" - a list modules are taken
            // from when the entry is removed from higher-priority modules.
            if (!existing.isRemoved() && !isModuleOverriding(module, existing.getModule())) {
                int index = 0;
                while (index < existing.shadowModules.size() &&
                       isModuleOverriding(module, existing.shadowModules.get(index)))
                {
                    index++;
                }
                existing.shadowModules.add(index, module);
                continue;
            }

            // Swap the entry for the existing one
            try (ModularConfigurationEntry.ChangeTracker t = existing.makeChanges()) {
                existing.loadFromModule(module);
            }
        }
    }

    /**
     * Called when a previously added module was removed from this modular configuration.
     * It will check what entries were defined by the module, and if not overridden,
     * will notify its removal.
     *
     * @param module ModuleConfiguration that was removed
     */
    void onModuleRemoved(ModularConfigurationModule<T> module) {
        for (String name : module.getNames()) {
            ModularConfigurationEntry<T> e = getIfExists(name);
            if (e != null) {
                e.onModuleRemoved(module);
            }
        }
    }

    /**
     * Checks whether one module configuration overrides the entries found inside another
     *
     * @param module ModuleConfiguration
     * @param otherModule ModuleConfiguration
     * @return True if <i>fileModule</i> overrides <i>otherModule</i>
     */
    private boolean isModuleOverriding(
            final ModularConfigurationModule<T> module,
            final ModularConfigurationModule<T> otherModule
    ) {
        for (ModularConfigurationBlock<T> block : blocks) {
            for (ModularConfigurationModule<T> existingModule : block.getFiles()) {
                if (existingModule == module) {
                    return true;
                } else if (existingModule == otherModule) {
                    return false;
                }
            }
        }
        return false; // Neither are stored. Weird!
    }

    @Override
    public void clear() {
        List<ModularConfigurationEntry<T>> newRemovedEntries = entries.getAll();

        // Before making changes, store a snapshot in case changes are frozen
        // Modify all of them but don't yet notify the changes
        newRemovedEntries.forEach(ModularConfigurationEntry::removeSilent);

        // Delete references to all modules and entries
        entries.clear();
        blocks.clear();

        // Keep removed entries in the removed mapping in case they're still referenced
        newRemovedEntries.forEach(e -> removedEntries.put(e.getName(), e));

        // Notify changes, probably does nothing
        newRemovedEntries.forEach(ModularConfigurationEntry::afterChanges);
    }

    /**
     * Groups the changes performed inside the action specified. If multiple changes occur
     * that cancel each other out, such as the removal and re-creation of an entry with
     * the same configuration, those changes are not notified.
     *
     * @param action Action that will cause lots of changes to happen
     */
    public void groupChanges(Runnable action) {
        if (cachedChanges == null) {
            // Start tracking
            cachedChanges = new LinkedHashMap<>();
            try {
                // Make changes
                action.run();
            } finally {
                // Apply changes
                Collection<FrozenEntryChanges<T>> changes = cachedChanges.values();
                cachedChanges = null;
                changes.stream()
                        .filter(FrozenEntryChanges::hasChanged)
                        .forEachOrdered(c -> c.entry.afterChanges());
            }
        } else {
            // Recursive groupChanges()
            action.run();
        }
    }

    /**
     * Looks up an entry by name.<br>
     * <br>
     * If the entry does not exist in this modular configuration,
     * a temporary entry is created with this name where
     * {@link ModularConfigurationEntry#isRemoved() isRemoved()}
     * returns true. Listeners can be registered on removed Entries,
     * who will be notified once and if the entry is later added.
     *
     * @param name Name for the entry
     * @return Existing entry, or a temporary entry that allows listeners
     *         to be registered notified when the entry is later created.
     */
    public ModularConfigurationEntry<T> get(String name) {
        ModularConfigurationEntry<T> e = entries.getIfExists(name);
        return (e != null) ? e : removedEntries.computeIfAbsent(name,
                n -> new ModularConfigurationEntry<T>(this, n));
    }

    @Override
    public ModularConfigurationEntry<T> getIfExists(String name) {
        return entries.getIfExists(name);
    }

    /**
     * Updates or adds the configuration for an entry. If the entry already exists but
     * is stored in a read-only module, then the entry is migrated to the main writable
     * module. If it does not yet exist, a new one is created inside the main writable
     * module. If one already exists in a certain module, the configuration in that
     * module is updated.
     *
     * @param name Name of the entry
     * @param config Configuration to store for the (new) entry
     * @return ModularConfigurationEntry of the entry modified
     */
    @Override
    public ModularConfigurationEntry<T> add(
            final String name,
            final ConfigurationNode config)
    {
        ModularConfigurationEntry<T> entry = get(name);
        try {
            if (entry.isReadOnly()) {
                return getDefaultModule().add(name, config);
            } else {
                entry.setConfig(config);
                return entry;
            }
        } catch (ReadOnlyModuleException ex) {
            throw new IllegalStateException("Unexpected read-only module exception", ex);
        }
    }

    @Override
    public boolean rename(String name, String newName) {
        // No change?
        if (name.equals(newName)) {
            return true;
        }

        // Get the entry to be renamed
        ModularConfigurationEntry<T> entry = get(name);
        if (entry.isRemoved()) {
            return false;
        }

        // If the entry is stored in a writable module, put the
        // renamed entry in there too. Then remove the original entry.
        ModularConfigurationEntry<T> target = get(newName);
        if (!entry.isReadOnly()) {
            target.createWithConfigInModule(entry.getConfig(), entry.getModule());
            entry.remove();
            return true;
        }

        // If the entry by the new name does not exist, or its module is
        // read-only, add the (new) entry to the DEFAULT module.
        // Otherwise, update the entry directly without moving it to a
        // different module first.
        if (target.isReadOnly()) {
            target.createWithConfigInModule(entry.getConfig(), getDefaultModule());
        } else {
            target.setConfig(entry.getConfig());
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public String getName() {
        return getDefaultModule().getName();
    }

    @Override
    public List<String> getNames() {
        return entries.getNames();
    }

    @Override
    public List<ModularConfigurationEntry<T>> getAll() {
        return entries.getAll();
    }

    /**
     * Temporarily stores the state of an entry to detect when changes have
     * occurred to the configuration.
     */
    static class FrozenEntryChanges<T> {
        private final ModularConfigurationEntry<T> entry;
        private final boolean removed;
        private final ConfigurationNode config;

        public FrozenEntryChanges(ModularConfigurationEntry<T> entry) {
            this.entry = entry;
            this.removed = entry.isRemoved();
            this.config = entry.getConfig().clone();
        }

        /**
         * Checks that the before-state described here is different from the
         * current state, implying a change has occurred.
         *
         * @return True if the entry actually changed
         */
        public boolean hasChanged() {
            // Changes to isRemoved()
            if (removed != entry.isRemoved()) {
                return true;
            }

            // Changes to the configuration
            if (!entry.isRemoved() && !entry.getConfig().equals(config)) {
                return true;
            }

            return false;
        }
    }
}
