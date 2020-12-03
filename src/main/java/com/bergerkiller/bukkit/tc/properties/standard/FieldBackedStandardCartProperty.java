package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;

/**
 * A standard cart property that can read the current
 * value from a TrainCarts internal field. Should be used
 * when optimized access to a property is required.
 */
public interface FieldBackedStandardCartProperty<T> extends ICartProperty<T> {

    /**
     * Reads the current property value stored in the holder object
     * 
     * @param holder
     * @return holder property value
     */
    T getHolderValue(FieldBackedStandardCartPropertiesHolder holder);

    /**
     * Applies the current property value stored in cart properties configuration
     * to the direct-access properties holder field.
     * 
     * @param holder The holder to update
     * @param value The value to update the holder with
     */
    void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, T value);

    @Override
    default void onConfigurationChanged(CartProperties properties) {
        setHolderValue(properties.getStandardPropertiesHolder(),
                this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault));
    }

    @Override
    default T get(CartProperties properties) {
        return getHolderValue(properties.getStandardPropertiesHolder());
    }

    @Override
    default void set(CartProperties properties, T value) {
        ICartProperty.super.set(properties, value);
        setHolderValue(properties.getStandardPropertiesHolder(), value);
    }

    /**
     * Utility function for implementing {@link #set(TrainProperties, Object)} that combines the values
     * of all sets together
     * 
     * @param properties TrainProperties whose carts to combine
     * @param property Property that returns a Set
     * @return combined set (unmodifiable, immutable)
     */
    public static Set<String> combineCartValues(TrainProperties properties, FieldBackedStandardCartProperty<Set<String>> property) {
        if (properties.size() == 1) {
            return property.get(properties.get(0));
        } else {
            Set<String> result = new HashSet<String>();
            for (CartProperties cprop : properties) {
                result.addAll(property.get(cprop));
            }
            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * Provides read access to a primitive double property. Prevents unneeded boxing
     * of primitive property values.
     */
    public static interface StandardDouble extends FieldBackedStandardCartProperty<java.lang.Double> {

        /**
         * @return default property value
         * @see #getDefault()
         */
        double getDoubleDefault();

        /**
         * Reads a double property from a {@link FieldBackedStandardCartPropertiesHolder}
         * 
         * @param holder StandardCartPropertiesHolder
         * @return current property value
         */
        double getHolderDoubleValue(FieldBackedStandardCartPropertiesHolder holder);

        /**
         * Updates a double property of {@link FieldBackedStandardCartPropertiesHolder}
         * 
         * @param holder StandardCartPropertiesHolder
         * @param value The new value
         */
        void setHolderDoubleValue(FieldBackedStandardCartPropertiesHolder holder, double value);

        @Override
        default java.lang.Double getDefault() {
            return java.lang.Double.valueOf(getDoubleDefault());
        }

        @Override
        default java.lang.Double getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return java.lang.Double.valueOf(getHolderDoubleValue(holder));
        }

        @Override
        default void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, java.lang.Double value) {
            setHolderDoubleValue(holder, value.doubleValue());
        }
    }
}
