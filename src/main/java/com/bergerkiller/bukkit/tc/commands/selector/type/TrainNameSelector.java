package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

/**
 * Selects train names on the server
 */
public class TrainNameSelector implements SelectorHandler {

    @Override
    public Collection<String> handle(CommandSender sender, String selector, Map<String, String> arguments) throws SelectorException {
        String name = arguments.get("name");
        if (name != null) {
            Collection<String> names = TrainPropertiesStore.matchAll(name).stream()
                    .map(TrainProperties::getTrainName)
                    .collect(Collectors.toList());
            if (names.isEmpty()) {
                throw new SelectorException("No trains match name expression '" + name + "'");
            }

            return names;
        }

        throw new SelectorException("No train name format specified");
    }
}
