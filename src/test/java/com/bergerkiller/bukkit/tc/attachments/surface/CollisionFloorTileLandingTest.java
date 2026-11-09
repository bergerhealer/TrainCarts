package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class CollisionFloorTileLandingTest {
    private static AABBHandle box(double top) {
        return AABBHandle.createNew(0.0, top - 1.0, 0.0, 1.0, top, 1.0);
    }

    @Test
    public void detectsFirstTopCrossed() {
        Vector from = new Vector(0.5, 5.0, 0.5);
        Vector to = new Vector(0.5, 0.0, 0.5);
        assertEquals(0.2, CollisionFloorTileGrid.findLanding(from, to, box(4.0), null).theta, 1e-10);
        assertEquals(0.6, CollisionFloorTileGrid.findLanding(from, to, box(2.0), null).theta, 1e-10);
    }

    @Test
    public void interpolatesHorizontalPositionAndPlayerWidth() {
        Vector from = new Vector(-1.0, 2.0, 0.5);
        Vector to = new Vector(1.0, 0.0, 0.5);
        CollisionFloorTileGrid.Landing landing = CollisionFloorTileGrid.findLanding(
                from, to, box(1.0), CollisionSurfaceMaterial.DEFAULT);
        assertEquals(0.5, landing.theta, 1e-10);
        assertEquals(new Vector(0.0, 1.0, 0.5), landing.position);
        assertSame(CollisionSurfaceMaterial.DEFAULT, landing.material);
        assertNull(CollisionFloorTileGrid.findLanding(
                new Vector(-1.0, 2.0, 1.31), new Vector(1.0, 0.0, 1.31), box(1.0), null));
        assertEquals(0.5, CollisionFloorTileGrid.findLanding(
                new Vector(1.29, 2.0, 0.5), new Vector(1.29, 0.0, 0.5), box(1.0), null).theta, 1e-10);
    }

    @Test
    public void ignoresAscendingAndNonCrossingMovement() {
        assertNull(CollisionFloorTileGrid.findLanding(
                new Vector(0.5, 0.0, 0.5), new Vector(0.5, 2.0, 0.5), box(1.0), null));
        assertNull(CollisionFloorTileGrid.findLanding(
                new Vector(0.5, 3.0, 0.5), new Vector(0.5, 2.0, 0.5), box(1.0), null));
    }
}
