package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.block.BlockFace;

/**
 * A single element used to represent a stationary collision box that players stand on or
 * are pushed by. This interface exposes purely the read-only state of this collision box,
 * which can be used for hit-testing.<br>
 * <br>
 * Basically represents the read-only state of a spawned shulker visible to the player.
 */
public interface StationaryCollisionElement {
    /**
     * Gets the bounding box of this collision element
     *
     * @return AxisAlignedBBHandle
     */
    AABBHandle getBoundingBox();

    /**
     * Gets the direction to push players during spawning if they are inside this stationary element.
     * Basically represents the collision normal.
     *
     * @return BlockFace push direction (intended)
     */
    BlockFace getPushDirection();
}
