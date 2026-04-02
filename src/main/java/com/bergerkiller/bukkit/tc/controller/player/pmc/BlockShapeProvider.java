package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;

interface BlockShapeProvider {
    /**
     * Returns the AxisAlignedBBHandle for the block at grid coordinates (x,y,z).
     * If the block is empty/air, return null.
     */
    AABBHandle getShape(int x, int y, int z);
}
