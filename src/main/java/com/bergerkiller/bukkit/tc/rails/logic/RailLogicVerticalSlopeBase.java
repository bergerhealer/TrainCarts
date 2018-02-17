package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;

public abstract class RailLogicVerticalSlopeBase extends RailLogicSloped {

    public RailLogicVerticalSlopeBase(BlockFace direction, boolean upsideDown) {
        super(direction, upsideDown);
    }

    /**
     * Gets whether a particular y-position of the Minecart is the vertical portion of this vertical-slope
     * 
     * @param y
     * @param blockPos
     * @return True if vertical half
     */
    protected abstract boolean isVerticalHalf(double y, IntVector3 blockPos);

    /**
     * Gets the y-coordinate offset relative to the middle of the block,
     * where the rail logic changes between sloped and vertical.
     * 
     * @return half offset
     */
    protected abstract double getHalfOffset();

    /**
     * Gets the vertical offset of the Minecart on the sloped rail portion
     * 
     * @return y offset
     */
    protected double getYOffset() {
        return Y_POS_OFFSET;
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    protected boolean checkSlopeBlockCollisions() {
        return false;
    }

}
