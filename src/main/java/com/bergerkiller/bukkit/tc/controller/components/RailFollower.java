package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * An abstract implementation for something that follows along rails.
 * Rail logic uses this object to interface in order to perform the movement updates.
 * Besides normal minecarts, this interface is also implemented by the track moving iterator,
 * to walk over rails virtually without an actual minecart being there.
 */
public abstract class RailFollower {

    /**
     * Gets the rail type this follower is currently moving over
     * 
     * @return current rail type
     */
    public abstract RailType getRailType();

    /**
     * Gets the rails block matching the rail type this follower is currently moving over
     * 
     * @return current rail block
     */
    public abstract Block getRailBlock();

    /**
     * Gets the direction from which this follower entered the current rails
     * 
     * @return enter direction
     */
    public abstract BlockFace getDirectionFrom();

    /**
     * Gets the direction towards which this follower is currently moving
     * 
     * @return exit direction
     */
    public abstract BlockFace getDirectionTo();

    /**
     * Gets whether this rail follower flying through the air currently
     * 
     * @return True if flying
     */
    public abstract boolean isFlying();
}
