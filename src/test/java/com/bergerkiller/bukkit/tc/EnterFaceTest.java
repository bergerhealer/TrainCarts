package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailAABB;

public class EnterFaceTest {

    @Test
    public void testEnterFace() {
        // Standard 1x1x1 cube
        testFace(RailAABB.BLOCK, BlockFace.DOWN, 0.5, 0.5, 0.5, 0.0, -1.0, 0.0);
        testFace(RailAABB.BLOCK, BlockFace.UP, 0.5, 0.1, 0.5, 0.0, 1.0, 0.6);
        testFace(RailAABB.BLOCK, BlockFace.SOUTH, 0.5, 0.1, 0.5, 0.0, 0.1, 0.6);
        testFace(RailAABB.BLOCK, BlockFace.NORTH, 0.5, 0.1, 0.5, 0.0, 0.1, -0.6);
        testFace(RailAABB.BLOCK, BlockFace.UP, 0.5, 0.1, 0.1, 0.0, 0.1, -0.6);
        testFace(RailAABB.BLOCK, BlockFace.NORTH, 0.5, 0.1, 0.1, 0.0, -0.1, -0.6);
        testFace(RailAABB.BLOCK, BlockFace.DOWN, 0.5, 0.9, 0.1, 0.0, -0.1, -0.6);

        // Test odd shape also
        RailAABB aabb = new RailAABB(0,-2,0, 1,2,1);
        testFace(aabb, BlockFace.NORTH, 0.5, 0.5, 0.5, 0.0, -0.5, -0.5);
        testFace(aabb, BlockFace.EAST, 0.5, 0.5, 0.5, 0.5, -0.5, -0.3);
        testFace(aabb, BlockFace.DOWN, 0.5, 0.5, 0.5, 0.1, -0.9, -0.1);
        testFace(aabb, BlockFace.UP, 0.5, 0.5, 0.5, 0.1, 0.9, -0.1);
    }

    private void testFace(RailAABB aabb, BlockFace face, double px, double py, double pz, double dx, double dy, double dz) {
        for (double f = -10.0; f < 10.0; f += 0.01) {
            if (f >= -1e-6 && f <= 1e-6) continue; // skip 0
            double ppx = px + f * dx;
            double ppy = py + f * dy;
            double ppz = pz + f * dz;
            BlockFace result = aabb.calculateEnterFace(new Vector(ppx, ppy, ppz), new Vector(dx, dy, dz));
            if (result != face) {
                fail("testFace(" + ppx + ", " + ppy + ", " + ppz + ",   " + dx + ", " + dy + ", " + dz + ") = " + result + " expected=" + face);
            }
        }
    }

    @Test
    public void testVectorDiagonal() {
        assertFalse(Util.isDiagonal(new Vector(0.0, 0.0, 1.0)));
        assertFalse(Util.isDiagonal(new Vector(0.0, 0.0, -1.0)));
        assertFalse(Util.isDiagonal(new Vector(1.0, 0.0, 0.0)));
        assertFalse(Util.isDiagonal(new Vector(-1.0, 0.0, 0.0)));
        assertTrue(Util.isDiagonal(new Vector(MathUtil.HALFROOTOFTWO, 0.0, MathUtil.HALFROOTOFTWO)));
        assertTrue(Util.isDiagonal(new Vector(-MathUtil.HALFROOTOFTWO, 0.0, MathUtil.HALFROOTOFTWO)));
        assertTrue(Util.isDiagonal(new Vector(MathUtil.HALFROOTOFTWO, 0.0, -MathUtil.HALFROOTOFTWO)));
        assertTrue(Util.isDiagonal(new Vector(-MathUtil.HALFROOTOFTWO, 0.0, -MathUtil.HALFROOTOFTWO)));
    }
}
