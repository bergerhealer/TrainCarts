package com.bergerkiller.bukkit.tc.rails.direction;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

/**
 * A type of {@link RailEnterDirection} that makes use of a named junction of the rails
 */
public final class RailEnterDirectionFromJunction implements RailEnterDirection {
    private final RailJunction junction;

    RailEnterDirectionFromJunction(RailJunction junction) {
        this.junction = junction;
    }

    /**
     * Gets the Junction from which trains drive towards the switched rail
     * that this direction represents.
     *
     * @return junction
     */
    public RailJunction getJunction() {
        return this.junction;
    }

    @Override
    public String name() {
        return this.junction.name();
    }

    @Override
    public double motionDot(Vector motion) {
        return -this.junction.position().motDot(motion);
    }

    @Override
    public boolean match(RailState state) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof RailEnterDirectionFromJunction) {
            return junction.equals(((RailEnterDirectionFromJunction) o).getJunction());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "EnterFrom{junction=" + junction.name() + "}";
    }
}
