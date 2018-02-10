package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.common.utils.MathUtil;

public class EnterFaceTest {

    @Test
    public void testEnterFace() {
        testFace(BlockFace.DOWN, 0.5, 0.5, 0.5, 0.0, -1.0, 0.0);
        testFace(BlockFace.UP, 0.5, 0.1, 0.5, 0.0, 1.0, 0.6);
        testFace(BlockFace.SOUTH, 0.5, 0.1, 0.5, 0.0, 0.1, 0.6);
        testFace(BlockFace.NORTH, 0.5, 0.1, 0.5, 0.0, 0.1, -0.6);
        testFace(BlockFace.UP, 0.5, 0.1, 0.1, 0.0, 0.1, -0.6);
        testFace(BlockFace.NORTH, 0.5, 0.1, 0.1, 0.0, -0.1, -0.6);
        testFace(BlockFace.DOWN, 0.5, 0.9, 0.1, 0.0, -0.1, -0.6);
    }

    private void testFace(BlockFace face, double px, double py, double pz, double dx, double dy, double dz) {
        for (double f = -10.0; f < 10.0; f += 0.01) {
            if (f >= -1e-6 && f <= 1e-6) continue; // skip 0
            double ppx = px + f * dx;
            double ppy = py + f * dy;
            double ppz = pz + f * dz;
            BlockFace result = Util.calculateEnterFace(new Vector(ppx, ppy, ppz), new Vector(dx, dy, dz));
            if (result != face) {
                fail("testFace(" + ppx + ", " + ppy + ", " + ppz + ",   " + dx + ", " + dy + ", " + dz + ") = " + result + " expected=" + face);
            }
        }
    }
}
