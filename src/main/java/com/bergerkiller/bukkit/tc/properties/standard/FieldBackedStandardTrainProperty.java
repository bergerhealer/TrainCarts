package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Set;

import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

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
    T getHolderValue(Holder holder);

    /**
     * Applies the current property value stored in train properties configuration
     * to the direct-access properties holder field.
     * 
     * @param holder The holder to update
     * @param value The value to update the holder with
     */
    void setHolderValue(Holder holder, T value);

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
        double getHolderDoubleValue(Holder holder);

        /**
         * Updates a double property of {@link FieldBackedStandardTrainPropertiesHolder}
         * 
         * @param holder StandardTrainPropertiesHolder
         * @param value The new value
         */
        void setHolderDoubleValue(Holder holder, double value);

        @Override
        default java.lang.Double getDefault() {
            return java.lang.Double.valueOf(getDoubleDefault());
        }

        @Override
        default java.lang.Double getHolderValue(Holder holder) {
            return java.lang.Double.valueOf(getHolderDoubleValue(holder));
        }

        @Override
        default void setHolderValue(Holder holder, java.lang.Double value) {
            setHolderDoubleValue(holder, value.doubleValue());
        }
    }

    /**
     * Holds a <b>copy</b> of the train properties stored in YAML configuration
     * for faster access at runtime. Properties that aren't accessed
     * very often aren't stored here.
     */
    public static final class Holder {
        protected double speedLimit;
        protected double gravity;
        protected CollisionOptions collision;
        protected Set<SlowdownMode> slowdown;
        protected SignSkipOptions signSkipOptionsData;
        protected WaitOptions waitOptionsData;
        protected BankingOptions bankingOptionsData;
        protected boolean soundEnabled;
        protected boolean keepChunksLoaded;
        protected boolean allowPlayerManualMovement;
        protected boolean allowMobManualMovement;
    }
}
