package com.bergerkiller.bukkit.tc.commands.argument;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.commands.parsers.DirectionParser;
import com.bergerkiller.bukkit.tc.commands.parsers.FormattedSpeedParser;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.type.Either;

import java.util.concurrent.CompletableFuture;

public final class DirectionOrFormattedSpeed {
    private final Direction direction;
    private final FormattedSpeed formattedSpeed;

    public DirectionOrFormattedSpeed(Direction direction) {
        this.direction = direction;
        this.formattedSpeed = null;
    }

    public DirectionOrFormattedSpeed(FormattedSpeed formattedSpeed) {
        this.direction = null;
        this.formattedSpeed = formattedSpeed;
    }

    public static ParserDescriptor<CommandSender, DirectionOrFormattedSpeed> directionOrFormattedSpeedParser() {
        return ArgumentParser.firstOf(
                FormattedSpeedParser.formattedSpeedParser(false),
                DirectionParser.directionParser()
        ).mapSuccess(
                DirectionOrFormattedSpeed.class,
                (context, either) -> CompletableFuture.completedFuture(DirectionOrFormattedSpeed.of(either))
        );
    }

    public static DirectionOrFormattedSpeed of(Either<FormattedSpeed, Direction> either) {
        return either.mapEither(DirectionOrFormattedSpeed::new, DirectionOrFormattedSpeed::new);
    }

    public boolean hasDirection() {
        return direction != null;
    }

    public boolean hasFormattedSpeed() {
        return formattedSpeed != null;
    }

    public Direction getDirection() {
        if (direction == null) {
            throw new UnsupportedOperationException("Argument has no direction");
        }
        return direction;
    }

    public FormattedSpeed getFormattedSpeed() {
        if (formattedSpeed == null) {
            throw new UnsupportedOperationException("Argument has no formatted speed");
        }
        return formattedSpeed;
    }
}
