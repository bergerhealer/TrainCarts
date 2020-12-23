package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import com.bergerkiller.mountiplex.MountiplexUtil;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.standard.DoubleArgument.DoubleParser;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.bukkit.parsers.location.LocationArgument;
import cloud.commandframework.context.CommandContext;
import io.leangen.geantyref.TypeToken;

/**
 * Specifies an x, y and z-coordinate and a radius around which
 * to look for something.
 */
public class NearPosition {
    public final Location at;
    public final double radius;

    public NearPosition(Location at, double radius) {
        this.at = at;
        this.radius = radius;
    }

    @Override
    public String toString() {
        return "near{world=" + at.getWorld().getName() + ", x=" + at.getX()
                + ", y=" + at.getY() + ", z=" + at.getZ()
                + ", radius=" + radius + "}";
    }

    public static CommandArgument<CommandSender, NearPosition> asArgument(String name) {
        return new NearArgument(name);
    }

    private static class NearArgument extends CommandArgument<CommandSender, NearPosition> {

        private NearArgument(String name) {
            super(true, name, new NearParser(), "",
                    TypeToken.get(NearPosition.class),
                    null,
                    Collections.emptyList());
        }
    }

    private static class NearParser implements ArgumentParser<CommandSender, NearPosition> {

        private static final int EXPECTED_PARAMETER_COUNT = 4;

        private final LocationArgument.LocationParser<CommandSender> locationParser = new LocationArgument.LocationParser<>();
        private final DoubleParser<CommandSender> radiusParser = new DoubleParser<CommandSender>(0.0, Double.MAX_VALUE);

        @Override
        public ArgumentParseResult<NearPosition> parse(
                final CommandContext<CommandSender> commandContext,
                final Queue<String> inputQueue
        ) {
            // Parse x/y/z location (and world from sender)
            ArgumentParseResult<Location> locationResult = this.locationParser.parse(commandContext, inputQueue);
            if (locationResult.getFailure().isPresent()) {
                return ArgumentParseResult.failure(
                        locationResult.getFailure().get()
                );
            }

            // Parse radius
            ArgumentParseResult<Double> radiusResult = this.radiusParser.parse(commandContext, inputQueue);
            if (radiusResult.getFailure().isPresent()) {
                return ArgumentParseResult.failure(
                        radiusResult.getFailure().get()
                );
            }

            // Done!
            return ArgumentParseResult.success(new NearPosition(
                    locationResult.getParsedValue().get(),
                    radiusResult.getParsedValue().get()));
        }

        @Override
        public List<String> suggestions(
                final CommandContext<CommandSender> commandContext,
                final String input
        ) {
            if (input.isEmpty()) {
                return Stream.concat(MountiplexUtil.toStream("~"),
                        IntStream.range(0, 10).mapToObj(Integer::toString))
                        .collect(Collectors.toList());
            }

            final String workingInput;
            final String prefix;
            if (input.startsWith("~") || input.startsWith("^")) {
                prefix = Character.toString(input.charAt(0));
                workingInput = input.substring(1);
            } else {
                prefix = "";
                workingInput = input;
            }
            return IntegerArgument.IntegerParser.getSuggestions(
                    Integer.MIN_VALUE,
                    Integer.MAX_VALUE,
                    workingInput
            ).stream().map(string -> prefix + string).collect(Collectors.toList());
        }

        @Override
        public int getRequestedArgumentCount() {
            return EXPECTED_PARAMETER_COUNT;
        }
    }
}
