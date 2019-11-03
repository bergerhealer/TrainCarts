package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.utils.BlockIterator;

public class BlockIteratorTest {

    @Test
    public void testSegmentIterator() {
        RailPath.Point p0 = new RailPath.Point(0.45648946023063963,1.8923327746278384,1.770718648404808);
        RailPath.Point p1 = new RailPath.Point(4.093611166091037,4.381399987094868,3.3969002901065504);
        RailPath.Segment segment = new RailPath.Segment(p0, p1);
        BlockIterator iter = new BlockIterator(segment, new IntVector3(0, 0, 0));
        assertTrue(iter.next());
        assertEquals(new IntVector3(0, 1, 1), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(0, 2, 1), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(0, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(1, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(2, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(2, 3, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 3, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 3, 3), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 4, 3), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(4, 4, 3), iter.block());
        assertFalse(iter.next());
    }

    @Test
    public void testPointIterator() {
        Vector pos = new Vector(200.3, -50.2, 20.523);
        Vector dir = new Vector(0.2, 0.6, -2.3).normalize();
        BlockIterator iter = new BlockIterator(pos.getX(), pos.getY(), pos.getZ(),
                                               dir.getX(), dir.getY(), dir.getZ(),
                                               10.0);
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -51, 20), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -51, 19), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -50, 19), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -50, 18), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -50, 17), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -50, 16), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -50, 15), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -49, 15), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -49, 14), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -49, 13), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(200, -49, 12), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(201, -49, 12), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(201, -48, 12), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(201, -48, 11), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(201, -48, 10), iter.block());
        assertFalse(iter.next());
    }
}
