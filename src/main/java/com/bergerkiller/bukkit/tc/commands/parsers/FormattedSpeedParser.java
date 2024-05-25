package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.mountiplex.MountiplexUtil;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Parses a speed with a default unit of blocks/tick, where a unit
 * can optionally be specified.
 */
public class FormattedSpeedParser implements ArgumentParser<CommandSender, FormattedSpeed>, BlockingSuggestionProvider.Strings<CommandSender> {
    private final boolean _greedy;

    public FormattedSpeedParser(boolean greedy) {
        this._greedy = greedy;
    }

    public static ParserDescriptor<CommandSender, FormattedSpeed> formattedSpeedParser(boolean greedy) {
        return ParserDescriptor.of(new FormattedSpeedParser(greedy), FormattedSpeed.class);
    }

    @Override
    public @NonNull ArgumentParseResult<@NonNull FormattedSpeed> parse(
            @NonNull CommandContext<@NonNull CommandSender> commandContext,
            @NonNull CommandInput commandInput
    ) {
        String input = this._greedy ? commandInput.remainingInput() : commandInput.peekString();
        FormattedSpeed result;

        if (input.equalsIgnoreCase("nan")) {
            result = FormattedSpeed.of(Double.NaN);
        } else {
            result = FormattedSpeed.parse(input, null);
            if (result == null) {
                return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                        Localization.COMMAND_INPUT_SPEED_INVALID, input));
            }
        }

        if (this._greedy) {
            commandInput.cursor(commandInput.length());
        } else {
            commandInput.readString();
        }
        return ArgumentParseResult.success(result);
    }

    @Override
    public @NonNull List<@NonNull String> stringSuggestions(
            @NonNull CommandContext<CommandSender> commandContext,
            @NonNull CommandInput commandInput
    ) {
        final String input = commandInput.lastRemainingToken();

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
