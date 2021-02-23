package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.commands.argument.DirectionOrFormattedSpeed;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;

public class DirectionOrFormattedSpeedParser implements ArgumentParser<CommandSender, DirectionOrFormattedSpeed> {
    private final FormattedSpeedParser formattedSpeedParser = new FormattedSpeedParser(false);
    private final DirectionParser directionParser = new DirectionParser();

    @Override
    public ArgumentParseResult<DirectionOrFormattedSpeed> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        // Try formatted speed
        ArgumentParseResult<FormattedSpeed> speedResult = formattedSpeedParser.parse(commandContext, inputQueue);
        if (speedResult.getParsedValue().isPresent()) {
            return ArgumentParseResult.success(new DirectionOrFormattedSpeed(speedResult.getParsedValue().get()));
        }

        // Try direction
        ArgumentParseResult<Direction> directionResult = directionParser.parse(commandContext, inputQueue);
        if (directionResult.getParsedValue().isPresent()) {
            return ArgumentParseResult.success(new DirectionOrFormattedSpeed(directionResult.getParsedValue().get()));
        }

        // Leading
        return ArgumentParseResult.failure(speedResult.getFailure().get());
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        List<String> formattedSpeedSuggestions = formattedSpeedParser.suggestions(commandContext, input);
        List<String> directionSuggestions = directionParser.suggestions(commandContext, input);
        List<String> result = new ArrayList<String>(formattedSpeedSuggestions.size() + directionSuggestions.size());
        result.addAll(formattedSpeedSuggestions);
        result.addAll(directionSuggestions);
        return result;
    }
}
