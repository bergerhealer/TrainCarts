package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

public abstract class RailLogicVerticalSlopeBase extends RailLogicSloped {

    public RailLogicVerticalSlopeBase(BlockFace direction, boolean upsideDown) {
        super(direction, upsideDown);
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
