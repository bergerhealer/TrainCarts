package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

/**
 * A simple set of tags that can be used to mark and switch carts or trains
 */
public final class TagSetProperty extends FieldBackedStandardCartProperty<Set<String>> {

    @PropertyParser("settag")
    public Set<String> parse(String input) {
        return Collections.singleton(input);
    }

    @PropertyParser(value="addtag", processPerCart=true)
    public Set<String> parseAddTag(PropertyParseContext<Set<String>> context) {
        // If empty, do nothing
        if (context.input().isEmpty()) {
            return context.current();
        }

        // When old set of tags is empty, return singleton set of new tag
        if (context.current().isEmpty()) {
            return Collections.singleton(context.input());
        }

        // If already contained, return the same set of tags
        if (context.current().contains(context.input())) {
            return context.current();
        }

        // Combine old and new into a new set
        HashSet<String> newTags = new HashSet<String>(context.current());
        newTags.add(context.input());
        return Collections.unmodifiableSet(newTags);
    }

    @PropertyParser(value="remtag|removetag", processPerCart=true)
    public Set<String> parseRemoveTag(PropertyParseContext<Set<String>> context) {
        // If empty or not contained, do nothing
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        }

        // If size=1 then no more tags remain, return empty set
        if (context.current().size() == 1) {
            return Collections.emptySet();
        }

        // Remove from set
        HashSet<String> newTags = new HashSet<String>(context.current());
        newTags.remove(context.input());
        return Collections.unmodifiableSet(newTags);
    }

    @Override
    public Set<String> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getData(CartInternalData data) {
        return data.tags;
    }

    @Override
    public void setData(CartInternalData data, Set<String> value) {
        data.tags = value;
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "tags");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "tags", value);
    }

    @Override
    public Set<String> get(TrainProperties properties) {
        return FieldBackedStandardCartProperty.combineCartValues(properties, this);
    }
}
