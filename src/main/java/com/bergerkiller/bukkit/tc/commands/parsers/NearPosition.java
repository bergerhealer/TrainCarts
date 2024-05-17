package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.LinkedList;
import java.util.Queue;

import io.leangen.geantyref.TypeToken;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.aggregate.AggregateParser;
import org.incendo.cloud.parser.standard.DoubleParser;

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

    public static ArgumentParseResult<NearPosition> parseNearest(final CommandContext<CommandSender> commandContext) {
        Queue<String> atSenderQueue = new LinkedList<>();
        atSenderQueue.add("~");
        atSenderQueue.add("~");
        atSenderQueue.add("~");
        ArgumentParseResult<Location> locationResult = new LocationParser<CommandSender>()
                .parse(commandContext, CommandInput.of(atSenderQueue));
        if (locationResult.failure().isPresent()) {
            return ArgumentParseResult.failure(
                    locationResult.failure().get()
            );
        }

        // Done!
        return ArgumentParseResult.success(new NearPosition(
                locationResult.parsedValue().get(),
                128.0));
    }

    public static ParserDescriptor<CommandSender, NearPosition> nearParser() {
        return AggregateParser.<CommandSender, Location, Double>pairBuilder(
                        "location", LocationParser.locationParser(),
                        "radius", DoubleParser.doubleParser(0))
                .withMapper(TypeToken.get(NearPosition.class),
                        (context, location, radius) ->
                                ArgumentParseResult.successFuture(new NearPosition(location, radius)))
                .build();
    }
}
