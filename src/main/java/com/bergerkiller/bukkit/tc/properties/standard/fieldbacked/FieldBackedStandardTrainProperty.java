package com.bergerkiller.bukkit.tc.properties.standard.fieldbacked;

import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

/**
 * A standard cart property that can read the current
 * value from a TrainCarts internal field. Should be used
 * when optimized access to a property is required.
 */
public abstract class FieldBackedStandardTrainProperty<T> extends FieldBackedProperty<T> implements ITrainProperty<T> {

    /**
     * Reads the current property value stored in internal data
     * 
     * @param data The internal data to read from
     * @return data property value
     */
    public abstract T getData(TrainInternalData data);

    /**
     * Applies the current property value stored in train properties configuration
     * to the direct-access internal data field.
     * 
     * @param data The internal data to update
     * @param value The value to update the data with
     */
    public abstract void setData(TrainInternalData data, T value);

    @Override
    public void onConfigurationChanged(TrainProperties properties) {
        setData(TrainInternalData.get(properties),
                this.readFromConfig(properties.getConfig()).orElseGet(this::getDefault));
    }

    @Override
    public T get(TrainProperties properties) {
        return getData(TrainInternalData.get(properties));
    }

    @Override
    public void set(TrainProperties properties, T value) {
        ITrainProperty.super.set(properties, value);
        setData(TrainInternalData.get(properties), value);
    }

    /**
     * Provides read access to a primitive double property. Prevents unneeded boxing
     * of primitive property values.
     */
    public static abstract class StandardDouble extends FieldBackedStandardTrainProperty<java.lang.Double> {

        /**
         * @return default property value
         * @see #getDefault()
         */
        public abstract double getDoubleDefault();

        /**
         * Reads a double property from a {@link TrainInternalData}
         * 
         * @param data TrainInternalData
         * @return current property value
         */
        public abstract double getDoubleData(TrainInternalData data);

        /**
         * Updates a double property of {@link TrainInternalData}
         * 
         * @param data TrainInternalData
         * @param value The new value
         */
        public abstract void setDoubleData(TrainInternalData data, double value);

        /**
         * Reads a double property for a given train
         * 
         * @param properties Train Properties to read from
         * @return current property value
         */
        public final double getDouble(TrainProperties properties) {
            return getDoubleData(TrainInternalData.get(properties));
        }

        @Override
        public java.lang.Double getDefault() {
            return java.lang.Double.valueOf(getDoubleDefault());
        }

        @Override
        public java.lang.Double getData(TrainInternalData holder) {
            return java.lang.Double.valueOf(getDoubleData(holder));
        }

        @Override
        public void setData(TrainInternalData holder, java.lang.Double value) {
            setDoubleData(holder, value.doubleValue());
        }
    }

}
