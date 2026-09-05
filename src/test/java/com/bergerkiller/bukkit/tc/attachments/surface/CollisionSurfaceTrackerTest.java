package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollisionSurfaceTrackerTest {
    @Test
    public void testUphillWalkSpeedFactorUnaffectedUpToNormalWalkAngle() {
        assertEquals(1.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(0.0), 1e-12);
        assertEquals(1.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(30.0), 1e-12);
        assertEquals(1.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(45.0), 1e-12);
    }

    @Test
    public void testUphillWalkSpeedFactorFallsOffBetweenNormalAndMaxAngles() {
        // Under the new behavior, speed factor no longer falls off; it remains 1.0 until MAX_INPUT_ANGLE
        assertEquals(1.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(52.5), 1e-12);
        assertEquals(1.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(56.25), 1e-12);
    }

    @Test
    public void testUphillWalkSpeedFactorZeroAtAndBeyondMaxAngle() {
        // Factor is disabled at and beyond MAX_INPUT_ANGLE
        assertEquals(0.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(80.0), 1e-12);
        assertEquals(0.0, CollisionSurfaceTracker.computeUphillWalkSpeedFactor(85.0), 1e-12);
    }

    @Test
    public void testRotateWalkingVelocityFollowsSurfaceTransitionRotation() {
        OBBSurfaceState from = createSurface(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceState to = createSurface(new Vector(1.0, 0.0, 0.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceTransition<String> fromTo = new OBBSurfaceTransition<>(from, to);
        Vector velocity = from.xAxis.clone().multiply(1.5).add(from.zAxis.clone().multiply(2.0));

        Vector result = fromTo.transformMovement(velocity);
        Vector expected = to.xAxis.clone().multiply(1.5).add(to.zAxis.clone().multiply(2.0));

        assertEquals(expected.getX(), result.getX(), 1e-9);
        assertEquals(expected.getY(), result.getY(), 1e-9);
        assertEquals(expected.getZ(), result.getZ(), 1e-9);
    }

    @Test
    public void testComputeSurfaceCarryVelocityIncludesTranslation() {
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(new Vector(1.0, 2.0, 3.0), new Vector(4.0, 0.2, 4.0),
                        Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0))),
                new OrientedBoundingBox(new Vector(4.0, 6.0, 8.0), new Vector(4.0, 0.2, 4.0),
                        Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0))),
                "surface"
        );

        Vector result = CollisionSurfaceTracker.computeSurfaceCarryVelocity(surface, new Vector(0.5, 4.0, -0.25));

        assertEquals(3.0, result.getX(), 1e-9);
        assertEquals(4.0, result.getY(), 1e-9);
        assertEquals(5.0, result.getZ(), 1e-9);
    }

    @Test
    public void testComputeSurfaceCarryVelocityIncludesRotationAroundLocalPoint() {
        OBBSurfaceState from = createSurface(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceState to = createSurface(new Vector(1.0, 0.0, 0.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(from.center, from.halfSize.clone().multiply(2.0), from.orientation),
                new OrientedBoundingBox(to.center, to.halfSize.clone().multiply(2.0), to.orientation),
                "surface"
        );
        Vector localPoint = new Vector(1.0, 999.0, 0.5);

        Vector result = CollisionSurfaceTracker.computeSurfaceCarryVelocity(surface, localPoint);
        Vector expected = to.localToWorld(new Vector(1.0, 0.0, 0.5), new Vector())
                .subtract(from.localToWorld(new Vector(1.0, 0.0, 0.5), new Vector()));

        assertEquals(expected.getX(), result.getX(), 1e-9);
        assertEquals(expected.getY(), result.getY(), 1e-9);
        assertEquals(expected.getZ(), result.getZ(), 1e-9);
    }

    @Test
    public void testShouldStartJumpOnlyOnRisingEdge() {
        assertTrue(CollisionSurfaceTracker.shouldStartJump(false,
                AttachmentViewer.Input.of(false, false, false, false, true, false, false)));
        assertFalse(CollisionSurfaceTracker.shouldStartJump(true,
                AttachmentViewer.Input.of(false, false, false, false, true, false, false)));
        assertFalse(CollisionSurfaceTracker.shouldStartJump(false,
                AttachmentViewer.Input.of(false, false, false, false, true, true, false)));
        assertFalse(CollisionSurfaceTracker.shouldStartJump(false,
                AttachmentViewer.Input.of(false, false, false, false, false, false, false)));
    }

    @Test
    public void testRemoveIntoSurfaceVelocityForFeetCollision() {
        Vector result = CollisionSurfaceTracker.removeIntoSurfaceVelocity(
                new Vector(0.2, -1.5, 0.3),
                PlayerCollisionSolver.CollisionMode.FEET,
                createFlatSurface()
        );

        assertEquals(0.2, result.getX(), 1e-9);
        assertEquals(0.0, result.getY(), 1e-9);
        assertEquals(0.3, result.getZ(), 1e-9);
    }

    @Test
    public void testRemoveIntoSurfaceVelocityForHeadCollision() {
        Vector result = CollisionSurfaceTracker.removeIntoSurfaceVelocity(
                new Vector(-0.4, 1.25, 0.1),
                PlayerCollisionSolver.CollisionMode.HEAD,
                createFlatSurface()
        );

        assertEquals(-0.4, result.getX(), 1e-9);
        assertEquals(0.0, result.getY(), 1e-9);
        assertEquals(0.1, result.getZ(), 1e-9);
    }

    @Test
    public void testRemoveIntoSurfaceVelocityForWallCollision() {
        OBBSurfaceState wall = createSurface(new Vector(0.0, 0.0, 1.0), new Vector(1.0, 0.0, 0.0));
        Vector result = CollisionSurfaceTracker.removeIntoSurfaceVelocity(
                new Vector(1.5, 0.4, -0.25),
                PlayerCollisionSolver.CollisionMode.WALL,
                wall
        );

        assertEquals(0.0, result.getX(), 1e-9);
        assertEquals(0.4, result.getY(), 1e-9);
        assertEquals(-0.25, result.getZ(), 1e-9);
    }

    @Test
    public void testUphillVelocityTooSteepCheck() {
        assertFalse(CollisionSurfaceTracker.isUphillVelocityTooSteep(new Vector(1.0, 0.0, 0.0)));
        assertFalse(CollisionSurfaceTracker.isUphillVelocityTooSteep(new Vector(1.0, 1.0, 0.0)));
        // Use a vector that corresponds to ~81 degrees uphill to exceed the new MAX_INPUT_ANGLE (80 deg)
        assertTrue(CollisionSurfaceTracker.isUphillVelocityTooSteep(new Vector(0.156434465, 0.987688340, 0.0)));
    }

    private static OBBSurfaceState createFlatSurface() {
        return createSurface(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
    }

    private static OBBSurfaceState createSurface(Vector forward, Vector up) {
        Quaternion orientation = Quaternion.fromLookDirection(forward, up);
        return new OBBSurfaceState(new OrientedBoundingBox(
                new Vector(0.0, 0.0, 0.0),
                new Vector(10.0, 0.2, 10.0),
                orientation
        ));
    }
}
