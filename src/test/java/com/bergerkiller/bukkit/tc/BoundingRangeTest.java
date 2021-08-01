package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.utils.BoundingRange;

/**
 * Verifies correct behavior of the BoundingRange class
 */
public class BoundingRangeTest {

    @Test
    public void testAddConstant() {
        BoundingRange a = BoundingRange.create(10.0, 15.0);
        BoundingRange b = BoundingRange.create(2.0, 2.0);
        BoundingRange c = a.add(b);
        assertEquals(10.0, c.getMin(), 1e-10);
        assertEquals(17.0, c.getMax(), 1e-10);
        assertTrue(c.isInclusive());
    }

    @Test
    public void testAddRange() {
        BoundingRange a = BoundingRange.create(10.0, 15.0);
        BoundingRange b = BoundingRange.create(-2.0, 2.0);
        BoundingRange c = a.add(b);
        assertEquals(8.0, c.getMin(), 1e-10);
        assertEquals(17.0, c.getMax(), 1e-10);
        assertTrue(c.isInclusive());
    }

    @Test
    public void testAddToExclusive() {
        BoundingRange a = BoundingRange.create(10.0, 15.0).invert();
        BoundingRange b = BoundingRange.create(2.0, 2.0);
        BoundingRange c = a.add(b);
        assertEquals(10.0, c.getMin(), 1e-10);
        assertEquals(17.0, c.getMax(), 1e-10);
        assertTrue(c.isExclusive());
    }

    @Test
    public void testIsInsideInclusive() {
        BoundingRange range = BoundingRange.create(10.0, 15.0);
        assertTrue(range.isInside(10.0));
        assertTrue(range.isInside(12.5));
        assertTrue(range.isInside(15.0));
        assertFalse(range.isInside(9.5));
        assertFalse(range.isInside(15.5));
    }

    @Test
    public void testIsInsideExclusive() {
        BoundingRange range = BoundingRange.create(10.0, 15.0).invert();
        assertTrue(range.isInside(9.5));
        assertTrue(range.isInside(15.5));
        assertFalse(range.isInside(10.0));
        assertFalse(range.isInside(12.5));
        assertFalse(range.isInside(15.0));
    }

    @Test
    public void testDistanceInclusive() {
        BoundingRange range = BoundingRange.create(10.0, 15.0);
        assertEquals(0.0, range.distance(10.0), 1e-10);
        assertEquals(0.0, range.distance(12.5), 1e-10);
        assertEquals(0.0, range.distance(15.0), 1e-10);
        assertEquals(2.0, range.distance(8.0), 1e-10);
        assertEquals(2.0, range.distance(17.0), 1e-10);
    }

    @Test
    public void testDistanceExclusive() {
        BoundingRange range = BoundingRange.create(10.0, 15.0).invert();
        assertEquals(0.0, range.distance(8.0), 1e-10);
        assertEquals(0.0, range.distance(17.0), 1e-10);
        assertEquals(1.5, range.distance(11.5), 1e-10);
        assertEquals(1.5, range.distance(13.5), 1e-10);
    }
}
