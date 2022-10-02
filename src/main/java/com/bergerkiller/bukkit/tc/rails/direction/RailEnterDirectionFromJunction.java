package com.bergerkiller.bukkit.tc.rails.direction;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
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
        // Take the rail state and walk it backwards until we reach the end of the path
        // This should be nearly the exact position and direction this junction represents
        RailPath path = state.loadRailLogic().getPath();
        RailPath.Position pos = state.position().clone();
        pos.makeRelative(state.railBlock());
        pos.invertMotion();

        // Move backwards all the way to the back
        path.moveRelative(pos, Double.MAX_VALUE);

        // Verify current position has the correct movement direction
        // This works regardless of whether the junction is absolute or relative, so just do it now
        if (pos.motDot(junction.position()) <= 0.0) {
            return false;
        }

        // Verify the positions are acceptably close together
        // 1e-5 = (1e-10 * 1e-10)
        return pos.distanceSquaredAtRail(state.railBlock(), junction.position()) < 1e-10;
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
