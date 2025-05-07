package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.CommonEntity;

/**
 * A vanilla attribute of a Minecart Entity that is persisted when the train is saved
 * and re-spawned.
 */
public interface PersistentCartAttribute<E extends CommonEntity<?>> {
    /**
     * Writes the entity attribute to the YAML 'data' entry
     *
     * @param entity Entity being saved
     * @param data Data YAML ConfigurationNode to write to
     */
    void save(E entity, ConfigurationNode data);

    /**
     * Restores a previously saved entity attribute from the YAML 'data' entry
     *
     * @param entity Entity loading the data back in (being spawned)
     * @param data Data YAML ConfigurationNode to read from
     */
    void load(E entity, ConfigurationNode data);
}
