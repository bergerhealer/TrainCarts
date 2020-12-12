package com.bergerkiller.bukkit.tc.properties.standard.fieldbacked;

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
public abstract class FieldBackedStandardCartProperty<T> extends FieldBackedProperty<T> implements ICartProperty<T> {

    /**
     * Reads the current property value stored in internal data
     * 
     * @param data The internal data to read from
     * @return data property value
     */
    public abstract T getData(CartInternalData data);

    /**
     * Applies the current property value stored in cart properties configuration
     * to the direct-access internal data field.
     * 
     * @param data The internal data to update
     * @param value The value to update the data with
     */
    public abstract void setData(CartInternalData data, T value);

    @Override
    public void onConfigurationChanged(CartProperties properties) {
        setData(CartInternalData.get(properties),
                this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault));
    }

    @Override
    public T get(CartProperties properties) {
        return getData(CartInternalData.get(properties));
    }

    @Override
    public void set(CartProperties properties, T value) {
        ICartProperty.super.set(properties, value);
        setData(CartInternalData.get(properties), value);
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
    public static abstract class StandardDouble extends FieldBackedStandardCartProperty<java.lang.Double> {

        /**
         * @return default property value
         * @see #getDefault()
         */
        public abstract double getDoubleDefault();

        /**
         * Reads a double property from a {@link CartInternalData}
         * 
         * @param data CartInternalData
         * @return current property value
         */
        public abstract double getDataDouble(CartInternalData data);

        /**
         * Updates a double property of {@link CartInternalData}
         * 
         * @param data CartInternalData
         * @param value The new value
         */
        public abstract void setDataDouble(CartInternalData data, double value);

        /**
         * Reads a double property for a given cart
         * 
         * @param properties Cart Properties to read from
         * @return current property value
         */
        public final double getDouble(CartProperties properties) {
            return getDataDouble(CartInternalData.get(properties));
        }

        @Override
        public java.lang.Double getDefault() {
            return java.lang.Double.valueOf(getDoubleDefault());
        }

        @Override
        public java.lang.Double getData(CartInternalData holder) {
            return java.lang.Double.valueOf(getDataDouble(holder));
        }

        @Override
        public void setData(CartInternalData holder, java.lang.Double value) {
            setDataDouble(holder, value.doubleValue());
        }
    }
}
