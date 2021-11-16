package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import cloud.commandframework.context.CommandContext;

/**
 * Suggests a train name from all trains that a player can edit/has ownership of.
 * Also handles suggestions for the @train[...] selector in this spot.
 */
public class TrainNameSuggestionProvider implements BiFunction<CommandContext<CommandSender>, String, List<String>> {

    @Override
    public List<String> apply(CommandContext<CommandSender> context, String input) {
        final CommandSender sender = context.getSender();
        if (input.startsWith("@train[")) {
            TrainCarts plugin = context.inject(TrainCarts.class).get();
            SelectorHandler handler = plugin.getSelectorHandlerRegistry().find("train");
            if (handler == null) {
                return Collections.singletonList("@train[]");
            }

            //TODO: Make this all fancy-like and stuff
            return handler.options(sender, "train", Collections.emptyList()).stream()
                    .map(SelectorHandlerConditionOption::name)
                    .map(s -> "@train[" + s + "=")
                    .collect(Collectors.toList());
        } else {
            // Train names, or the @train flag (start with opening bracket)
            Stream<String> stream = TrainProperties.getAll().stream()
                    .filter(p -> !(sender instanceof Player) || p.hasOwnership((Player) sender))
                    .map(TrainProperties::getTrainName);
            if ("@train[".startsWith(input)) {
                stream = Stream.concat(stream, Stream.of("@train["));
            }
            return stream.collect(Collectors.toList());
        }
    }
}
