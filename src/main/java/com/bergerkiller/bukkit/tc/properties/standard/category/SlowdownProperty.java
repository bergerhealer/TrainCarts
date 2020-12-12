package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

/**
 * Controls whether a train slows (or speeds up) down over time
 * due to various factors.
 */
public final class SlowdownProperty extends FieldBackedStandardTrainProperty<Set<SlowdownMode>> {
    private final Set<SlowdownMode> ALL = Collections.unmodifiableSet(EnumSet.allOf(SlowdownMode.class));
    private final Set<SlowdownMode> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SlowdownMode.class));

    // Uses constants if possible, and otherwise makes the set unmodifiable
    private Set<SlowdownMode> wrapAndOptimize(Set<SlowdownMode> result) {
        if (result.isEmpty()) {
            return NONE;
        } else if (result.size() == ALL.size()) {
            return ALL;
        } else {
            return Collections.unmodifiableSet(result);
        }
    }

    @PropertyParser("slowdown")
    public Set<SlowdownMode> parseSlowdownAll(PropertyParseContext<Set<SlowdownMode>> context) {
        return context.inputBoolean() ? ALL : NONE;
    }

    @PropertyParser("slowfriction")
    public Set<SlowdownMode> parseSlowdownFriction(PropertyParseContext<Set<SlowdownMode>> context) {
        EnumSet<SlowdownMode> values = EnumSet.noneOf(SlowdownMode.class);
        values.addAll(context.current());
        LogicUtil.addOrRemove(values, SlowdownMode.FRICTION, context.inputBoolean());
        return wrapAndOptimize(values);
    }

    @PropertyParser("slowgravity")
    public Set<SlowdownMode> parseSlowdownGravity(PropertyParseContext<Set<SlowdownMode>> context) {
        EnumSet<SlowdownMode> values = EnumSet.noneOf(SlowdownMode.class);
        values.addAll(context.current());
        LogicUtil.addOrRemove(values, SlowdownMode.GRAVITY, context.inputBoolean());
        return wrapAndOptimize(values);
    }

    @Override
    public Set<SlowdownMode> getDefault() {
        return ALL;
    }

    @Override
    public Set<SlowdownMode> getData(TrainInternalData data) {
        return data.slowdown;
    }

    @Override
    public void setData(TrainInternalData data, Set<SlowdownMode> value) {
        data.slowdown = value;
    }

    @Override
    public Optional<Set<SlowdownMode>> readFromConfig(ConfigurationNode config) {
        if (!config.contains("slowDown")) {
            // Not set
            return Optional.empty();
        } else if (!config.isNode("slowDown")) {
            // Boolean all defaults / none
            return Optional.of(config.get("slowDown", true) ? ALL : NONE);
        } else {
            // Node with [name]: true/false options
            final EnumSet<SlowdownMode> modes = EnumSet.noneOf(SlowdownMode.class);
            ConfigurationNode slowDownNode = config.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                if (slowDownNode.contains(mode.getKey()) &&
                    slowDownNode.get(mode.getKey(), true))
                {
                    modes.add(mode);
                }
            }
            return Optional.of(wrapAndOptimize(modes));
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<SlowdownMode>> value) {
        if (value.isPresent()) {
            Set<SlowdownMode> modes = value.get();
            if (modes.isEmpty()) {
                config.set("slowDown", false);
            } else if (modes.equals(ALL)) {
                config.set("slowDown", true);
            } else {
                ConfigurationNode slowDownNode = config.getNode("slowDown");
                slowDownNode.clear();
                for (SlowdownMode mode : SlowdownMode.values()) {
                    slowDownNode.set(mode.getKey(), modes.contains(mode));
                }
            }
        } else {
            config.remove("slowDown");
        }
    }
}
