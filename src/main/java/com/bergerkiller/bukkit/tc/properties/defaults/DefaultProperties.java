package com.bergerkiller.bukkit.tc.properties.defaults;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Caches the information of default property values. These can be applied to a train
 * in an efficient manner, or differences between the defaults and those of a train/cart
 * configuration can be detected.
 */
public class DefaultProperties {
    private final ConfigurationNode config;
    private final List<DefaultProperty<?>> properties;
    private final List<DefaultProperty<?>> propertiesWithValues;

    /**
     * Generates the default properties using the configuration specified
     *
     * @param defaultConfig Default Configuration to parse
     * @return new DefaultProperties representation
     */
    public static DefaultProperties of(ConfigurationNode defaultConfig) {
        return new DefaultProperties(defaultConfig);
    }

    private DefaultProperties(ConfigurationNode config) {
        Collection<IProperty<Object>> registeredProperties = IPropertyRegistry.instance().all();

        this.config = config;
        this.properties = new ArrayList<>(registeredProperties.size());
        this.propertiesWithValues = new ArrayList<>(registeredProperties.size());
        for (IProperty<?> property : registeredProperties) {
            if (property.isAppliedAsDefault()) {
                DefaultProperty<?> defaultProperty = new DefaultProperty<>(property, config);
                this.properties.add(defaultProperty);
                if (defaultProperty.set) {
                    this.propertiesWithValues.add(defaultProperty);
                }
            }
        }
    }

    /**
     * Gets the underlying YAML configuration of these default properties
     *
     * @return default YAML configuration
     */
    public ConfigurationNode getConfig() {
        return config;
    }

    /**
     * Applies these default properties to a train and all its carts.
     *
     * @param properties Train Properties to apply these default properties to
     */
    public void applyTo(TrainProperties properties) {
        // Read all properties TrainCarts knows about from the configuration
        // This will read and apply both train and cart properties
        for (DefaultProperty<?> defaultProperty : propertiesWithValues) {
            defaultProperty.applyTo(properties);
        }

        // Fire onPropertiesChanged, if possible
        properties.tryUpdate();
        for (CartProperties prop : properties) {
            prop.tryUpdate();
        }
    }

    /**
     * Applies these default properties to a cart. Note that if the default includes
     * train properties, the train is updated too.
     *
     * @param properties Cart Properties to apply these default properties to
     */
    public void applyTo(CartProperties properties) {
        // Read all properties TrainCarts knows about from the configuration
        // This will read and apply both train and cart properties
        for (DefaultProperty<?> defaultProperty : propertiesWithValues) {
            defaultProperty.applyTo(properties);
        }

        // Fire onPropertiesChanged, if possible
        properties.tryUpdate();
    }

    private static class DefaultProperty<T> {
        public final IProperty<T> property;
        public final boolean set;
        public final T value;

        public DefaultProperty(IProperty<T> property, ConfigurationNode config) {
            this.property = property;

            Optional<T> valueOpt = property.readFromConfig(config);
            this.set = valueOpt.isPresent();
            this.value = valueOpt.orElse(property.getDefault());
        }

        public void applyTo(IProperties properties) {
            properties.set(property, value);
        }
    }
}
