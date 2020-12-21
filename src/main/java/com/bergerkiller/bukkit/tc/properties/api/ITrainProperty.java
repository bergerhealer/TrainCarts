package com.bergerkiller.bukkit.tc.properties.api;

import java.util.Optional;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * {@link IProperty} specifically for properties stored per train.
 * When reading properties on carts, the property is read from the train
 * instead. When updating properties of carts, the property is applied
 * to the train of the cart.
 *
 * @param <T> Property value type
 */
public interface ITrainProperty<T> extends IProperty<T> {

    @Override
    default T get(TrainProperties properties) {
        return this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault);
    }

    @Override
    default T get(CartProperties properties) {
        return get(properties.getTrainProperties());
    }

    @Override
    default void set(CartProperties properties, T value) {
        set(properties.getTrainProperties(), value);
    }

    @Override
    default void set(TrainProperties properties, T value) {
        if (value == null || value.equals(this.getDefault())) {
            this.writeToConfig(properties.getConfig(), Optional.empty());
        } else {
            this.writeToConfig(properties.getConfig(), Optional.of(value));
        }
        properties.tryUpdate(); // onPropertiesChanged()
    }
}
