package com.bergerkiller.bukkit.tc.properties.standard;

import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

/**
 * A standard cart property that can read the current
 * value from a TrainCarts internal field. Should be used
 * when optimized access to a property is required.
 */
public interface FieldBackedStandardTrainProperty<T> extends ITrainProperty<T> {

    /**
     * Reads the current property value stored in the holder object
     * 
     * @param holder
     * @return holder property value
     */
    T getHolderValue(FieldBackedStandardTrainPropertiesHolder holder);

    /**
     * Applies the current property value stored in train properties configuration
     * to the direct-access properties holder field.
     * 
     * @param holder The holder to update
     * @param value The value to update the holder with
     */
    void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, T value);

    @Override
    default void onConfigurationChanged(TrainProperties properties) {
        setHolderValue(properties.getStandardPropertiesHolder(),
                this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault));
    }

    @Override
    default T get(TrainProperties properties) {
        return getHolderValue(properties.getStandardPropertiesHolder());
    }

    @Override
    default void set(TrainProperties properties, T value) {
        ITrainProperty.super.set(properties, value);
        setHolderValue(properties.getStandardPropertiesHolder(), value);
    }

    /**
     * Provides read access to a primitive double property. Prevents unneeded boxing
     * of primitive property values.
     */
    public static interface StandardDouble extends FieldBackedStandardTrainProperty<java.lang.Double> {

        /**
         * @return default property value
         * @see #getDefault()
         */
        double getDoubleDefault();

        /**
         * Reads a double property from a {@link FieldBackedStandardTrainPropertiesHolder}
         * 
         * @param holder StandardTrainPropertiesHolder
         * @return current property value
         */
        double getHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder);

        /**
         * Updates a double property of {@link FieldBackedStandardTrainPropertiesHolder}
         * 
         * @param holder StandardTrainPropertiesHolder
         * @param value The new value
         */
        void setHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder, double value);

        @Override
        default java.lang.Double getDefault() {
            return java.lang.Double.valueOf(getDoubleDefault());
        }

        @Override
        default java.lang.Double getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return java.lang.Double.valueOf(getHolderDoubleValue(holder));
        }

        @Override
        default void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, java.lang.Double value) {
            setHolderDoubleValue(holder, value.doubleValue());
        }
    }
}
