package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Localization;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Parses a direction from string
 */
public class DirectionParser implements ArgumentParser<CommandSender, Direction>, BlockingSuggestionProvider.Strings<CommandSender> {
    public static ParserDescriptor<CommandSender, Direction> directionParser() {
        return ParserDescriptor.of(new DirectionParser(), Direction.class);
    }

    @Override
    public @NonNull ArgumentParseResult<@NonNull Direction> parse(@NonNull CommandContext<@NonNull CommandSender> commandContext, @NonNull CommandInput commandInput) {
        Direction result = Direction.parse(commandInput.peekString());
        if (result == Direction.NONE) {
            return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                    Localization.COMMAND_INPUT_DIRECTION_INVALID, commandInput.peekString()));
        }

        commandInput.readString();
        return ArgumentParseResult.success(result);
    }

    @Override
    public @NonNull List<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput commandInput) {
        final String input = commandInput.lastRemainingToken();

        // These are recommended. If any start with input, suggest it
        List<String> recommended = Arrays.asList(
                "north", "east", "south", "west", "up", "down",
                "left", "right", "forward", "backward",
                "continue", "reverse");
        if (recommended.stream().anyMatch(s -> s.startsWith(input))) {
            return recommended;
        }

        // Suggest all possible values
        return Stream.of(Direction.values())
                .filter(s -> s != Direction.NONE)
                .flatMap(s -> Stream.of(s.aliases()))
                .collect(Collectors.toList());
    }
}
