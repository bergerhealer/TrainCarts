package com.bergerkiller.bukkit.tc.properties.api;

import java.util.Optional;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * {@link IProperty} specifically for properties stored per cart.
 * When reading properties on trains, the property is read from the first
 * cart instead. When updating properties of trains, the property is applied
 * to all carts.
 *
 * @param <T> Property value type
 */
public interface ICartProperty<T> extends IProperty<T> {

    @Override
    default T get(CartProperties properties) {
        return this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault);
    }

    @Override
    default T get(TrainProperties properties) {
        if (properties.isEmpty()) {
            return this.getDefault();
        } else {
            return get(properties.get(0));
        }
    }

    @Override
    default void set(TrainProperties properties, T value) {
        for (CartProperties cProp : properties) {
            set(cProp, value);
        }
    }

    @Override
    default void set(CartProperties properties, T value) {
        if (value == null || value.equals(this.getDefault())) {
            this.writeToConfig(properties.getConfig(), Optional.empty());
        } else {
            this.writeToConfig(properties.getConfig(), Optional.of(value));
        }
        properties.tryUpdate(); // onPropertiesChanged()
    }
}
