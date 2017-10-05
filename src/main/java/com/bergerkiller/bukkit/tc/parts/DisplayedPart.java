package com.bergerkiller.bukkit.tc.parts;

import com.bergerkiller.bukkit.common.map.util.Matrix4f;

/**
 * A single item that exists somewhere in the world, attached to the Minecart to display things
 */
public interface DisplayedPart {

    /**
     * Updates the position of the displayed part
     * 
     * @param transform relative to which the part should be positioned
     */
    public void updatePosition(Matrix4f transform);

}
