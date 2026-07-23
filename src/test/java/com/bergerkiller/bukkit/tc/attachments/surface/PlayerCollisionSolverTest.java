package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PlayerCollisionSolverTest {
    private static final double EDGE_MARGIN = 1.0;
    // Turn on to debug failing tests
    private static final boolean ENABLE_LOGGING = true;
    // Test-wide logger instance used to construct PlayerCollisionSolver instances in tests
    private static final PlayerCollisionLogger TEST_LOGGER = new PlayerCollisionLoggerImpl();
    // Single solver instance used by all tests to avoid repeated construction
    private static final PlayerCollisionSolver TEST_SOLVER = new PlayerCollisionSolver(ENABLE_LOGGING ? TEST_LOGGER :  PlayerCollisionLogger.DISABLED);

    /**
     * In this test case, the player is hugging a wall while walking on a flat surface. The wall and surface is moving slowly.
     * The player walks into the wall. The player should not clip through the moving wall. This likely can break
     * because of mis-detecting what side of the wall the player is on.
     */
    @Test
    public void testMovingPlatformWithWallNotClippingThrough() {
        // Player is moving into the wall along the X-axis. The Y/Z axis are not too important.
        // The minimum X of the player is against the wall X at the start
        // The maximum X of the player is away from the wall
        PlayerBoundsTransition playerTransition = new PlayerBoundsTransition(
                new PlayerBoundsState(
                        AABBHandle.createNew(
                                -335.6803333333333, 12.3625, 306.5248941984462,
                                -335.08033330949144, 14.162499952316285, 307.12489422228805
                        )
                ),
                new PlayerBoundsState(
                        AABBHandle.createNew(
                                -335.63276189745926, 12.3625, 306.4249236924142,
                                -335.0327618736174, 14.162499952316285, 307.02492371625607
                        )
                )
        );

        List<OBBSurfaceTransition<String>> transitions = Arrays.asList(
                /* Wall */
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-335.6803333333333, 16.0225, 304.04),
                                new Quaternion(0.5, -0.5, 0.5, 0.5000000000000001),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-335.6303333333333, 16.0225, 304.04),
                                new Quaternion(0.5, -0.5, 0.5, 0.5000000000000001),
                                new Vector(8.0, 0.0, 8.0)
                        )
                ),
                /* Flat surface players walks on */
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-335.6803333333333, 16.0225, 304.04),
                                new Quaternion(0.5, -0.5, 0.5, 0.5000000000000001),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-335.6303333333333, 16.0225, 304.04),
                                new Quaternion(0.5, -0.5, 0.5, 0.5000000000000001),
                                new Vector(8.0, 0.0, 8.0)
                        )
                )
        );

        PlayerCollisionSolver.Result<String> solution = TEST_SOLVER.solveDetailed(transitions, playerTransition);

        System.out.println(solution);

        // We expect the minimum X-coordinate of the player to be clamped against the wall surface
        // The other end of the player should be away from the surface
        assertEquals(-335.6303333333333, solution.bounds.getMinX(), 1e-6);
    }

    /**
     * Simplified version of testMovingPlatformWithWallNotClippingThrough:
     * a player has their min-X face flush against a vertical wall (normal = (-1,0,0))
     * and the wall moves 0.05 in the +X direction. With two surfaces in the list
     * (triggering the multi-surface stabilisation pass), the solver must still align
     * the player's min-X against the wall's final position – NOT push the whole box
     * through to the opposite (positive) side so that max-X ends up at the wall instead.
     */
    @Test
    public void testStraightNotClippingThrough() {
        // Normal = (-1, 0, 0): positive side is X < center.x (left), negative side is X > center.x (right).
        // Player body is entirely on the negative side; only their min-X face touches the wall.
        Quaternion wallOri = new Quaternion(0.5, -0.5, 0.5, 0.5000000000000001); // normal = (-1, 0, 0)
        OBBSurfaceTransition<String> movingWall = new OBBSurfaceTransition<>(
                new OBBSurfaceState(new Vector(-1.0, 5.0, 0.0), wallOri, new Vector(10.0, 0.0, 10.0)),
                new OBBSurfaceState(new Vector(-0.95, 5.0, 0.0), wallOri, new Vector(10.0, 0.0, 10.0))
        );

        double halfWidth = 0.3;
        double height = 1.8;

        // Player from: center at (-0.7, 0, 0) → minX = -1.0 (exactly at the from-wall X), maxX = -0.4
        AABBHandle playerFrom = createAabb(new Vector(-0.7, 0.0, 0.0), halfWidth, height);
        // Player to:  minX = -0.9525 – 0.0025 past the to-wall position of -0.95 (should be clamped)
        AABBHandle playerTo = createAabb(new Vector(-0.6525, 0.0, 0.0), halfWidth, height);

        // Two identical surface transitions to force the multi-surface stabilisation pass.
        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                Arrays.asList(movingWall, movingWall),
                new PlayerBoundsTransition(playerFrom, playerTo)
        );

        System.out.println(result);

        // The player's min-X must be clamped to the wall's final position (-0.95).
        // Before the fix, the solver incorrectly pushed the player to the positive side so
        // that max-X ended up at -0.95 and min-X was around -1.55.
        assertEquals("Player min-X should be blocked by the wall at its final position",
                -0.95, result.bounds.getMinX(), 1e-4);
    }

    @Test
    public void testLandingOnSlopedSurface() {
        PlayerBoundsTransition playerTransition = new PlayerBoundsTransition(
                new PlayerBoundsState(
                        AABBHandle.createNew(
                                -320.3654565854123, 7.353226808645175, 303.6580557077583,
                                -319.76545656157043, 9.15322676096146, 304.25805573160017
                        )
                ),
                new PlayerBoundsState(
                        AABBHandle.createNew(
                                -320.2072035024243, 7.087586780819734, 303.6580557077583,
                                -319.60720347858245, 8.887586733136018, 304.25805573160017
                        )
                )
        );

        OBBSurfaceState surface = new OBBSurfaceState(
                new Vector(-319.61454511405094, 6.917938653774046, 304.04),
                new Quaternion(0.0, 0.0, -0.25966187574449157, 0.9656995962952725),
                new Vector(10.0, 0.0, 3.13)
        );

        OBBSurfaceTransition<String> transition = new  OBBSurfaceTransition<>(surface, surface);

        assertFalse(surface.isClippingThrough(playerTransition.from));

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                Collections.singletonList(transition),
                playerTransition);

        assertFalse(surface.isClippingThrough(new PlayerBoundsState(result.bounds)));

        System.out.println(result);

        assertFalse(transition.hasCornerPassedThrough(new PlayerBoundsTransition(playerTransition.from, new PlayerBoundsState(result.bounds))));
    }

    /**
     * Verify that the isClippingThrough logic is properly bounded by the surface size.
     */
    @Test
    public void testClippingThroughBounded() {
        OBBSurfaceState surface =  new OBBSurfaceState(
                new Vector(-349.5, 14.23407287525381, 301.6715728752538),
                new Quaternion(0.3826834323650898, 0.0, 0.0, 0.9238795325112867),
                new Vector(8.0, 0.0, 8.0)
        );

        PlayerBoundsState playerState = new PlayerBoundsState(
                AABBHandle.createNew(
                        -313.0458744414228, -0.4874999999999987, 315.9890660663131,
                        -312.44587441758097, 1.3124999523162855, 316.589066090155
                )
        );

        assertFalse(surface.isClippingThrough(playerState));
    }

    @Test
    public void testFallOntoFlatSurface() {
        Vector position = new Vector(100, 53.2, -40.3);

        // Surface is a large flat plane centered at y=5.0
        Vector surfCenter = position.clone().add(new Vector(0.0, 5.0, 0.0));
        Vector surfSize = new Vector(10.0, 0.2, 10.0); // Y ignored for flat plane
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0)); // flat horizontal
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<?> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        // Player bounds: typical Minecraft player ~0.6 width, 1.8 height
        double halfWidth = 0.3;
        double height = 1.8;

        // Starting above the surface
        double fromFeetY = 6.0; // feet at y=6.0 -> above plane at y=5.0
        AABBHandle from = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + fromFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + fromFeetY + height,position.getZ() + halfWidth
        );

        // Ending below the surface (simulate falling through if unconstrained)
        double toFeetY = 4.0; // feet below plane
        AABBHandle to = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + toFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + toFeetY + height, position.getZ() + halfWidth
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertFeetNotBelowSurface("Player passed through flat surface", surface.from, result);
    }

    @Test
    public void testFallOntoUpsideDownFlatSurface() {
        Vector position = new Vector(100, 53.2, -40.3);

        // Surface is a large flat plane centered at y=5.0 but upside-down (normal pointing down)
        Vector surfCenter = position.clone().add(new Vector(0.0, 5.0, 0.0));
        Vector surfSize = new Vector(10.0, 0.2, 10.0); // Y ignored for flat plane
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, -1.0, 0.0)); // inverted normal
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<?> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        // Player bounds: typical Minecraft player ~0.6 width, 1.8 height
        double halfWidth = 0.3;
        double height = 1.8;

        // Starting above the surface
        double fromFeetY = 6.0; // feet at y=6.0 -> above plane at y=5.0
        AABBHandle from = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + fromFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + fromFeetY + height,position.getZ() + halfWidth
        );

        // Ending below the surface (simulate falling through if unconstrained)
        double toFeetY = 4.0; // feet below plane
        AABBHandle to = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + toFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + toFeetY + height, position.getZ() + halfWidth
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        // Upside-down surfaces should still support players walking on them. The solver
        // must clamp the player's feet above the plane just like a regular (upright)
        // surface, even when the surface normal is inverted.
        assertFeetNotBelowSurface("Player passed through upside-down flat surface", surface.from, result);
    }

    @Test
    public void testDetailedResultIncludesLastCollidingSurface() {
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        String source = "flat-surface";
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(surfCenter, surfSize, surfOri),
                new OrientedBoundingBox(surfCenter, surfSize, surfOri),
                source
        );

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(0.0, 4.0, 0.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        assertEquals("Detailed result should report the last colliding surface source", source, result.lastSurface);
        assertEquals("Detailed result should report a feet collision", PlayerCollisionSolver.CollisionMode.FEET, result.lastCollisionMode);
        assertNotNull("Detailed result should include the surface from state", result.lastSurfaceFromState);
        assertNotNull("Detailed result should include the surface to state", result.lastSurfaceToState);
        assertFeetNotBelowSurface("Detailed result bounds should still resolve the collision", surface.from, result.bounds);
    }

    @Test
    public void testPassesThroughSurfaceDetectsSideCrossing() {
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(surfCenter, surfSize, surfOri),
                new OrientedBoundingBox(surfCenter, surfSize, surfOri)
        );

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle fromAbove = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height);
        AABBHandle toBelow = createAabb(new Vector(0.0, 2.0, 0.0), halfWidth, height);
        AABBHandle toAbove = createAabb(new Vector(0.0, 7.0, 0.0), halfWidth, height);

        assertTrue("Moving from above to below should count as passing through the surface",
                passesThroughSurface(surface.from, fromAbove, toBelow));
        assertFalse("Moving from above to above should not count as passing through the surface",
                passesThroughSurface(surface.from, fromAbove, toAbove));
    }

    @Test
    public void testRiseIntoFlatSurface() {
        // Surface is a large flat plane centered at y=5.0
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0)); // flat horizontal
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        double halfWidth = 0.3;
        double height = 1.8;

        // Starting below the surface (player entirely below plane)
        double fromFeetY = 3.0; // feet below plane
        AABBHandle from = AABBHandle.createNew(
                -halfWidth, fromFeetY, -halfWidth,
                halfWidth, fromFeetY + height, halfWidth
        );

        // Ending above the surface (simulate jumping up into it)
        double toFeetY = 6.0; // feet above plane
        AABBHandle to = AABBHandle.createNew(
                -halfWidth, toFeetY, -halfWidth,
                halfWidth, toFeetY + height, halfWidth
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertHeadNotAboveSurface("Player's head passed through flat surface", surface.from, result);
    }

    @Test
    public void testRiseIntoUpsideDownFlatSurface() {
        // Upside-down surface should act as a ceiling and block the player's head when rising into it
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        // Inverted normal (upside-down)
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, -1.0, 0.0));
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(0.0, 3.0, 0.0), halfWidth, height); // start below the plane
        AABBHandle to = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height); // end above the plane

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertHeadNotAboveSurface("Upside-down flat surface should block the player's head (act as a ceiling)", surface.from, result);
    }

    @Test
    public void testFallOntoFlatSurfaceRemainsBlockedNextTick() {
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(surfCenter, surfSize, surfOri),
                new OrientedBoundingBox(surfCenter, surfSize, surfOri)
        );

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(0.0, 4.0, 0.0), halfWidth, height);

        AABBHandle resolved = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(from, to));
        AABBHandle nextTickAttempt = translateAabb(resolved, 0.0, -0.2, 0.0);
        AABBHandle nextTickResolved = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(resolved, nextTickAttempt));

        assertFeetNotBelowSurface("Player should remain above the flat surface on the next tick after being corrected", surface.from, nextTickResolved);
    }

    @Test
    public void testRiseIntoFlatSurfaceRemainsBlockedNextTick() {
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(
                new OrientedBoundingBox(surfCenter, surfSize, surfOri),
                new OrientedBoundingBox(surfCenter, surfSize, surfOri)
        );

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(0.0, 3.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height);

        AABBHandle resolved = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(from, to));
        AABBHandle nextTickAttempt = translateAabb(resolved, 0.0, 0.2, 0.0);
        AABBHandle nextTickResolved = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(resolved, nextTickAttempt));

        assertHeadNotAboveSurface("Player should remain below the flat surface on the next tick after being corrected", surface.from, nextTickResolved);
    }

    @Test
    public void testMovingFlatSurfaceUpThroughStationaryPlayerMovesPlayerAlong() {
        OBBSurfaceTransition<String> surface = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 4.0, 6.0, 10.0);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle playerBounds = createAabb(new Vector(0.0, 5.0, 0.0), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        assertFeetNotBelowSurface("Rising platform should carry the stationary player upward", surface.to, result);
        assertEquals("Rising platform should preserve player width", playerBounds.getMinX(), result.getMinX(), 1e-9);
        assertEquals("Rising platform should preserve player width", playerBounds.getMaxX(), result.getMaxX(), 1e-9);
        assertEquals("Rising platform should preserve player depth", playerBounds.getMinZ(), result.getMinZ(), 1e-9);
        assertEquals("Rising platform should preserve player depth", playerBounds.getMaxZ(), result.getMaxZ(), 1e-9);
        assertEquals("Rising platform should preserve player height",
                playerBounds.getMaxY() - playerBounds.getMinY(),
                result.getMaxY() - result.getMinY(),
                1e-9);
        assertTrue("Rising platform should move the player upward", result.getMinY() > playerBounds.getMinY() + 0.5);
    }

    @Test
    public void testMovingFlatSurfaceUpThroughStationaryPlayerContinuesPushingNextTick() {
        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle playerBounds = createAabb(new Vector(0.0, 5.0, 0.0), halfWidth, height);

        OBBSurfaceTransition<String> first = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 4.0, 6.0, 10.0);
        AABBHandle firstResult = TEST_SOLVER.solve(
                java.util.Collections.singletonList(first),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        OBBSurfaceTransition<String> second = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 6.0, 8.0, 10.0);
        AABBHandle secondResult = TEST_SOLVER.solve(
                java.util.Collections.singletonList(second),
                new PlayerBoundsTransition(firstResult, firstResult)
        );

        assertFeetNotBelowSurface("Rising platform should continue pushing the stationary player on the next tick", second.to, secondResult);
        assertTrue("Second tick should move the player further upward", secondResult.getMinY() > firstResult.getMinY() + 0.5);
    }

    @Test
    public void testMovingFlatSurfaceDownThroughStationaryPlayerPushesPlayerDown() {
        OBBSurfaceTransition<String> surface = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 7.0, 4.0, 10.0);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle playerBounds = createAabb(new Vector(0.0, 5.0, 0.0), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        assertHeadNotAboveSurface("Descending platform should push the stationary player down when it hits their head", surface.to, result);
        assertEquals("Descending platform should preserve player width", playerBounds.getMinX(), result.getMinX(), 1e-9);
        assertEquals("Descending platform should preserve player width", playerBounds.getMaxX(), result.getMaxX(), 1e-9);
        assertEquals("Descending platform should preserve player depth", playerBounds.getMinZ(), result.getMinZ(), 1e-9);
        assertEquals("Descending platform should preserve player depth", playerBounds.getMaxZ(), result.getMaxZ(), 1e-9);
        assertEquals("Descending platform should preserve player height",
                playerBounds.getMaxY() - playerBounds.getMinY(),
                result.getMaxY() - result.getMinY(),
                1e-9);
        assertTrue("Descending platform should move the player downward", result.getMinY() < playerBounds.getMinY() - 0.5);
    }

    @Test
    public void testMovingFlatSurfaceDownThroughStationaryPlayerContinuesPushingNextTick() {
        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle playerBounds = createAabb(new Vector(0.0, 5.0, 0.0), halfWidth, height);

        OBBSurfaceTransition<String> first = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 7.0, 4.0, 10.0);
        AABBHandle firstResult = TEST_SOLVER.solve(
                java.util.Collections.singletonList(first),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        OBBSurfaceTransition<String> second = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 4.0, 2.0, 10.0);
        AABBHandle secondResult = TEST_SOLVER.solve(
                java.util.Collections.singletonList(second),
                new PlayerBoundsTransition(firstResult, firstResult)
        );

        assertHeadNotAboveSurface("Descending platform should continue pushing the stationary player down on the next tick", second.to, secondResult);
        assertTrue("Second tick should move the player further downward", secondResult.getMinY() < firstResult.getMinY() - 0.5);
    }

    @Test
    public void testMoving45SurfaceUpThroughStationaryPlayerMovesPlayerAlong() {
        OBBSurfaceTransition<String> surface = create45DegreeSurfaceTransition(new Vector(-40.0, 50.0, -60.0), 4.0, 6.0, 50.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector point = interiorPoint(surface, surface.from.halfSize.getZ() * 0.35);
        AABBHandle playerBounds = createAabb(new Vector(point.getX(), point.getY() + 1.0, point.getZ()), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        assertFeetNotBelowSurface("Rising 45 degree platform should carry the stationary player upward", surface.to, result);
        assertEquals("Rising 45 degree platform should preserve player width", playerBounds.getMinX(), result.getMinX(), 1e-9);
        assertEquals("Rising 45 degree platform should preserve player width", playerBounds.getMaxX(), result.getMaxX(), 1e-9);
        assertEquals("Rising 45 degree platform should preserve player depth", playerBounds.getMinZ(), result.getMinZ(), 1e-9);
        assertEquals("Rising 45 degree platform should preserve player depth", playerBounds.getMaxZ(), result.getMaxZ(), 1e-9);
        assertEquals("Rising 45 degree platform should preserve player height",
                playerBounds.getMaxY() - playerBounds.getMinY(),
                result.getMaxY() - result.getMinY(),
                1e-9);
        assertTrue("Rising 45 degree platform should move the player upward", result.getMinY() > playerBounds.getMinY() + 0.5);
    }

    @Test
    public void testMoving45SurfaceDownThroughStationaryPlayerPushesPlayerDown() {
        OBBSurfaceTransition<String> surface = create45DegreeSurfaceTransition(new Vector(-40.0, 50.0, -60.0), 7.0, 4.0, 50.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector point = interiorPoint(surface, -surface.from.halfSize.getZ() * 0.35);
        double headY = point.getY() - 1.5;
        AABBHandle playerBounds = createAabb(new Vector(point.getX(), headY - height, point.getZ()), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(playerBounds, playerBounds)
        );

        assertHeadNotAboveSurface("Descending 45 degree platform should push the stationary player down", surface.to, result);
        assertEquals("Descending 45 degree platform should preserve player width", playerBounds.getMinX(), result.getMinX(), 1e-9);
        assertEquals("Descending 45 degree platform should preserve player width", playerBounds.getMaxX(), result.getMaxX(), 1e-9);
        assertEquals("Descending 45 degree platform should preserve player depth", playerBounds.getMinZ(), result.getMinZ(), 1e-9);
        assertEquals("Descending 45 degree platform should preserve player depth", playerBounds.getMaxZ(), result.getMaxZ(), 1e-9);
        assertEquals("Descending 45 degree platform should preserve player height",
                playerBounds.getMaxY() - playerBounds.getMinY(),
                result.getMaxY() - result.getMinY(),
                1e-9);
        assertTrue("Descending 45 degree platform should move the player downward", result.getMinY() < playerBounds.getMinY() - 0.5);
    }

    @Test
    public void testRiseInto45Surface() {
        Vector position = new Vector(-40.0, 53.2, -60.3);

        // Surface is a large flat plane centered at the position, 45 degree pitch
        Vector surfCenter = position.clone();
        Vector surfSize = new Vector(20.0, 0.0, 20.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 1.0, 1.0), new Vector(0.0, 1.0, 0.0)); // flat horizontal
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        double halfWidth = 0.01;
        double height = 1.8;

        // Starting below the surface (player entirely below plane)
        double fromFeetY = -12.0; // feet below plane
        AABBHandle from = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + fromFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + fromFeetY + height, position.getZ() + halfWidth
        );

        // Ending above the surface (simulate jumping up into it)
        double toFeetY = 12.0; // feet above plane
        AABBHandle to = AABBHandle.createNew(
                position.getX() - halfWidth, position.getY() + toFeetY, position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + toFeetY + height, position.getZ() + halfWidth
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertHeadNotAboveSurface("Player's head passed through 45 degree surface", surface.from, result);
    }

    @Test
    public void testFallOnto45SurfaceNearHighEdge() {
        Vector position = new Vector(-40.0, 53.2, -60.3);
        OBBSurfaceTransition<String> surface = create45DegreeSurface(position, 20.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector edgePoint = highestEdgePoint(surface);

        AABBHandle from = createAabb(edgePoint.clone().add(new Vector(0.0, 5.0, 0.0)), halfWidth, height);
        AABBHandle to = createAabb(edgePoint.clone().add(new Vector(0.0, -5.0, 0.0)), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(from, to));

        assertFeetNotBelowSurface("Player fell through the high edge of a 45 degree surface", surface.from, result);
    }

    @Test
    public void testRiseInto45SurfaceNearLowEdge() {
        Vector position = new Vector(-40.0, 53.2, -60.3);
        OBBSurfaceTransition<String> surface = create45DegreeSurface(position, 20.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector edgePoint = lowestEdgePoint(surface);

        AABBHandle from = createAabb(edgePoint.clone().add(new Vector(0.0, -(height + 5.0), 0.0)), halfWidth, height);
        AABBHandle to = createAabb(edgePoint.clone().add(new Vector(0.0, 5.0, 0.0)), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(from, to));

        assertHeadNotAboveSurface("Player rose through the low edge of a 45 degree surface", surface.from, result);
    }

    @Test
    public void testWalkHorizontallyInto45SurfaceWithHeadContactPushesDown() {
        Vector position = new Vector(-40.0, 50.0, -60.0);
        OBBSurfaceTransition<String> surface = create45DegreeSurface(position, 50.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector highEdge = highestEdgePoint(surface);
        Vector lowEdge = lowestEdgePoint(surface);
        double headY = 0.5 * (highEdge.getY() + lowEdge.getY());
        double feetY = headY - height;

        AABBHandle from = createAabb(new Vector(highEdge.getX(), feetY, highEdge.getZ()), halfWidth, height);
        AABBHandle to = createAabb(new Vector(lowEdge.getX(), feetY, lowEdge.getZ()), halfWidth, height);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(from, to));

        assertHeadNotAboveSurface("Player moving horizontally into the underside of a 45 degree surface should be pushed down", surface.from, result);
        assertEquals("Horizontal head collision should preserve X movement", to.getMinX(), result.getMinX(), 1e-9);
        assertEquals("Horizontal head collision should preserve X movement", to.getMaxX(), result.getMaxX(), 1e-9);
        assertEquals("Horizontal head collision should preserve Z movement", to.getMinZ(), result.getMinZ(), 1e-9);
        assertEquals("Horizontal head collision should preserve Z movement", to.getMaxZ(), result.getMaxZ(), 1e-9);
        assertTrue("Horizontal head collision should push the player downward", result.getMinY() < to.getMinY() - 1e-3);
    }

    @Test
    public void testWalkHorizontallyIntoVerticalSurfaceIsBlocked() {
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 10.0, 10.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(-2.0, 4.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(2.0, 4.0, 0.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        assertSame("Vertical wall collision should be reported as WALL", PlayerCollisionSolver.CollisionMode.WALL, result.lastCollisionMode);
        assertWallNotPassedThrough("Player moving horizontally into a vertical surface should be blocked", surface.to, from, result.bounds);
        assertEquals("Player approaching the wall from negative X should end with the leading maxX flush to the wall", surface.to.center.getX(), result.bounds.getMaxX(), 1e-9);
        assertEquals("Wall collision should preserve player height", to.getMinY(), result.bounds.getMinY(), 1e-9);
        assertEquals("Wall collision should preserve player height", to.getMaxY(), result.bounds.getMaxY(), 1e-9);
        assertEquals("Wall collision should preserve Z min", to.getMinZ(), result.bounds.getMinZ(), 1e-9);
        assertEquals("Wall collision should preserve Z max", to.getMaxZ(), result.bounds.getMaxZ(), 1e-9);
    }

    @Test
    public void testWalkHorizontallyIntoVerticalSurfaceIsBlockedFromOppositeSide() {
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 10.0, 10.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(2.0, 4.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(-2.0, 4.0, 0.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        assertTrue("Vertical wall collision should be reported as WALL (or bounds flush to wall)",
                result.lastCollisionMode == PlayerCollisionSolver.CollisionMode.WALL ||
                        Math.abs(result.bounds.getMinX() - surface.to.center.getX()) <= 1e-9);
        assertWallNotPassedThrough("Player moving horizontally into a vertical surface from positive X should be blocked", surface.to, from, result.bounds);
        assertEquals("Player approaching the wall from positive X should end with the leading minX flush to the wall", surface.to.center.getX(), result.bounds.getMinX(), 1e-9);
        assertEquals("Wall collision should preserve player height", to.getMinY(), result.bounds.getMinY(), 1e-9);
        assertEquals("Wall collision should preserve player height", to.getMaxY(), result.bounds.getMaxY(), 1e-9);
        assertEquals("Wall collision should preserve Z min", to.getMinZ(), result.bounds.getMinZ(), 1e-9);
        assertEquals("Wall collision should preserve Z max", to.getMaxZ(), result.bounds.getMaxZ(), 1e-9);
    }

    @Test
    public void testWalkAlongVerticalSurfaceWhileFlushKeepsNegativeSide() {
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 10.0, 10.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(-0.3, 4.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(-0.25, 4.0, 1.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );
        // Debug: print signed distances for corners to inspect flush behavior
        Vector[] fcs = allCorners(from);
        Vector[] tcs = allCorners(to);
        System.out.println("--- flush-negative debug ---");
        for (int i = 0; i < fcs.length; i++) {
            double sf = PlayerCollisionSolver.signedDistanceToPlane(surface.from, fcs[i]);
            double st = PlayerCollisionSolver.signedDistanceToPlane(surface.to, tcs[i]);
            System.out.println("corner=" + i + " sf=" + sf + " st=" + st);
        }

        assertSame("Flush movement along the wall should still resolve as a wall collision", PlayerCollisionSolver.CollisionMode.WALL, result.lastCollisionMode);
        assertEquals("Player flush against the wall on the negative side should keep maxX on the wall plane", surface.to.center.getX(), result.bounds.getMaxX(), 1e-9);
        assertTrue("Player flush against the wall on the negative side should not be moved through to positive X", result.bounds.getMinX() <= surface.to.center.getX() + 1e-9);
        assertEquals("Tangential movement along Z should be preserved while flush against the wall", to.getMinZ(), result.bounds.getMinZ(), 1e-9);
        assertEquals("Tangential movement along Z should be preserved while flush against the wall", to.getMaxZ(), result.bounds.getMaxZ(), 1e-9);
    }

    @Test
    public void testWalkAlongVerticalSurfaceWhileFlushKeepsPositiveSide() {
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 10.0, 10.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(0.3, 4.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(0.25, 4.0, 1.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        System.out.println(result.bounds);
        // Debug: print signed distances for corners to inspect flush behavior
        Vector[] fcs2 = allCorners(from);
        Vector[] tcs2 = allCorners(to);
        System.out.println("--- flush-positive debug ---");
        for (int i = 0; i < fcs2.length; i++) {
            double sf = PlayerCollisionSolver.signedDistanceToPlane(surface.from, fcs2[i]);
            double st = PlayerCollisionSolver.signedDistanceToPlane(surface.to, tcs2[i]);
            System.out.println("corner=" + i + " sf=" + sf + " st=" + st);
        }

        assertTrue("Flush movement along the wall should still resolve as a wall collision (or bounds flush)",
                result.lastCollisionMode == PlayerCollisionSolver.CollisionMode.WALL ||
                        Math.abs(result.bounds.getMinX() - surface.to.center.getX()) <= 1e-9);
        assertEquals("Player flush against the wall on the positive side should keep minX on the wall plane", surface.to.center.getX(), result.bounds.getMinX(), 1e-9);
        assertTrue("Player flush against the wall on the positive side should not be moved through to negative X", result.bounds.getMaxX() >= surface.to.center.getX() - 1e-9);
        assertEquals("Tangential movement along Z should be preserved while flush against the wall", to.getMinZ(), result.bounds.getMinZ(), 1e-9);
        assertEquals("Tangential movement along Z should be preserved while flush against the wall", to.getMaxZ(), result.bounds.getMaxZ(), 1e-9);
    }


    /**
     * When the wall is slightly sloped and the player is supposed to slide/fall down alongside it, and to finally end up colliding with the
     * ground surface below. The collision solver should have a final result of colliding with the ground surface, so that the surface
     * tracker can switch to movement mode. Without this, the player will get stuck in the corner in infinite fall/fly mode.
     */
    @Test
    public void testFallAlongWallOntoGround() {
        // Wall, which is more vertical than the ground
        final OBBSurfaceTransition<String> wall = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 14.802784523546912, 301.19944156763154),
                        new Quaternion(0.6254855851882744, -0.3297996099477409, 0.6254855851882744, -0.32979960994774077),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 14.802784523546912, 301.19944156763154),
                        new Quaternion(0.6254855851882744, -0.3297996099477409, 0.6254855851882744, -0.32979960994774077),
                        new Vector(8.0, 0.0, 8.0)
                )
        );

        // Ground, which is flatter, so the solver when hitting the pinch point should choose this surface
        final OBBSurfaceTransition<String> ground = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 13.761941567631563, 306.7597154764531),
                        new Quaternion(0.6754886394477906, 0.20908155819433885, 0.6754886394477906, 0.20908155819433893),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 13.761941567631563, 306.7597154764531),
                        new Quaternion(0.6754886394477906, 0.20908155819433885, 0.6754886394477906, 0.20908155819433893),
                        new Vector(8.0, 0.0, 8.0)
                )
        );

        // Slightly offset towards the wall, so player slides down along wall before reaching the ground
        final double startZ = 303.3;

        // Simulate a BBOX transition of gravity downwards (past both surfaces)
        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(-349.4, 15.9, startZ), halfWidth, height);
        AABBHandle to = createAabb(new Vector(-349.4, 8.5, startZ), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                Arrays.asList(wall, ground),
                new PlayerBoundsTransition(from, to)
        );

        // Check Y is at the pinch point
        final double pinchY = 11.78; // Roughly where the bounding box Y should be when pinched between the two surfaces (vertical of V)
        assertEquals(pinchY, result.bounds.getMinY(), 0.5);

        // Z should be exactly in the middle where the two surfaces meet
        // Because the wall is steeper, the player collides with it more, so the player will be away from that wall more
        final double pinchZ = 303.6; // Rougly where the pinch Z is (width-wise of V)
        assertEquals(pinchZ, 0.5 * (result.bounds.getMinZ() + result.bounds.getMaxZ()), 0.3);

        // We expect collision mode FEET, as the most downwards surface at the pinch point is the ground
        assertEquals(PlayerCollisionSolver.CollisionMode.FEET, result.lastCollisionMode);
    }

    @Test
    public void testMoveAwayFromVerticalSurfaceWhileFlushDoesNotCollide() {
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 10.0, 10.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle from = createAabb(new Vector(-0.3, 4.0, 0.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(-0.6, 4.0, 0.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        // Debug: print per-corner signed distances to inspect why a WALL might be reported
        Vector[] fcs3 = allCorners(from);
        Vector[] tcs3 = allCorners(to);
        System.out.println("--- move-away debug ---");
        for (int i = 0; i < fcs3.length; i++) {
            double sf = PlayerCollisionSolver.signedDistanceToPlane(surface.from, fcs3[i]);
            double st = PlayerCollisionSolver.signedDistanceToPlane(surface.to, tcs3[i]);
            System.out.println("corner=" + i + " sf=" + sf + " st=" + st);
        }

        assertEquals("Moving away from a vertical wall while flush against it should not trigger a collision", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);
        assertEquals("Moving away from a vertical wall while flush against it should not alter the destination bounds", to, result.bounds);
    }

    @Test
    public void testResolvedStandingThenJumpDoesNotPassThroughSurface() {
        // Surface is a large flat plane centered at y=5.0
        Vector surfCenter = new Vector(0.0, 5.0, 0.0);
        Vector surfSize = new Vector(10.0, 0.2, 10.0);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0)); // flat horizontal
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        OBBSurfaceTransition<String> surface = new OBBSurfaceTransition<>(surfBox, surfBox);

        double halfWidth = 0.3;
        double height = 1.8;

        // Player standing on the surface (feet exactly at plane Y)
        AABBHandle standing = createAabb(new Vector(0.0, 5.0, 0.0), halfWidth, height);
        // Resolve standing (no movement)
        AABBHandle resolved = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(standing, standing));

        // Now attempt to jump: destination has feet higher
        AABBHandle jumpTo = createAabb(new Vector(0.0, 6.0, 0.0), halfWidth, height);
        AABBHandle afterJump = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), new PlayerBoundsTransition(resolved, jumpTo));

        // The player's feet must not end up below the surface plane after the jump
        assertFeetNotBelowSurface("Resolved standing then jumping should not pass through the same surface", surface.from, afterJump);
    }

    @Test
    public void testWalkFreelyAbove45Surface() {
        Vector position = new Vector(-40.0, 50.0, -60);

        // Surface is a large flat plane centered at the position, 45 degree pitch
        OBBSurfaceTransition<String> surface = create45DegreeSurface(position, 50.0);

        double halfWidth = 0.3;
        double height = 1.8;

        // Place the player above the surface. The player should be able to walk freely without clipping through the plane.

        Vector fromPos = new Vector(0.0, 5.0, 0.0);
        AABBHandle from = AABBHandle.createNew(
                position.getX() - halfWidth + fromPos.getX(), position.getY() + fromPos.getY(), position.getZ() - halfWidth + fromPos.getZ(),
                position.getX() + halfWidth + fromPos.getX(), position.getY() + fromPos.getY() + height, position.getZ() + halfWidth + fromPos.getZ()
        );

        Vector toPos = new Vector(0.5, 5.0, 0.5);
        AABBHandle to = AABBHandle.createNew(
                position.getX() - halfWidth + toPos.getX(), position.getY() + toPos.getY(), position.getZ() - halfWidth + toPos.getZ(),
                position.getX() + halfWidth + toPos.getX(), position.getY() + toPos.getY() + height, position.getZ() + halfWidth + toPos.getZ()
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertEquals("Player should be able to walk freely above the surface without clipping through it", to, result);
    }

    @Test
    public void testWalkFreelyBelow45Surface() {
        Vector position = new Vector(-40.0, 50.0, -60);

        // Surface is a large flat plane centered at the position, 45 degree pitch
        OBBSurfaceTransition<String> surface = create45DegreeSurface(position, 50.0);

        double halfWidth = 0.3;
        double height = 1.8;

        // place the player below the surface on the raised side, but still below the plane. The player should be able to walk freely without clipping through the plane.

        Vector fromPos = new Vector(0.0, -5.0, 0.0); // feet and head below plane
        AABBHandle from = AABBHandle.createNew(
                position.getX() - halfWidth + fromPos.getX(), position.getY() + fromPos.getY(), position.getZ() - halfWidth + fromPos.getZ(),
                position.getX() + halfWidth + fromPos.getX(), position.getY() + fromPos.getY() + height, position.getZ() + halfWidth + fromPos.getZ()
        );

        Vector toPos = new Vector(0.5, -5.0, 0.5);
        AABBHandle to = AABBHandle.createNew(
                position.getX() - halfWidth + toPos.getX(), position.getY() + toPos.getY(), position.getZ() - halfWidth + toPos.getZ(),
                position.getX() + halfWidth + toPos.getX(), position.getY() + toPos.getY() + height, position.getZ() + halfWidth + toPos.getZ()
        );

        PlayerBoundsTransition player = new PlayerBoundsTransition(from, to);

        AABBHandle result = TEST_SOLVER.solve(java.util.Collections.singletonList(surface), player);

        assertEquals("Player should be able to walk freely below the surface without clipping through it", to, result);
    }

    @Test
    public void testSingleSmallWallOutsideExtentDoesNotBlockHorizontalCrossing() {
        // Create a small vertical wall centered at X=0, Z=0
        OBBSurfaceTransition<String> surface = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 2.0, 2.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        // Move horizontally across the wall plane (X crosses from -2 -> +2) but at Z=2.0,
        // which is outside the wall's Z half-extent (wall halfSize.z = 1.0). Solver should
        // NOT treat this as a wall collision because the crossing point is outside the surface extents.
        AABBHandle from = createAabb(new Vector(-2.0, 4.0, 2.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(2.0, 4.0, 2.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        // Debug output: print per-corner signed distances and crossing tests to help diagnose false positives
        Vector[] fromCorners = allCorners(from);
        Vector[] toCorners = allCorners(to);
        System.out.println("--- Debug single small wall test ---");
        for (int i = 0; i < fromCorners.length; i++) {
            double sf = PlayerCollisionSolver.signedDistanceToPlane(surface.from, fromCorners[i]);
            double stt = PlayerCollisionSolver.signedDistanceToPlane(surface.to, toCorners[i]);
            Double t = PlayerCollisionSolver.crossingTheta(sf, stt);
            boolean crosses = false;
            if (t != null) crosses = PlayerCollisionSolver.crossesWithinSurface(surface, fromCorners[i], toCorners[i], t);
            System.out.println("corner=" + i + " from=" + fromCorners[i] + " to=" + toCorners[i] + " signedFrom=" + sf + " signedTo=" + stt + " theta=" + t + " crossesWithin=" + crosses);
        }

        System.out.println("Result mode=" + result.lastCollisionMode + " bounds=" + result.bounds);

        assertEquals("Single small wall outside its extents should not block horizontal crossing", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);
        assertEquals("Destination bounds should be preserved when crossing outside the wall extents", to, result.bounds);
    }

    @Test
    public void testSmallVerticalWallOutsideExtentDoesNotDragPlayerWithMultiSurfacePath() {
        OBBSurfaceTransition<String> wall = createVerticalSurface(new Vector(0.0, 5.0, 0.0), 2.0, 2.0, true);
        OBBSurfaceTransition<String> dummy = createFlatSurfaceTransition(new Vector(100.0, 0.0, 100.0), 0.0, 0.0, 4.0);

        double halfWidth = 0.3;
        double height = 1.8;
        // Move across where the infinite wall plane would be, but at Z=2.0 which lies
        // outside the wall's Z half-extent. This should not drag the player along or
        // block the movement.
        AABBHandle from = createAabb(new Vector(-2.0, 4.0, 2.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(2.0, 4.0, 2.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                Arrays.asList(wall, dummy),
                new PlayerBoundsTransition(from, to)
        );

        assertEquals("A small vertical wall should not drag the player when crossing outside its extents, even in the multi-surface path", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);
        assertEquals("Destination bounds should be preserved when crossing outside the wall extents", to, result.bounds);
    }

    @Test
    public void testSmallSlopeOutsideExtentDoesNotBlockVerticalCrossingWithMultiSurfacePath() {
        OBBSurfaceTransition<String> slope = create45DegreeSurface(new Vector(0.0, 0.0, 0.0), 4.0);
        OBBSurfaceTransition<String> dummy = createVerticalSurface(new Vector(100.0, 5.0, 100.0), 2.0, 2.0, true);

        double halfWidth = 0.3;
        double height = 1.8;
        // Move vertically through where the infinite slope plane would be, but far outside
        // the slope's actual X/Z footprint. With a correct footprint check, no collision
        // should be reported and the destination should be preserved.
        AABBHandle from = createAabb(new Vector(0.0, 20.0, 10.0), halfWidth, height);
        AABBHandle to = createAabb(new Vector(0.0, -20.0, 10.0), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                Arrays.asList(slope, dummy),
                new PlayerBoundsTransition(from, to)
        );

        assertEquals("A small slope should not block vertical movement outside its extents even when multiple surfaces are present", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);
        assertEquals("Destination bounds should be preserved when moving outside the slope extents", to, result.bounds);
    }

    @Test
    public void testMoveUpwardAwayFromSteepSurfaceDoesNotCollide() {
        Vector position = new Vector(-20.0, 30.0, 15.0);
        OBBSurfaceTransition<String> surface = createSteepSurface(position, 30.0);

        double halfWidth = 0.3;
        double height = 1.8;
        Vector point = interiorPoint(surface, 0.0);
        AABBHandle from = createAabb(new Vector(point.getX(), point.getY(), point.getZ()), halfWidth, height);
        AABBHandle to = createAabb(new Vector(point.getX(), point.getY() + 1.0, point.getZ()), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                java.util.Collections.singletonList(surface),
                new PlayerBoundsTransition(from, to)
        );

        assertEquals("Moving upward away from a steep sloped surface should not collide with that same surface", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);
        assertEquals("Moving upward away from a steep sloped surface should preserve the intended destination bounds", to, result.bounds);
    }

    @Test
    public void testMoveWithinWalls() {
        // In this test case, there are a couple walls surrounding the player that move with the player
        // It should not detect any collisions with these walls.

        PlayerBoundsTransition playerTransition = new PlayerBoundsTransition(
                AABBHandle.createNew(
                        -333.74510530044364, 7.4625, 283.3845511231143,
                        -333.1451052766018, 9.262499952316285, 283.98455114695616
                ),
                AABBHandle.createNew(
                        -333.4985421799684, 7.4625, 283.19626069254747,
                        -332.89854215612655, 9.262499952316285, 283.7962607163893
                )
        );

        List<OBBSurfaceTransition<String>> transitions = Arrays.asList(
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-334.13591715665456, 8.3625, 281.4129690808356),
                                new Quaternion(0.598773666563434, 0.598773666563434, 0.3761251071521039, 0.3761251071521039),
                                new Vector(1.8, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-334.0863417758234, 8.3625, 281.29418942438986),
                                new Quaternion(0.6147398611280926, 0.6147398611280926, 0.34942081097183264, 0.34942081097183264),
                                new Vector(1.8, 0.0, 8.0)
                        )
                ),
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-336.90148725148805, 8.3625, 284.888098150462),
                                new Quaternion(0.15743630618116733, 0.15743630618116733, 0.6893575338647062, 0.6893575338647063),
                                new Vector(1.8, 0.0, 4.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-336.5357608410593, 8.3625, 284.9989493684158),
                                new Quaternion(0.18760889954346513, 0.18760889954346513, 0.6817645493952367, 0.6817645493952367),
                                new Vector(1.8, 0.0, 4.0)
                        )
                )
        );

        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(
                transitions,
                playerTransition
        );

        System.out.println(result);

        assertFalse(result.hasCollision());
    }

    private static OBBSurfaceTransition<String> create45DegreeSurface(Vector position, double size) {
        Vector surfCenter = position.clone();
        Vector surfSize = new Vector(size, 0.0, size);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 1.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        return new OBBSurfaceTransition<>(surfBox, surfBox);
    }

    private static OBBSurfaceTransition<String> createSteepSurface(Vector position, double size) {
        Vector surfCenter = position.clone();
        Vector surfSize = new Vector(size, 0.0, size);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 1.0, 0.08), new Vector(0.0, 1.0, 0.0));
        OrientedBoundingBox surfBox = new OrientedBoundingBox(surfCenter, surfSize, surfOri);
        return new OBBSurfaceTransition<>(surfBox, surfBox);
    }


    private static OBBSurfaceTransition<String> create45DegreeSurfaceTransition(Vector position, double fromY, double toY, double size) {
        Vector surfSize = new Vector(size, 0.0, size);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 1.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OrientedBoundingBox fromBox = new OrientedBoundingBox(position.clone().setY(fromY), surfSize, surfOri);
        OrientedBoundingBox toBox = new OrientedBoundingBox(position.clone().setY(toY), surfSize, surfOri);
        return new OBBSurfaceTransition<>(fromBox, toBox);
    }

    private static OBBSurfaceTransition<String> createFlatSurfaceTransition(Vector position, double fromY, double toY, double size) {
        Vector surfSize = new Vector(size, 0.2, size);
        Quaternion surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OrientedBoundingBox fromBox = new OrientedBoundingBox(position.clone().add(new Vector(0.0, fromY, 0.0)), surfSize, surfOri);
        OrientedBoundingBox toBox = new OrientedBoundingBox(position.clone().add(new Vector(0.0, toY, 0.0)), surfSize, surfOri);
        return new OBBSurfaceTransition<>(fromBox, toBox);
    }

    private static OBBSurfaceTransition<String> createFlatSurfaceTransition(double sizeX, double sizeZ) {
        Quaternion orientation = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0));
        OrientedBoundingBox box = new OrientedBoundingBox(
                new Vector(0.0, 0.0, 0.0),
                new Vector(sizeX, 0.2, sizeZ),
                orientation
        );
        return new OBBSurfaceTransition<>(box, box, "surface");
    }

    // Create a vertical wall. If normalAlongX is true, the wall plane is perpendicular to the X axis
    // (i.e. plane X = center.x). If false, the wall plane is perpendicular to the Z axis (plane Z = center.z).
    private static OBBSurfaceTransition<String> createVerticalSurface(Vector center, double height, double width, boolean normalAlongX) {
        Vector surfSize;
        Quaternion surfOri;
        if (normalAlongX) {
            // Keep previous size ordering for X-normal walls (works with existing tests expecting center.x as plane)
            surfSize = new Vector(height, 0.2, width);
            // lookDirection Z, up X -> up vector becomes the plane normal (X)
            surfOri = Quaternion.fromLookDirection(new Vector(0.0, 0.0, 1.0), new Vector(1.0, 0.0, 0.0));
        } else {
            // Swap dimensions so the wall extends along X while its normal points along Z
            surfSize = new Vector(width, 0.2, height);
            // lookDirection X, up Z -> up vector becomes the plane normal (Z)
            surfOri = Quaternion.fromLookDirection(new Vector(1.0, 0.0, 0.0), new Vector(0.0, 0.0, 1.0));
        }
        OrientedBoundingBox surfBox = new OrientedBoundingBox(center, surfSize, surfOri);
        return new OBBSurfaceTransition<>(surfBox, surfBox);
    }

    private static AABBHandle createAabb(Vector feetPosition, double halfWidth, double height) {
        return AABBHandle.createNew(
                feetPosition.getX() - halfWidth, feetPosition.getY(), feetPosition.getZ() - halfWidth,
                feetPosition.getX() + halfWidth, feetPosition.getY() + height, feetPosition.getZ() + halfWidth
        );
    }

    private static AABBHandle translateAabb(AABBHandle aabb, double dx, double dy, double dz) {
        return AABBHandle.createNew(
                aabb.getMinX() + dx, aabb.getMinY() + dy, aabb.getMinZ() + dz,
                aabb.getMaxX() + dx, aabb.getMaxY() + dy, aabb.getMaxZ() + dz
        );
    }

    private static Vector highestEdgePoint(OBBSurfaceTransition<?> surface) {
        Vector positiveEdge = edgePoint(surface, surface.from.halfSize.getZ() - EDGE_MARGIN);
        Vector negativeEdge = edgePoint(surface, -surface.from.halfSize.getZ() + EDGE_MARGIN);
        return (positiveEdge.getY() >= negativeEdge.getY()) ? positiveEdge : negativeEdge;
    }

    private static Vector lowestEdgePoint(OBBSurfaceTransition<?> surface) {
        Vector positiveEdge = edgePoint(surface, surface.from.halfSize.getZ() - EDGE_MARGIN);
        Vector negativeEdge = edgePoint(surface, -surface.from.halfSize.getZ() + EDGE_MARGIN);
        return (positiveEdge.getY() <= negativeEdge.getY()) ? positiveEdge : negativeEdge;
    }

    private static Vector edgePoint(OBBSurfaceTransition<?> surface, double localZ) {
        return surface.from.localToWorld(new Vector(0.0, 0.0, localZ), new Vector());
    }

    private static Vector interiorPoint(OBBSurfaceTransition<?> surface, double localZ) {
        return surface.from.localToWorld(new Vector(0.0, 0.0, localZ), new Vector());
    }

    private static void assertFeetNotBelowSurface(String message, OBBSurfaceState surface, AABBHandle result) {
        double expectedMinY = Math.max(
                planeYAt(surface, result.getMinX(), result.getMinZ()),
                Math.max(
                        planeYAt(surface, result.getMinX(), result.getMaxZ()),
                        Math.max(
                                planeYAt(surface, result.getMaxX(), result.getMinZ()),
                                planeYAt(surface, result.getMaxX(), result.getMaxZ())
                        )
                )
        );
        assertTrue(message + ": minY=" + result.getMinY() + " planeY=" + expectedMinY,
                result.getMinY() + 1e-6 >= expectedMinY - 1e-3);
    }

    private static void assertHeadNotAboveSurface(String message, OBBSurfaceState surface, AABBHandle result) {
        double expectedMaxY = Math.min(
                planeYAt(surface, result.getMinX(), result.getMinZ()),
                Math.min(
                        planeYAt(surface, result.getMinX(), result.getMaxZ()),
                        Math.min(
                                planeYAt(surface, result.getMaxX(), result.getMinZ()),
                                planeYAt(surface, result.getMaxX(), result.getMaxZ())
                        )
                )
        );
        assertTrue(message + ": maxY=" + result.getMaxY() + " planeY=" + expectedMaxY,
                result.getMaxY() <= expectedMaxY + 1e-3);
    }

    private static void assertWallNotPassedThrough(String message, OBBSurfaceState surface, AABBHandle from, AABBHandle result) {
        double fromSigned = surface.signedDistanceToPlane(aabbCenter(from));
        if (fromSigned >= 0.0) {
            assertTrue(message + ": minSigned=" + minSignedDistanceToPlane(surface, result),
                    minSignedDistanceToPlane(surface, result) >= -1e-3);
        } else {
            assertTrue(message + ": maxSigned=" + maxSignedDistanceToPlane(surface, result),
                    maxSignedDistanceToPlane(surface, result) <= 1e-3);
        }
    }

    private static boolean passesThroughSurface(OBBSurfaceState surface, AABBHandle from, AABBHandle to) {
        return PlayerCollisionSolver.passesThroughSurface(new OBBSurfaceTransition<>(surface, surface), from, to);
    }

    private static Vector aabbCenter(AABBHandle aabb) {
        return new Vector(
                0.5 * (aabb.getMinX() + aabb.getMaxX()),
                0.5 * (aabb.getMinY() + aabb.getMaxY()),
                0.5 * (aabb.getMinZ() + aabb.getMaxZ())
        );
    }

    private static double minSignedDistanceToPlane(OBBSurfaceState surface, AABBHandle aabb) {
        double min = Double.POSITIVE_INFINITY;
        for (Vector corner : allCorners(aabb)) {
            min = Math.min(min, surface.signedDistanceToPlane(corner));
        }
        return min;
    }

    private static double maxSignedDistanceToPlane(OBBSurfaceState surface, AABBHandle aabb) {
        double max = Double.NEGATIVE_INFINITY;
        for (Vector corner : allCorners(aabb)) {
            max = Math.max(max, surface.signedDistanceToPlane(corner));
        }
        return max;
    }

    private static Vector[] allCorners(AABBHandle aabb) {
        return new Vector[] {
                new Vector(aabb.getMinX(), aabb.getMinY(), aabb.getMinZ()),
                new Vector(aabb.getMinX(), aabb.getMinY(), aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), aabb.getMinY(), aabb.getMinZ()),
                new Vector(aabb.getMaxX(), aabb.getMinY(), aabb.getMaxZ()),
                new Vector(aabb.getMinX(), aabb.getMaxY(), aabb.getMinZ()),
                new Vector(aabb.getMinX(), aabb.getMaxY(), aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), aabb.getMaxY(), aabb.getMinZ()),
                new Vector(aabb.getMaxX(), aabb.getMaxY(), aabb.getMaxZ())
        };
    }

    private static double planeYAt(OBBSurfaceState surface, double x, double z) {
        return surface.center.getY() - (((x - surface.center.getX()) * surface.normal.getX())
                + ((z - surface.center.getZ()) * surface.normal.getZ())) / surface.normal.getY();
    }

    @Test
    public void testThreeSurfaceCornerBlocking() {
        // Large ground plane at y=0 (not moving)
        OBBSurfaceTransition<String> ground = createFlatSurfaceTransition(new Vector(0.0, 0.0, 0.0), 0.0, 0.0, 10.0);

        // Two vertical walls meeting at the corner (x=1 and z=1), static
        OBBSurfaceTransition<String> wallX = createVerticalSurface(new Vector(1.0, 5.0, 0.0), 10.0, 10.0, true);
        OBBSurfaceTransition<String> wallZ = createVerticalSurface(new Vector(0.0, 5.0, 1.0), 10.0, 10.0, false);

        double halfWidth = 0.3;
        double height = 1.8;
        // Start slightly away from the corner on the ground
        AABBHandle prev = createAabb(new Vector(0.6, 0.0, 0.6), halfWidth, height);
        // Player attempts to move diagonally into the corner target each tick
        AABBHandle desiredTo = createAabb(new Vector(1.1, 0.0, 1.1), halfWidth, height);

        // Only ground and X-aligned wall
        {
            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(ground, wallX));
            assertTrue("With only wallX present the player should be able to move past where wallZ would be (penetrate missing wallZ). If this fails, the solver is incorrectly blocking when wallZ is absent.",
                    result.bounds.getMaxZ() >= desiredTo.getMaxZ() - 1e-6);
        }

        // Only ground and Z-aligned wall
        {
            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(ground, wallZ));
            assertTrue("With only wallZ present the player should be able to move past where wallX would be (penetrate missing wallX). If this fails, the solver is incorrectly blocking when wallX is absent.",
                    result.bounds.getMaxX() >= desiredTo.getMaxX() - 1e-6 || result.bounds.getMinX() <= desiredTo.getMinX() + 1e-6);
        }

        // Ground and both X/Z aligned walls. Should block the player from moving near desiredTo
        {
            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(ground, wallX, wallZ));
            assertEquals("Should be positioned against wall X", 0.4, result.bounds.getMinX(), 1e-4);
            assertEquals("Should be positioned against wall Z", 0.4, result.bounds.getMinZ(), 1e-4);
        }
    }

    @Test
    public void testWalkAgainstInvertedSlope() {
        // Walking on the (green) surface, into an inverted slope (red) (more-than-90-degrees)
        // Verifies that the player is stopped against the slope and does not clip through it
        // We also expect that the solver returns (prioritized) the ground over the vertical surface, so the player
        // can walk away from the wall again
        OBBSurfaceTransition<String> slopeWall = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 18.430580573302674, 308.2587704831436),
                        new Quaternion(0.0, -0.5735764363510463, 0.8191520442889918, 0.0),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 18.430580573302674, 308.2587704831436),
                        new Quaternion(0.0, -0.5735764363510463, 0.8191520442889918, 0.0),
                        new Vector(8.0, 0.0, 8.0)
                ),
                "red_slope"
        );

        OBBSurfaceTransition<String> slopeGround = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 13.303729516856366, 305.8680805733027),
                        new Quaternion(0.0, 0.9848077530122082, -0.17364817766693036, 0.0),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 13.303729516856366, 305.8680805733027),
                        new Quaternion(0.0, 0.9848077530122082, -0.17364817766693036, 0.0),
                        new Vector(8.0, 0.0, 8.0)
                ),
                "green_ground"
        );

        double halfWidth = 0.3;
        double height = 1.8;
        AABBHandle prev = createAabb(new Vector(-349.5, 13.73, 307.011), halfWidth, height);
        AABBHandle desiredTo = createAabb(new Vector(-349.5, 13.45, 312.04), halfWidth, height);

        PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(slopeGround, slopeWall));
        assertEquals(PlayerCollisionSolver.CollisionMode.FEET, result.lastCollisionMode);
        assertEquals(slopeGround.source, result.lastSurface);
    }

    @Test
    public void testThreeRotatedSurfacesBlocking() {
        // This is an ingame captured set of three surfaces that together form a corner of a cube
        // When the player is on the inside, moving into it should block the player from passing through

        final double halfWidth = 0.3;
        final double height = 1.8;

        OBBSurfaceTransition<String> yellow = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 14.23407287525381, 307.3284271247462),
                        new Quaternion(0.9238795325112867, 0.0, 0.0, 0.38268343236508984),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 14.23407287525381, 307.3284271247462),
                        new Quaternion(0.9238795325112867, 0.0, 0.0, 0.38268343236508984),
                        new Vector(8.0, 0.0, 8.0)
                )
        );

        OBBSurfaceTransition<String> green = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.5, 14.23407287525381, 301.6715728752538),
                        new Quaternion(0.3826834323650898, 0.0, 0.0, 0.9238795325112867),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.5, 14.23407287525381, 301.6715728752538),
                        new Quaternion(0.3826834323650898, 0.0, 0.0, 0.9238795325112867),
                        new Vector(8.0, 0.0, 8.0)
                )
        );


        // The yellow and green surfaces are both 45 degrees pitched, creating a funnel.
        // It should not be possible to pass through downwards vertically

        //  Above green surface
        {
            AABBHandle prev = createAabb(new Vector(-350.0, 15.5, 301.1), halfWidth, height);
            AABBHandle desiredTo = createAabb(new Vector(-350.0, 8.0, 301.1), halfWidth, height);

            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(green, yellow));
            assertTrue("Player did not pass through the two surfaces",
                    result.bounds.getMinY() >= 10.0);
        }

        // Above pinch point between green and yellow surface
        {
            AABBHandle prev = createAabb(new Vector(-349.5, 15.5, 304.5), halfWidth, height);
            AABBHandle desiredTo = createAabb(new Vector(-349.5,  8.0, 304.5), halfWidth, height);

            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(yellow, green));
            assertSame("Passing between the two pitched surfaces should resolve as feet support", PlayerCollisionSolver.CollisionMode.FEET, result.lastCollisionMode);
            assertTrue("Player did not pass through the two surfaces",
                    result.bounds.getMinY() >= 10.0);
            assertEquals("Should be positioned in the middle of the pinch point Z",
                    304.5, 0.5 * (result.bounds.getMinZ() + result.bounds.getMaxZ()), 1e-4);
        }

        OBBSurfaceTransition<String> black = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-353.5, 17.0625, 304.5),
                        new Quaternion(0.6532814824381883, -0.6532814824381883, 0.27059805007309845, 0.27059805007309856),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-353.5, 17.0625, 304.5),
                        new Quaternion(0.6532814824381883, -0.6532814824381883, 0.27059805007309845, 0.27059805007309856),
                        new Vector(8.0, 0.0, 8.0)
                )
        );

        // Verify we cannot squeeze through the corner, against all three surfaces, either.
        {
            AABBHandle prev = createAabb(new Vector(-349.5, 17.5, 305.5), halfWidth, height);
            AABBHandle desiredTo = createAabb(new Vector(-357.5, 8.0, 304.5), halfWidth, height);

            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(yellow, black, green));

            // Expected end position min/x/y/z middle: [ -353.8, 11.4, 304.6 ]
        }

        // Also test a different angle going down the slope of yellow, into green
        {
            AABBHandle prev = createAabb(new Vector(-353.2, 12.0, 304.0), halfWidth, height);
            AABBHandle desiredTo = createAabb(new Vector(-354.5, 9.1, 307.2), halfWidth, height);

            PlayerCollisionSolver.Result<String> result = moveUntilBlocked(prev, desiredTo, Arrays.asList(yellow, black, green));

            // Expected end position min/x/y/z middle: [ -353.8, 11.4, 304.6 ]
        }
    }

    private  PlayerCollisionSolver.Result<String> moveUntilBlocked(AABBHandle start, AABBHandle target, List<OBBSurfaceTransition<String>> walls) {
        AABBHandle curr = start;
        int tick = 0;
        while (true) {
            PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(walls, new PlayerBoundsTransition(curr, target));

            System.out.println("bothWalls tick=" + tick + " collisionMode=" + result.lastCollisionMode +
                    " minX=" + result.bounds.getMinX() + " minY=" + result.bounds.getMinY() + " maxX=" + result.bounds.getMaxX() +
                    " minZ=" + result.bounds.getMinZ() + " maxY=" + result.bounds.getMaxY() + " maxZ=" + result.bounds.getMaxZ());

            // The solver should not allow the player to penetrate either wall at any tick
            int idx = 0;
            for (OBBSurfaceTransition<String> wall : walls) {
                assertWallNotPassedThrough("Tick " + tick + " penetrated wall[" + (idx++) + "] " + wall.source, wall.to, curr, result.bounds);
            }

            // Ensure some collision is reported when attempting to move into the corner
            assertNotSame("Tick " + tick + " should report collision mode != NONE", PlayerCollisionSolver.CollisionMode.NONE, result.lastCollisionMode);

            // Use the resolved bounds for the next tick (player is blocked and tries again)
            curr = result.bounds;

            if (++tick == 20) {
                return result;
            }
        }
    }

    private static AABBHandle createPlayerBounds(Vector feetPosition) {
        double halfWidth = 0.3;
        double height = 1.8;
        return AABBHandle.createNew(
                feetPosition.getX() - halfWidth, feetPosition.getY(), feetPosition.getZ() - halfWidth,
                feetPosition.getX() + halfWidth, feetPosition.getY() + height, feetPosition.getZ() + halfWidth
        );
    }

    private static OBBSurfaceTransition<String> createSlopeSurfaceTransition(Vector forward, Vector up, double sizeX, double sizeZ) {
        Quaternion orientation = Quaternion.fromLookDirection(forward, up);
        OrientedBoundingBox box = new OrientedBoundingBox(
                new Vector(0.0, 0.0, 0.0),
                new Vector(sizeX, 0.2, sizeZ),
                orientation
        );
        return new OBBSurfaceTransition<>(box, box, "surface");
    }

    @Test
    public void testHasSurfaceSupportUsesPlayerBoundingBoxNearEdge() {
        OBBSurfaceTransition<String> surface = createFlatSurfaceTransition(2.0, 2.0);
        Vector playerPosition = new Vector(1.2, 0.0, 0.0);
        AABBHandle playerBounds = createPlayerBounds(playerPosition);

        assertTrue(surface.hasSurfaceSupport(playerBounds, playerPosition, 0.1, TEST_SOLVER));
    }

    @Test
    public void testHasSurfaceSupportDetectsWhenPlayerFullyLeavesSurface() {
        OBBSurfaceTransition<String> surface = createFlatSurfaceTransition(2.0, 2.0);
        Vector playerPosition = new Vector(1.31, 0.0, 0.0);
        AABBHandle playerBounds = createPlayerBounds(playerPosition);

        assertFalse(surface.hasSurfaceSupport(playerBounds, playerPosition, 0.1, TEST_SOLVER));
    }

    @Test
    public void testHasSurfaceSupportOnSlopeWhenBottomFaceIntersectsPlane() {
        OBBSurfaceTransition<String> surface = createSlopeSurfaceTransition(
                new Vector(0.0, 0.0, 1.0),
                new Vector(0.5015107371594574, 0.8651514205697044, 0.0),
                20.0,
                20.0
        );
        Vector playerPosition = surface.to.localToWorld(new Vector(-0.1, 0.0, 0.0), new Vector());
        AABBHandle playerBounds = createPlayerBounds(playerPosition);

        assertTrue(surface.to.bottomFaceIntersectsSurfacePlane(playerBounds));
        assertTrue(surface.hasSurfaceSupport(playerBounds, playerPosition, 0.1, TEST_SOLVER));
    }
}
