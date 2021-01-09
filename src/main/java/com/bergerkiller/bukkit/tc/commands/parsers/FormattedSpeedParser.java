package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.mountiplex.MountiplexUtil;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses a speed with a default unit of blocks/tick, where a unit
 * can optionally be specified.
 */
public class FormattedSpeedParser implements ArgumentParser<CommandSender, FormattedSpeed> {
    private final boolean _greedy;

    public FormattedSpeedParser(boolean greedy) {
        this._greedy = greedy;
    }

    @Override
    public ArgumentParseResult<FormattedSpeed> parse(
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
        FormattedSpeed result;

        if (input.equalsIgnoreCase("nan")) {
            result = FormattedSpeed.of(Double.NaN);
        } else {
            result = FormattedSpeed.parse(input, null);
            if (result == null) {
                return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                        Localization.COMMAND_INPUT_SPEED_INVALID, input));
            }
        }

        if (this._greedy) {
            inputQueue.clear();
        } else {
            inputQueue.poll();
        }
        return ArgumentParseResult.success(result);
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        if (input.isEmpty()) {
            // Show digits and '-'/'+'
            return Stream.concat(Stream.of("-", "+"), IntStream.range(0, 10)
                    .mapToObj(Integer::toString))
                    .collect(Collectors.toList());
        }

        char lastChar = input.charAt(input.length()-1);
        if (lastChar == '-' || lastChar == '.' || lastChar == ',') {
            // Show digits only
            return IntStream.range(0, 10)
                    .mapToObj(Integer::toString)
                    .map(s -> input + s)
                    .collect(Collectors.toList());
        } else if (Character.isDigit(lastChar)) {
            // Ends with a digit, variety of digits
            Stream<String> suggestions = unitStream();
            if (!input.contains(".") && !input.contains(",")) {
                suggestions = Stream.concat(suggestions, MountiplexUtil.toStream("."));
            }
            suggestions = Stream.concat(suggestions,
                    IntStream.range(0, 10)
                    .mapToObj(Integer::toString));
            return suggestions.map(s -> input + s).collect(Collectors.toList());
        } else {
            // Check if input ends with any of the units, if so, suggest those
            final String unitPrefix = getUnitPrefix(input);
            final String value = input.substring(0, input.length() - unitPrefix.length());
            return unitStream()
                    .filter(u -> u.startsWith(unitPrefix))
                    .map(u -> value + u)
                    .collect(Collectors.toList());
        }
    }

    private static String getUnitPrefix(String input) {
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '-' && c != '.' && c != ',' && c != ' ' && !Character.isDigit(c)) {
                return input.substring(i);
            }
        }
        return "";
    }

    private static Stream<String> unitStream() {
        return Stream.of("m/s", "km/h", "mi/h", "mph", "kmh", "ft/s");
    }
}
