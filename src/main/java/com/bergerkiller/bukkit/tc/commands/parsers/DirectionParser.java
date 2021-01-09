package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Localization;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses a direction from string
 */
public class DirectionParser implements ArgumentParser<CommandSender, Direction> {

    public DirectionParser() {
    }

    @Override
    public ArgumentParseResult<Direction> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        Direction result = Direction.parse(inputQueue.peek());
        if (result == Direction.NONE) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    Localization.COMMAND_INPUT_DIRECTION_INVALID, inputQueue.peek()));
        }

        inputQueue.poll();
        return ArgumentParseResult.success(result);
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
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
