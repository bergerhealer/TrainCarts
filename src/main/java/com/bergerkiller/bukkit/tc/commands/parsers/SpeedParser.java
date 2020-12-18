package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.Queue;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Util;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses a speed with a default unit of blocks/tick, where a unit
 * can optionally be specified.
 */
public class SpeedParser implements ArgumentParser<CommandSender, Double> {
    public static final String NAME = "speed";
    private final boolean _greedy;

    public SpeedParser(boolean greedy) {
        this._greedy = greedy;
    }

    @Override
    public ArgumentParseResult<Double> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String input = this._greedy ? String.join(" ", inputQueue) : inputQueue.peek();

        double result = Util.parseVelocity(input, Double.NaN);
        if (Double.isNaN(result)) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    Localization.COMMAND_INPUT_SPEED_INVALID, input));
        }

        if (this._greedy) {
            inputQueue.clear();
        } else {
            inputQueue.poll();
        }
        return ArgumentParseResult.success(result);
    }
}
