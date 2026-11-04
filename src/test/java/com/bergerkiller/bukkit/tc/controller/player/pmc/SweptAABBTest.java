package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.block.BlockFace;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SweptAABBTest {

    private static AABBHandle player(double x, double z) {
        return AABBHandle.createNew(x - 0.3, 5.0, z - 0.3, x + 0.3, 6.8, z + 0.3);
    }

    private static AABBHandle block(int x, int z) {
        return AABBHandle.createNew(x, 5.0, z, x + 1.0, 6.0, z + 1.0);
    }

    @Test
    public void testSlideAlongWallIgnoresFlushNeighbourBlock() {
        // Player flush against the west face (x=-273) of a wall, walking along it (-z).
        // The block further along the wall is only touched on the x axis, never overlapped.
        SweptAABB.CollisionResult result = SweptAABB.sweepTest(
                player(-273.3, 317.3), player(-273.3, 317.2),
                block(-273, 316), -273, 5, 316);
        assertNull(result);
    }

    @Test
    public void testWalkIntoNeighbourBlockStillCollides() {
        SweptAABB.CollisionResult result = SweptAABB.sweepTest(
                player(-273.1, 317.3), player(-273.1, 317.2),
                block(-273, 316), -273, 5, 316);
        assertNotNull(result);
        assertEquals(BlockFace.SOUTH, result.face);
    }

    @Test
    public void testWalkIntoWallCollides() {
        SweptAABB.CollisionResult result = SweptAABB.sweepTest(
                player(-273.5, 317.5), player(-273.2, 317.5),
                block(-273, 317), -273, 5, 317);
        assertNotNull(result);
        assertEquals(BlockFace.WEST, result.face);
    }
}
