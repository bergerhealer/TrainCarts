package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.bergerkiller.bukkit.common.collections.StringMapCaseInsensitive;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Globally registers different available attachment types.
 */
public class AttachmentTypeRegistry {
    private final Map<String, AttachmentType> _types = new StringMapCaseInsensitive<AttachmentType>();
    private static final AttachmentTypeRegistry _instance = new AttachmentTypeRegistry();

    /**
     * Gets the global default instance of the attachment type registry.
     * Unless specified otherwise, this is what is used by {@link AttachmentManager}
     * 
     * @return attachment type registry
     */
    public static AttachmentTypeRegistry instance() {
        return _instance;
    }

    /**
     * Retrieves a list of all registered attachment types. The returned collection
     * is read-only and will not change as a result of new attachment types
     * being registered. It can be modified. By default the values
     * are sorted alphabetically by name.
     * 
     * @return collection of attachment types
     */
    public synchronized List<AttachmentType> all() {
        ArrayList<AttachmentType> result = new ArrayList<AttachmentType>(_types.values());
        Collections.sort(result, (t1, t2) -> {
            return t1.getName().compareTo(t2.getName());
        });
        return result;
    }

    /**
     * Retrieves the attachment type from the "type" field in the configuration.
     * Returns null if no valid attachment type could be decoded.
     * 
     * @param config
     * @return attachment type
     */
    public synchronized AttachmentType fromConfig(ConfigurationNode config) {
        return find(config.get("type", "EMPTY"));
    }

    /**
     * Saves the attachment type to the "type" field in the configuration.
     * 
     * @param config
     * @param type
     */
    public void toConfig(ConfigurationNode config, AttachmentType type) {
        config.set("type", type.getID());
    }

    /**
     * Generates the default configuration when creating a new attachment given a type
     * 
     * @param config
     * @param type
     */
    public void toDefaultConfig(ConfigurationNode config, AttachmentType type) {
        toConfig(config, type);
        type.getDefaultConfig(config);
    }

    /**
     * Finds an attachment type by its type ID
     * 
     * @param id The ID of the attachment
     * @return attachment type, null if not available
     */
    public synchronized AttachmentType find(String id) {
        return _types.get(id);
    }

    /**
     * Registers an attachment type, so that a future {@link #find(id)} can find it.
     * 
     * @param type The attachment type to register
     */
    public synchronized void register(AttachmentType type) {
        _types.put(type.getID(), type);
        type.onRegister(this);
    }

    /**
     * Removes an attachment type from this registry.
     * 
     * @param type The attachment type to unregister
     */
    public synchronized void unregister(AttachmentType type) {
        AttachmentType removed = _types.remove(type.getID());
        if (removed != null) {
            if (removed != type) {
                _types.put(removed.getID(), removed);
            } else {
                removed.onUnregister(this);
            }
        }
    }

    /**
     * Clears all registered attachment types
     */
    public synchronized void unregisterAll() {
        List<AttachmentType> removed = new ArrayList<AttachmentType>(_types.values());
        _types.clear();
        for (AttachmentType removedType : removed) {
            removedType.onUnregister(this);
        }
    }
}
