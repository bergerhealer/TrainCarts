package com.bergerkiller.bukkit.tc.properties.api;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * A Double value property. Adds methods to get/set the value using double,
 * avoiding boxing overhead.
 */
public interface IDoubleProperty extends IProperty<Double> {
    /**
     * Gets the default value as a Double
     *
     * @return default property value
     * @see #getDefault()
     */
    double getDoubleDefault();

    /**
     * Reads a double property for a given cart. If these properties are for trains,
     * returns the value of the train instead.
     *
     * @param properties Cart Properties to read from
     * @return current property value
     */
    double getDouble(CartProperties properties);

    /**
     * Reads a double property for a given train. If these properties are for carts not trains,
     * returns the value of the first cart instead.
     *
     * @param properties Cart Properties to read from
     * @return current property value
     */
    double getDouble(TrainProperties properties);

    @Override
    default java.lang.Double getDefault() {
        return java.lang.Double.valueOf(getDoubleDefault());
    }

    @Override
    default Double get(CartProperties properties) {
        return java.lang.Double.valueOf(getDouble(properties));
    }

    @Override
    default Double get(TrainProperties properties) {
        return java.lang.Double.valueOf(getDouble(properties));
    }
}
