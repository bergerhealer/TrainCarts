package com.bergerkiller.bukkit.tc.utils.modularconfiguration;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Stores all entries contained inside a modular configuration or single module.
 * Adds a mechanism to retrieve a name-sorted list of entries contained inside.
 * Internal implementation.
 *
 * @param <T> Type of object stored the configuration is for
 */
class ModularConfigurationEntryMap<T> implements ModularConfigurationEntry.Container<T> {
    private final HashMap<String, ModularConfigurationEntry<T>> entries = new HashMap<>();
    private List<ModularConfigurationEntry<T>> entriesList = Collections.emptyList();
    private List<String> entryNamesList = Collections.emptyList();

    public void clear() {
        entries.clear();
        entriesList = Collections.emptyList();
        entryNamesList = Collections.emptyList();
    }

    public void set(String name, ModularConfigurationEntry<T> entry) {
        entries.put(name, entry);
        regenSortedLists();
    }

    public ModularConfigurationEntry<T> remove(String name) {
        ModularConfigurationEntry<T> entry = entries.remove(name);
        if (entry != null) {
            regenSortedLists();
        }
        return entry;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModularConfigurationEntry<T> getIfExists(String name) {
        return entries.get(name);
    }

    @Override
    public ModularConfigurationEntry<T> add(String name, ConfigurationNode initialConfig) throws ReadOnlyModuleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rename(String name, String newName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public List<String> getNames() {
        List<String> result = entryNamesList;
        if (result == null) {
            List<ModularConfigurationEntry<T>> entries = getAll();
            result = new ArrayList<>(entries.size());
            for (ModularConfigurationEntry<T> entry : entries) {
                result.add(entry.getName());
            }
            entryNamesList = result = Collections.unmodifiableList(result);
        }
        return result;
    }

    @Override
    public List<ModularConfigurationEntry<T>> getAll() {
        List<ModularConfigurationEntry<T>> result = entriesList;
        if (result == null) {
            result = new ArrayList<>(entries.values());
            Collections.sort(result);
            entriesList = result = Collections.unmodifiableList(result);
        }
        return result;
    }

    private void regenSortedLists() {
        entriesList = null;
        entryNamesList = null;
    }
}
