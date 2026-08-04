package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests {@link Util#compareChunkRelativePositions} to verify ordering is correct
 */
public class ChunkSpawnOrderTest {

    @Test
    public void testListSortOrder() {
        List<IntVector2> chunks = new ArrayList<>(Arrays.asList(
                new IntVector2(-2, -2),
                new IntVector2(1, 1),
                new IntVector2(0, 0)
        ));
        chunks.sort((c1, c2) -> Util.compareChunkRelativePositions(c1.x, c1.z, c2.x, c2.z));

        assertEquals("Chunks in sorted order with center first", Arrays.asList(
                new IntVector2(0, 0),
                new IntVector2(1, 1),
                new IntVector2(-2, -2)
        ), chunks);
    }

    @Test
    public void testEqual() {
        assertEquals(0, Util.compareChunkRelativePositions(0, 0, 0, 0));
        assertEquals(0, Util.compareChunkRelativePositions(1, 1, 1, 1));
        assertEquals(0, Util.compareChunkRelativePositions(-1, -1, -1, -1));
    }

    @Test
    public void testCenterFirst() {
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, 0, 2, 2) < 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(2, 2, 0, 0) > 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, 0, -2, -2) < 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(-2, -2, 0, 0) > 0);

        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, 0, 0, 1) < 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, 1, 0, 0) > 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, 0, 0, -1) < 0);
        assertTrue("Center sorted first", Util.compareChunkRelativePositions(0, -1, 0, 0) > 0);
    }

    @Test
    public void testDistanceSort() {
        assertTrue("Closest sorted first", Util.compareChunkRelativePositions(1, 1, 3, 3) < 0);
        assertTrue("Closest sorted first", Util.compareChunkRelativePositions(3, 3, 1, 1) > 0);
    }

    @Test
    public void testSameDistSortX() {
        assertTrue("Same distance sorted by X", Util.compareChunkRelativePositions(-1, 0, 1, 0) < 0);
        assertTrue("Same distance sorted by X", Util.compareChunkRelativePositions(1, 0, -1, 0) > 0);
    }

    @Test
    public void testSameDistSortZ() {
        assertTrue("Same distance sorted by Z", Util.compareChunkRelativePositions(0, -1, 0, 1) < 0);
        assertTrue("Same distance sorted by Z", Util.compareChunkRelativePositions(0, 1, 0, -1) > 0);
    }
}
