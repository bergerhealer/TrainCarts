package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Optional;

import org.bukkit.util.Vector;

/**
 * A single switchable rail junction. Switcher signs can switch rails from and to different
 * junctions to perform routing functionality.
 */
public final class RailJunction {
    private final String _name;
    private final RailPath.Position _position;

    /**
     * Rail junction constructor.
     * 
     * @param name of the junction, used when parsing switcher signs
     * @param position of the end of the junction, used by path finding (relative or absolute)
     */
    public RailJunction(String name, RailPath.Position position) {
        this._name = name;
        this._position = position;
    }

    /**
     * Gets the name of this junction.
     * This is used when parsing direction statements on switcher signs.
     * 
     * @return name
     */
    public String name() {
        return this._name;
    }

    /**
     * Gets the end position on the rails when taking this junction.
     * This is used by the path finding algorithm during the mapping process.
     * The position can be relative or absolute, check {@link RailPath.Position#relative}.
     * 
     * @return end position on the rails of this junction
     */
    public RailPath.Position position() {
        return this._position;
    }

    @Override
    public String toString() {
        return "{" + this._name + ": " + this._position.toString() + "}";
    }

    /**
     * Searches for the junction which best leads into a given direction
     *
     * @param junctions Junctions to search in
     * @param direction Direction to match
     * @return Best-matching junction, or Empty if no junctions lead anywhere near the direction
     */
    public static Optional<RailJunction> findBest(Iterable<RailJunction> junctions, Vector direction) {
        double bestDot = 0.0;
        RailJunction best = null;
        for (RailJunction junction : junctions) {
            double dot = junction.position().motDot(direction);
            if (dot > bestDot) {
                bestDot = dot;
                best = junction;
            }
        }
        return Optional.ofNullable(best);
    }
}
