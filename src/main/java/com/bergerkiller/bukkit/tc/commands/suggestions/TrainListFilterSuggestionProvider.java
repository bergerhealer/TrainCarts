package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Provides suggestions for the arguments following a /train list command
 */
public class TrainListFilterSuggestionProvider implements BlockingSuggestionProvider.Strings<CommandSender> {
    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> context, @NonNull CommandInput input) {
        final CommandSender sender = context.sender();
        if (input.remainingInput().startsWith("@train[")) {
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
