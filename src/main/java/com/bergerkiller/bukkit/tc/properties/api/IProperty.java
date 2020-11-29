package com.bergerkiller.bukkit.tc.properties.api;

import java.util.List;
import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Property that can be get or set for a cart or train.
 * 
 * @param <T> Property value type
 */
public interface IProperty<T> {

    /**
     * Gets the names by which this property gets matched when updating a property
     * by name. This is used on primarily Property Signs.
     * 
     * @return names of this property
     */
    List<String> getNames();

    /**
     * Gets the default value of this property. If the current value of the
     * property equals this value, then the property is not written to the
     * configuration. As well, this default is used during initialization
     * of new properties.
     * 
     * @return default property value
     */
    T getDefault();

    /**
     * Tries to to read the value of this property from a given YAML configuration.
     * The configuration could be of a train, cart or train default. If the value
     * is not stored in the configuration, {@link Optional#empty()} should be returned.<br>
     * <br>
     * If the property is unset because it is the default value, then empty needs to be
     * returned anyway. Applying the default in this situation is done automatically.
     * 
     * @param config YAML configuration from which to read
     * @return read value, or {@link Optional#empty()} if none was read
     */
    Optional<T> readFromConfig(ConfigurationNode config);

    /**
     * Updates the value of this property in the given YAML configuration.
     * The configuration could be of a train, cart or train default. If the value
     * is {@link Optional#isPresent()}, the value should be written to the configuration.
     * If it is not present, the relevant configuration entries need to be
     * removed from the configuration.<br>
     * <br>
     * This method is generally called with {@link Optional#empty()} when the current
     * value is the default one.
     * 
     * @param config YAML configuration to which to write
     * @param value The value to write when {@link Optional#isPresent()}, otherwise
     *              the property should be removed from the configuration node
     */
    void writeToConfig(ConfigurationNode config, Optional<T> value);

    /**
     * Called when the configuration of a cart was updated. Properties can optionally
     * perform their own initialization here.
     * 
     * @param properties Cart Properties that have had the configuration updated
     */
    default void onConfigurationChanged(CartProperties properties) {}

    /**
     * Called when the configuration of a train was updated. Properties can optionally
     * perform their own initialization here.
     * 
     * @param properties Train Properties that have had the configuration updated
     */
    default void onConfigurationChanged(TrainProperties properties) {}

    /**
     * Reads the value of this property for the given cart properties.
     * If this property is used for entire trains, then the current value
     * of the property for the train properties of this cart is returned
     * instead.
     * 
     * @param properties Cart Properties to read from
     * @return current property value
     */
    T get(CartProperties properties);

    /**
     * Updates the value of this property in the cart properties specified.
     * If this property is used for entire trains, and not for single carts, then
     * setting it will set it for the entire train.
     * 
     * @param properties Cart Properties to update
     * @param value New value
     */
    void set(CartProperties properties, T value);

    /**
     * Reads the value of this property for the given train properties.
     * If this property is used for individual carts, rather than entire trains,
     * then the property is read from the first cart of the train (head).
     * Some properties may re-implement this to combine the values of all
     * carts together.
     * 
     * @param properties Train Properties to read from
     * @return current property value
     */
    T get(TrainProperties properties);

    /**
     * Updates the value of this property in the train propeties specified.
     * If this property is used for individual carts, applies the same
     * property value to all individual carts of the train.
     * 
     * @param properties Train Properties to update
     * @param value
     */
    void set(TrainProperties properties, T value);
}
