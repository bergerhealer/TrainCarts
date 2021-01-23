package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Controls whether a train slows down (or speeds up) over time
 * due to various factors.
 */
public final class SlowdownProperty extends FieldBackedStandardTrainProperty<Set<SlowdownMode>> {
    private final Set<SlowdownMode> ALL = Collections.unmodifiableSet(EnumSet.allOf(SlowdownMode.class));
    private final Set<SlowdownMode> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SlowdownMode.class));

    public void appendSlowdownInfo(MessageBuilder message, TrainProperties properties) {
        message.yellow("Slow down over time: ");
        if (properties.isSlowingDownAll()) {
            message.green("Yes (All)");
        } else if (properties.isSlowingDownNone()) {
            message.red("No (None)");
        } else {
            message.setSeparator(", ");
            for (SlowdownMode mode : SlowdownMode.values()) {
                if (properties.isSlowingDown(mode)) {
                    message.green(mode.getKey() + "[Yes]");
                } else {
                    message.red(mode.getKey() + "[No]");
                }
            }
            message.clearSeparator();
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("slowdown")
    @CommandMethod("train slowdown <mode> <enabled>")
    @CommandDescription("Sets whether trains slow down and speed up due to a particular type of slow-down mode")
    private void trainSetSlowdownMode(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") SlowdownMode mode,
            final @Argument("enabled") boolean enabled
    ) {
        properties.setSlowingDown(mode, enabled);
        trainGetSlowdownMode(sender, properties, mode);
    }

    @CommandMethod("train slowdown <mode>")
    @CommandDescription("Gets whether trains slow down and speed up for a particular slow-down mode")
    private void trainGetSlowdownMode(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") SlowdownMode mode
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train slows down over time due to " + ChatColor.BLUE +
                mode.getKey() + ChatColor.YELLOW + ": " +
                (properties.isSlowingDown(mode) ? (ChatColor.GREEN + "Yes") : (ChatColor.RED + "No")));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("slowdown")
    @CommandMethod("train slowdown all|true|enable|enabled")
    @CommandDescription("Enables all default modes of slowing down")
    private void trainSetSlowdownAll(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.setSlowingDown(true);
        trainGetSlowdownModes(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("slowdown")
    @CommandMethod("train slowdown none|false|disable|disabled")
    @CommandDescription("Disables all default modes of slowing down")
    private void trainSetSlowdownNone(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.setSlowingDown(false);
        trainGetSlowdownModes(sender, properties);
    }

    @CommandMethod("train slowdown")
    @CommandDescription("Gets what types of slow-down are enabled for a train")
    private void trainGetSlowdownModes(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();
        appendSlowdownInfo(message, properties);
        message.send(sender);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_SLOWDOWN.has(sender);
    }

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
        if (context.input().equalsIgnoreCase("all")) {
            return ALL;
        } else if (context.input().equalsIgnoreCase("none")) {
            return NONE;
        } else {
            return context.inputBoolean() ? ALL : NONE;
        }
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
