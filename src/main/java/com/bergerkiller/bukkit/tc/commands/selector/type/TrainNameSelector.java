package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.commands.selector.TCSelectorHandlerRegistry;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Selects train names on the server
 */
public class TrainNameSelector implements SelectorHandler {
    private final TCSelectorHandlerRegistry registry;
    private final Set<String> ignoredCommands = new HashSet<>();

    public TrainNameSelector(TCSelectorHandlerRegistry registry) {
        this.registry = registry;
        this.ignoredCommands.add("train_carts:train list");
        this.ignoredCommands.add("train list");
    }

    @Override
    public Collection<String> handle(CommandSender sender, String selector, List<SelectorCondition> conditions) throws SelectorException {
        return this.registry.matchTrains(sender, conditions).stream()
                .map(TrainProperties::getTrainName)
                .collect(Collectors.toList());
    }

    @Override
    public List<SelectorHandlerConditionOption> options(CommandSender sender, String selector, List<SelectorCondition> conditions) {
        return this.registry.matchOptions(sender, conditions);
    }

    @Override
    public boolean isCommandHandled(String command) {
        return !ignoredCommands.contains(command);
    }
}
