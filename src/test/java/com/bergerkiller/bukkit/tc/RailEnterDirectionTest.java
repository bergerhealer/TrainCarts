package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.junit.Test;

import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirection;

/**
 * Tests the correct parsing of RailEnterDirection arrays from strings
 */
public class RailEnterDirectionTest {

    @Test
    public void testAbsoluteDirections() {
        testParseDirections("", BlockFace.NORTH);
        testParseDirections("*", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
        testParseDirections("all", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
        testParseDirections("ALL", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
        testParseDirections("north", BlockFace.NORTH, BlockFace.NORTH);
        testParseDirections("n", BlockFace.NORTH, BlockFace.NORTH);
        testParseDirections("up", BlockFace.NORTH, BlockFace.UP);
        testParseDirections("u", BlockFace.NORTH, BlockFace.UP);
        testParseDirections("nesw", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
        testParseDirections("ud", BlockFace.NORTH, BlockFace.UP, BlockFace.DOWN);
    }

    @Test
    public void testRelativeDirections() {
        testParseDirections("left", BlockFace.NORTH, BlockFace.EAST);
        testParseDirections("forward", BlockFace.NORTH, BlockFace.SOUTH);
        testParseDirections("lr", BlockFace.EAST, BlockFace.SOUTH, BlockFace.NORTH);
        testParseDirections("fb", BlockFace.EAST, BlockFace.WEST, BlockFace.EAST);
    }

    @Test
    public void testInvalids() {
        testParseDirections("X", BlockFace.NORTH);
        testParseDirections("neX", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST);
        testParseDirections("Xne", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST);
        testParseDirections("nXe", BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST);
    }

    @Test
    public void testWithJunctions() {
        RailJunction j1 = new RailJunction("1", new RailPath.Position());
        RailJunction j2 = new RailJunction("2", new RailPath.Position());
        RailJunction j3 = new RailJunction("3", new RailPath.Position());
        RailPiece dummyRail = new RailPiece() {
            @Override
            public List<RailJunction> getJunctions() {
                return Arrays.asList(j1, j2, j3);
            }
        };

        testParseJunctions("1", dummyRail, j1);
        testParseJunctions("2", dummyRail, j2);
        testParseJunctions("3", dummyRail, j3);
        testParseJunctions("123", dummyRail, j1, j2, j3);
        testParseJunctions("321", dummyRail, j3, j2, j1);
    }

    @Test
    public void testMixed() {
        RailJunction j1 = new RailJunction("1", new RailPath.Position());
        RailJunction j2 = new RailJunction("2", new RailPath.Position());
        RailJunction j3 = new RailJunction("3", new RailPath.Position());
        RailPiece dummyRail = new RailPiece() {
            @Override
            public List<RailJunction> getJunctions() {
                return Arrays.asList(j1, j2, j3);
            }
        };

        testParse("n1e", dummyRail, BlockFace.NORTH,
                RailEnterDirection.toFace(BlockFace.NORTH),
                RailEnterDirection.fromJunction(j1),
                RailEnterDirection.toFace(BlockFace.EAST));
        testParse("n3e", dummyRail, BlockFace.NORTH,
                RailEnterDirection.toFace(BlockFace.NORTH),
                RailEnterDirection.fromJunction(j3),
                RailEnterDirection.toFace(BlockFace.EAST));
        testParse("1n", dummyRail, BlockFace.NORTH,
                RailEnterDirection.fromJunction(j1),
                RailEnterDirection.toFace(BlockFace.NORTH));
        testParse("w2", dummyRail, BlockFace.NORTH,
                RailEnterDirection.toFace(BlockFace.WEST),
                RailEnterDirection.fromJunction(j2));
        testParse("l2r3", dummyRail, BlockFace.WEST,
                RailEnterDirection.toFace(BlockFace.NORTH),
                RailEnterDirection.fromJunction(j2),
                RailEnterDirection.toFace(BlockFace.SOUTH),
                RailEnterDirection.fromJunction(j3));
    }

    private void testParseJunctions(String text, RailPiece rail, RailJunction... junctions) {
        RailEnterDirection[] expected = new RailEnterDirection[junctions.length];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = RailEnterDirection.fromJunction(junctions[i]);
        }
        testParse(text, rail, BlockFace.NORTH, expected);
    }

    private void testParseDirections(String text, BlockFace forwardDirection, BlockFace... expectedFaces) {
        RailEnterDirection[] expected = new RailEnterDirection[expectedFaces.length];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = RailEnterDirection.toFace(expectedFaces[i]);
        }
        testParse(text, RailPiece.NONE, forwardDirection, expected);
    }

    private void testParse(String text, RailPiece rail, BlockFace forwardDirection, RailEnterDirection... expected) {
        RailEnterDirection[] actual = RailEnterDirection.parseAll(rail, forwardDirection, text);
        assertArrayEquals(expected, actual);
    }
}
