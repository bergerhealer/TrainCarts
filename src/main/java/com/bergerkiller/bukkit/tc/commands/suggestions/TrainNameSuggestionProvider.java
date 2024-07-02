package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.cloud.parsers.QuotedArgumentParser;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Suggests a train name from all trains that a player can edit/has ownership of.
 * Also handles suggestions for the @train[...] selector in this spot.
 */
public class TrainNameSuggestionProvider implements BlockingSuggestionProvider.Strings<CommandSender> {

    // For use in command arguments (not flags) that are @Quoted
    // Makes use of BKCL's QuotedArgumentParser (but ignores the parser part)
    // TODO: QuotedArgumentParser now has some helper methods for this instead!
    public SuggestionProvider<CommandSender> quoteEscaped() {
        return new QuotedArgumentParser<CommandSender, String>() {
            // @Override - new since 1.21 later builds
            public boolean isStrictQuoteEscaping() {
                return true;
            }

            @Override
            public ArgumentParseResult<String> parseQuotedString(CommandContext<CommandSender> commandContext, String inputString) {
                return ArgumentParseResult.success(inputString);
            }

            @Override
            public SuggestionProvider<CommandSender> suggestionProvider() {
                return TrainNameSuggestionProvider.this;
            }
        }.createParser().suggestionProvider();
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(
            @NonNull CommandContext<CommandSender> commandContext,
            @NonNull CommandInput commandInput
    ) {
        final CommandSender sender = commandContext.sender();
        String input = commandInput.lastRemainingToken();
        if (input.startsWith("@train[")) {
            TrainCarts plugin = commandContext.inject(TrainCarts.class).get();
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
                    .map(TrainProperties::getTrainName)
                    .map(ChatColor::stripColor);
            if ("@train[".startsWith(input)) {
                stream = Stream.concat(stream, Stream.of("@train["));
            }
            return stream.collect(Collectors.toList());
        }
    }
}
