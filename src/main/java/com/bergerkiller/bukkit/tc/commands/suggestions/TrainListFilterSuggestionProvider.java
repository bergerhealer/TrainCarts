package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;

import cloud.commandframework.context.CommandContext;

/**
 * Provides suggestions for the arguments following a /train list command
 */
public class TrainListFilterSuggestionProvider implements BiFunction<CommandContext<CommandSender>, String, List<String>> {

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
        }

        return Collections.singletonList("@train[");
    }
}
