package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

public class AnimationNodeParsingTest {

    @Test
    public void testSerializeAll() {
        AnimationNode node = new AnimationNode(
                new Vector(1.0, 2.0, 3.0),
                new Vector(10.0, 20.0, 30.0),
                false, 1.0, "MyScene");
        assertEquals("t=1.0 active=0 x=1.0 y=2.0 z=3.0 pitch=10.0 yaw=20.0 roll=30.0 scene=MyScene",
                node.serializeToString());
    }

    @Test
    public void testParseBasic() {
        AnimationNode node = AnimationNode.parseFromString("t=0.5 yaw=90.0 x=2.0");
        assertEquals(0.5, node.getDuration(), 1e-10);
        assertEquals(90.0, node.getRotationVector().getY(), 1e-10);
        assertEquals(2.0, node.getPosition().getX(), 1e-10);
    }

    @Test
    public void testParseAll() {
        AnimationNode node = AnimationNode.parseFromString("t=0.5 pitch=10.0 yaw=20.0 roll=30.0 x=1.0 y=2.0 z=3.0 scene=scene15");
        assertEquals(0.5, node.getDuration(), 1e-10);
        assertEquals(10.0, node.getRotationVector().getX(), 1e-10);
        assertEquals(20.0, node.getRotationVector().getY(), 1e-10);
        assertEquals(30.0, node.getRotationVector().getZ(), 1e-10);
        assertEquals(1.0, node.getPosition().getX(), 1e-10);
        assertEquals(2.0, node.getPosition().getY(), 1e-10);
        assertEquals(3.0, node.getPosition().getZ(), 1e-10);
        assertEquals("scene15", node.getSceneMarker());
    }

    @Test
    public void testSceneRemoveSpaces() {
        AnimationNode node = new AnimationNode(new Vector(), new Vector(), true, 1.0);
        node = node.setSceneMarker("hello, world\ttabs");
        assertEquals("hello,_world_tabs", node.getSceneMarker());
    }
}
