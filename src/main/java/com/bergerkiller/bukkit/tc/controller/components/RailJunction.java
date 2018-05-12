package com.bergerkiller.bukkit.tc.controller.components;

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
}
