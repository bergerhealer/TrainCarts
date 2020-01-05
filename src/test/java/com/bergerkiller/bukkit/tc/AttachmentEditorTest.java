package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

public class AttachmentEditorTest {

    @Test
    public void testAnimationNodeTimeString() {
        assertEquals("0.0", Util.stringifyAnimationNodeTime(0.0));
        assertEquals("0.0", Util.stringifyAnimationNodeTime(1e-9));
        assertEquals("0.001", Util.stringifyAnimationNodeTime(0.001));
        assertEquals("9999", Util.stringifyAnimationNodeTime(10000));
        assertEquals("9999", Util.stringifyAnimationNodeTime(9999));
        assertEquals("5555", Util.stringifyAnimationNodeTime(5555));
        assertEquals("555.5", Util.stringifyAnimationNodeTime(555.5));
        assertEquals("55.55", Util.stringifyAnimationNodeTime(55.55));
        assertEquals("5.555", Util.stringifyAnimationNodeTime(5.555));
        assertEquals("0.555", Util.stringifyAnimationNodeTime(0.555));
        assertEquals("1234", Util.stringifyAnimationNodeTime(1234.5678)); //TODO: Shouldn't it be 1235?
        assertEquals("10.0", Util.stringifyAnimationNodeTime(10.000001));
        assertEquals("10.01", Util.stringifyAnimationNodeTime(10.010001));
        assertEquals("1.12", Util.stringifyAnimationNodeTime(1.120004));
        assertEquals("1.1", Util.stringifyAnimationNodeTime(1.1));
        assertEquals("1.1", Util.stringifyAnimationNodeTime(1.10000005));
    }
}
