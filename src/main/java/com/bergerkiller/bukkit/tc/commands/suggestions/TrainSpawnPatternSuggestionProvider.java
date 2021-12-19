package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;

import cloud.commandframework.context.CommandContext;

/**
 * Suggests parts of a train spawn pattern, as used for the chest item and on spawn signs
 */
public class TrainSpawnPatternSuggestionProvider implements BiFunction<CommandContext<CommandSender>, String, List<String>> {

    @Override
    public List<String> apply(CommandContext<CommandSender> commandContext, String input) {
        final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();

        if (input.isEmpty() || Character.isDigit(input.charAt(input.length()-1))) {
            // If last character is a digit or all empty, add all possible names as options (and more digits) as postfix
            Stream<String> result = plugin.getSavedTrains().getNames().stream();
            result = Stream.concat(result, Stream.of(SpawnableGroup.VanillaCartType.values())
                    .map(SpawnableGroup.VanillaCartType::toString));
            result = Stream.concat(result, IntStream.range(0, 10).mapToObj(Integer::toString));

            // Prefix text before
            if (!input.isEmpty()) {
                result = result.map(name -> input + name);
            }

            // Done
            return result.collect(Collectors.toList());
        } else {
            // Figure out name typed so far by looking up the first typed digit, if any
            int nameStart = 0;
            for (int i = input.length()-1; i >= 0; i--) {
                if (Character.isDigit(input.charAt(i))) {
                    nameStart = i + 1;
                    break;
                }
            }
            final String prefix = input.substring(0, nameStart);
            final String typedName = input.substring(nameStart);

            // Locate train names that could possibly be put at the end of the command
            List<String> filtered = plugin.getSavedTrains().getNames().stream()
                    .filter(n -> n.length() > typedName.length() && n.startsWith(typedName))
                    .map(n -> prefix + n)
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                // Just show vanilla cart types and digits here
                Stream<String> result = Stream.of(SpawnableGroup.VanillaCartType.values())
                        .map(SpawnableGroup.VanillaCartType::toString);
                result = Stream.concat(result, IntStream.range(0, 10).mapToObj(Integer::toString));
                result = result.map(n -> input + n);
                return result.collect(Collectors.toList());
            } else {
                // Complete!
                return filtered;
            }
        }
    }
}
