package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;

/**
 * Stores a set of tickets that a player can use to enter a train.
 * If empty, no tickets are required.
 */
public final class TicketSetProperty implements ITrainProperty<Set<String>> {

    @PropertyParser("setticket|tickets set")
    public Set<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptySet() : Collections.singleton(input);
    }

    @PropertyParser("clrticket|cleartickets|tickets clear")
    public Set<String> parseClear(String input) {
        return Collections.emptySet();
    }

    @PropertyParser(value = "addticket|tickets add", processPerCart = true)
    public Set<String> parseAdd(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.add(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @PropertyParser(value = "remticket|tickets rem|tickets remove", processPerCart = true)
    public Set<String> parseRemove(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.remove(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @Override
    public Set<String> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "tickets");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "tickets", value);
    }
}
