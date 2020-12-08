package com.bergerkiller.bukkit.tc.properties.api;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * A type of property which doesn't actually exist and isn't read
 * from or written to the configuration. Can be used to register
 * properties which only declare parsers, or for tracking when
 * train configuration changes.
 * 
 * @param <T> Property value type
 */
public interface ISyntheticProperty<T> extends IProperty<T> {

    @Override
    default Optional<T> readFromConfig(ConfigurationNode config) {
        return Optional.empty();
    }

    @Override
    default void writeToConfig(ConfigurationNode config, Optional<T> value) {
    }
}
