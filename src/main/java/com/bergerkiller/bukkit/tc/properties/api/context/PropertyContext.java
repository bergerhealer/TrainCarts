package com.bergerkiller.bukkit.tc.properties.api.context;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Base context of a parsing or formatting operation
 */
public class PropertyContext {
    private final IProperties properties;

    /**
     * Initializes a new PropertyContext
     *
     * @param properties Properties being parsed or formatted
     */
    public PropertyContext(IProperties properties) {
        this.properties = properties;
    }

    /**
     * Retrieves the cart properties involved in the current operation if
     * {@link #isCartProperties()} is true, otherwise returns
     * <i>null</i>
     *
     * @return Cart properties, null if the operation is on an entire train
     */
    public CartProperties cartProperties() {
        return isCartProperties() ? ((CartProperties) this.properties) : null;
    }

    /**
     * Retrieves the train properties involved in the current operation.
     * If {@link #isTrainProperties()} is true, then these
     * properties are returned. If {@link #isCartProperties()} is
     * true instead, then the train properties of the cart properties
     * are returned. If neither returns true, then the caller did
     * not specify properties, and null is returned.
     *
     * @return Train Properties, null if no properties were specified
     */
    public TrainProperties trainProperties() {
        if (isTrainProperties()) {
            return (TrainProperties) this.properties;
        } else if (isCartProperties()) {
            return ((CartProperties) this.properties).getTrainProperties();
        } else {
            return null;
        }
    }

    /**
     * Gets whether this context is about the properties of a cart 
     *
     * @return True if this is about cart properties
     */
    public boolean isCartProperties() {
        return this.properties instanceof CartProperties;
    }

    /**
     * Gets whether this context is about the properties of a train 
     *
     * @return True if this is about train properties
     */
    public boolean isTrainProperties() {
        return this.properties instanceof TrainProperties;
    }
}
