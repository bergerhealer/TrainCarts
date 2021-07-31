package com.bergerkiller.bukkit.tc.properties.api;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * A condition for filtering train properties in a command selector
 */
@FunctionalInterface
public interface IPropertySelectorCondition {

    /**
     * Gets whether the properties match this condition
     *
     * @param properties Train properties to check
     * @param condition The condition to check
     * @return True if the condition is true
     */
    boolean matches(TrainProperties properties, SelectorCondition condition);
}
