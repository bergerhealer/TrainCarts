package com.bergerkiller.bukkit.tc.commands.argument;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;

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
