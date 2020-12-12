package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

/**
 * Stores a list of destinations a cart traversed. When it reaches the next
 * destination in this list, it automatically advances to the next one.
 * This property and {@link StandardProperties#DESTINATION_ROUTE_INDEX}
 * are used together.
 */
public final class DestinationRouteProperty implements ICartProperty<List<String>> {

    @PropertyParser("clearroute|route clear")
    public List<String> parseClear(String input) {
        return Collections.emptyList();
    }

    @PropertyParser("setroute|route set")
    public List<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptyList() : Collections.singletonList(input);
    }

    @PropertyParser("loadroute|route load")
    public List<String> parseLoad(String input) {
        return TrainCarts.plugin.getRouteManager().findRoute(input);
    }

    @PropertyParser(value="addroute|route add", processPerCart = true)
    public List<String> parseAdd(PropertyParseContext<List<String>> context) {
        if (context.input().isEmpty()) {
            return context.current();
        } else if (context.current().isEmpty()) {
            return Collections.singletonList(context.input());
        } else {
            ArrayList<String> newRoute = new ArrayList<String>(context.current());
            newRoute.add(context.input());
            return Collections.unmodifiableList(newRoute);
        }
    }

    @PropertyParser(value="remroute|removeroute|route rem|route remove", processPerCart = true)
    public List<String> parseRemove(PropertyParseContext<List<String>> context) {
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        } else {
            ArrayList<String> newRoute = new ArrayList<String>(context.current());
            while (newRoute.remove(context.input())); // remove all instances
            return Collections.unmodifiableList(newRoute);
        }
    }

    @Override
    public List<String> getDefault() {
        return Collections.emptyList();
    }

    @Override
    public Optional<List<String>> readFromConfig(ConfigurationNode config) {
        if (config.contains("route")) {
            return Optional.of(Collections.unmodifiableList(new ArrayList<String>(
                    config.getList("route", String.class)
            )));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<List<String>> value) {
        if (value.isPresent()) {
            config.set("route", value.get());
        } else {
            config.remove("route");
        }
    }

    @Override
    public void set(CartProperties properties, List<String> value) {
        // Update route itself
        ICartProperty.super.set(properties, value);

        // Keep routing towards the same destination
        // This allows for a seamless transition between routes
        if (!value.isEmpty() && properties.hasDestination()) {
            int new_index = value.indexOf(properties.getDestination());
            if (new_index == -1) {
                new_index = 0;
            }
            properties.set(StandardProperties.DESTINATION_ROUTE_INDEX, new_index);
        } else {
            properties.set(StandardProperties.DESTINATION_ROUTE_INDEX, 0);
        }
    }

    @Override
    public List<String> get(TrainProperties properties) {
        for (CartProperties cprop : properties) {
            List<String> route = get(cprop);
            if (!route.isEmpty()) {
                return route;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Stores the current index of the route a train is moving towards.
     * Used together with {@link DestinationRouteProperty}.
     */
    public static final class IndexProperty implements ICartProperty<Integer> {
        private final Integer DEFAULT = 0;

        @Override
        public Integer getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Integer> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "routeIndex", int.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
            Util.setConfigOptional(config, "routeIndex", value);
        }
    }
}
