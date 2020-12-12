package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.List;
import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

/**
 * The current destination a train is going for. May also update the
 * {@link StandardProperties#DESTINATION_ROUTE_INDEX} when new destinations
 * are set.
 */
public final class DestinationProperty implements ICartProperty<String> {

    @PropertyParser("destination")
    public String parseDestination(String input) {
        return input;
    }

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "destination", String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, "destination", value);
    }

    @Override
    public void set(CartProperties properties, String value) {
        // Save current index before the destination was changed
        int prior_route_index = properties.getCurrentRouteDestinationIndex();

        // Update destination
        ICartProperty.super.set(properties, value);

        // If a destination is now set, increment the route index if it matches the next one in the list
        if (!value.isEmpty() && prior_route_index != -1) {
            List<String> route = StandardProperties.DESTINATION_ROUTE.get(properties);
            int nextIndex = (prior_route_index + 1) % route.size();
            if (value.equals(route.get(nextIndex))) {
                StandardProperties.DESTINATION_ROUTE_INDEX.set(properties, nextIndex);
            }
        }
    }

    @Override
    public String get(TrainProperties properties) {
        // Return first cart from index=0 that has a destination
        for (CartProperties cprop : properties) {
            String destination = get(cprop);
            if (!destination.isEmpty()) {
                return destination;
            }
        }
        return "";
    }
}
