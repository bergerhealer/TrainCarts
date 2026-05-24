package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

/**
 * Continuous collision solver between a moving player AABB and one or more flat OBB surfaces.
 *
 * <p>Current contract (as used by unit tests):
 * <ul>
 *   <li>If the player moves from above a surface to below it, clamp so feet don't end below the surface.</li>
 *   <li>If the player moves from below a surface to above it, clamp so head doesn't end above the surface.</li>
 *   <li>If the player stays entirely on one side (or does not cross within the surface extents), leave movement unchanged.</li>
 * </ul>
 *
 * <p>This uses a continuous plane-crossing test for the bottom and top corners of the player AABB.
 * Using the full footprint avoids missing collisions near the edges of a surface and correctly handles
 * sloped planes where the contact Y varies across the surface.
 *
 * <p>Surface motion is taken into account using the surface from/to states. This allows rising
 * platforms to pick up players and descending platforms to push players down when intersecting
 * their head.
 */
public class PlayerCollisionSolver {
    private static final double EPS = 1e-9;
    /** Small downward sweep used when checking what surface the player is standing on */
    private static final double SUPPORT_SEARCH_DISTANCE = 0.15;
    /** Threshold used to treat a surface as near-vertical for stability in various calculations */
    private static final double VERTICAL_NORMAL_EPS = 1e-2;
    /** If algebraic plane Y and projection-based plane Y differ by more than this, prefer projection. */
    private static final double ALGEBRAIC_PROJECTION_DIFF_EPS = 15.0;
    /** If the worst-case algebraic Y deviation across the surface exceeds this, reject algebraic. */
    private static final double ALGEBRAIC_WORST_DELTA_LIMIT = 20.0;
    /** Distance that counts as definitely penetrating the plane */
    private static final double CROSSING_MARGIN = 1e-3;
    /** Tiny comparison tolerance for floating-point rounding around the crossing margin */
    private static final double COMPARE_EPS = 1e-6;
    /** Extra distance to separate the player from the plane after resolution */
    private static final double RESOLVE_OFFSET = 0.0;
    /** Maximum allowed Y delta when multiple surfaces are involved before trying a safer single-surface result */
    private static final double MULTI_SURFACE_SAFE_DELTA = 20.0;
    /** Safety limit for the solver's stabilization loop */
    private static final int LOOP_GUARD_LIMIT = 10000;

    // Instance logger: when using an instance of PlayerCollisionSolver, this logger
    // is used by instance methods to emit structured debug information.
    private final PlayerCollisionLogger logger;

    public PlayerCollisionSolver() {
        this(PlayerCollisionLogger.DISABLED);
    }

    public PlayerCollisionSolver(PlayerCollisionLogger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("logger cannot be null");
        }
        this.logger = logger;
    }

    public enum CollisionMode {
        NONE,
        FEET,
        HEAD,
        WALL
    }

    // Interaction candidate stores the meaning of a potential surface interaction
    private static final class InteractionCandidate<T> {
        final OBBSurfaceTransition<T> st;
        final OBBSurfaceState surfaceAtTheta;
        final PlayerState playerAtTheta;
        final double theta; // first encounter parameter along motion [0..1]
        final boolean isVertical;
        final boolean feetCrossed;
        final boolean headCrossed;

        InteractionCandidate(OBBSurfaceTransition<T> st, OBBSurfaceState surfaceAtTheta, PlayerState playerAtTheta, double theta, boolean isVertical,
                             boolean feetCrossed, boolean headCrossed) {
            this.st = st;
            this.surfaceAtTheta = surfaceAtTheta;
            this.playerAtTheta = playerAtTheta;
            this.theta = theta;
            this.isVertical = isVertical;
            this.feetCrossed = feetCrossed;
            this.headCrossed = headCrossed;
        }
    }

    private static boolean areAABBsEqual(AABBHandle a, AABBHandle b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        final double TOL = 1e-6;
        return Math.abs(a.getMinX() - b.getMinX()) <= TOL
                && Math.abs(a.getMinY() - b.getMinY()) <= TOL
                && Math.abs(a.getMinZ() - b.getMinZ()) <= TOL
                && Math.abs(a.getMaxX() - b.getMaxX()) <= TOL
                && Math.abs(a.getMaxY() - b.getMaxY()) <= TOL
                && Math.abs(a.getMaxZ() - b.getMaxZ()) <= TOL;
    }

    private static AABBHandle interpolateAABB(AABBHandle from, AABBHandle to, double t) {
        double minX = from.getMinX() + (to.getMinX() - from.getMinX()) * t;
        double minY = from.getMinY() + (to.getMinY() - from.getMinY()) * t;
        double minZ = from.getMinZ() + (to.getMinZ() - from.getMinZ()) * t;
        double maxX = from.getMaxX() + (to.getMaxX() - from.getMaxX()) * t;
        double maxY = from.getMaxY() + (to.getMaxY() - from.getMaxY()) * t;
        double maxZ = from.getMaxZ() + (to.getMaxZ() - from.getMaxZ()) * t;
        return AABBHandle.createNew(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private <T> Optional<InteractionCandidate<T>> computeInteractionCandidate(OBBSurfaceTransition<T> st,
                                                                              PlayerTransition player,
                                                                              double minTheta) {
        double startTheta = Math.max(0.0, Math.min(1.0, minTheta));
        if (startTheta >= 1.0) {
            return Optional.empty();
        }

        final int ROUGH_STEPS = 12;
        double prevTheta = startTheta;
        OBBSurfaceState prevSurface = st.interpolate(prevTheta);
        PlayerState prevPlayer = player.interpolate(prevTheta);

        for (int i = 1; i <= ROUGH_STEPS; i++) {
            double theta = startTheta + (1.0 - startTheta) * (i / (double) ROUGH_STEPS);
            OBBSurfaceState currSurface = st.interpolate(theta);
            PlayerState currPlayer = player.interpolate(theta);

            Optional<InteractionCandidate<T>> cand = evaluateInteractionCandidateInterval(
                    st, prevSurface, currSurface, prevPlayer, currPlayer, prevTheta, theta);
            if (cand.isPresent()) {
                InteractionCandidate<T> best = cand.get();
                this.logger.debugCandidate(st, best.theta, best.isVertical, best.feetCrossed, best.headCrossed,
                        player.from.allCorners(), best.playerAtTheta.allCorners());
                return Optional.of(best);
            }

            prevTheta = theta;
            prevSurface = currSurface;
            prevPlayer = currPlayer;
        }

        return Optional.empty();
    }

    private static <T> Optional<InteractionCandidate<T>> evaluateInteractionCandidateInterval(OBBSurfaceTransition<T> st,
                                                                                             OBBSurfaceState fromSurface, OBBSurfaceState toSurface,
                                                                                             PlayerState fromPlayer, PlayerState toPlayer,
                                                                                             double fromTheta, double toTheta) {
        boolean isVertical = Math.abs(fromSurface.normal.getY()) < VERTICAL_NORMAL_EPS && Math.abs(toSurface.normal.getY()) < VERTICAL_NORMAL_EPS;
        double bestTheta = Double.POSITIVE_INFINITY;
        boolean feetCrossed = false;
        boolean headCrossed = false;
        boolean found = false;

        Vector[] fromBottom = fromPlayer.bottomFaceCorners();
        Vector[] toBottom = toPlayer.bottomFaceCorners();
        Vector[] fromTop = fromPlayer.topFaceCorners();
        Vector[] toTop = toPlayer.topFaceCorners();
        Vector[] fromCorners = fromPlayer.allCorners();
        Vector[] toCorners = toPlayer.allCorners();

        if (isVertical) {
            for (int i = 0; i < fromCorners.length; i++) {
                double signedFrom = signedDistanceToPlane(fromSurface, fromCorners[i]);
                double signedTo = signedDistanceToPlane(toSurface, toCorners[i]);
                Double t = crossingTheta(signedFrom, signedTo);
                if (t != null && crossesWithinEitherSurface(fromSurface, toSurface, fromCorners[i], toCorners[i], t)) {
                    double theta = fromTheta + (toTheta - fromTheta) * t;
                    bestTheta = Math.min(bestTheta, theta);
                    found = true;
                }
            }
        } else {
            FaceCross bottom = checkFaceCrossing(fromSurface, toSurface, fromBottom, toBottom, 0.0, true);
            FaceCross top = checkFaceCrossing(fromSurface, toSurface, fromTop, toTop, 0.0, false);
            if (bottom.crossed || top.crossed) {
                found = true;
                if (bottom.crossed && top.crossed) {
                    double bottomTheta = fromTheta + (toTheta - fromTheta) * bottom.bestTheta;
                    double topTheta = fromTheta + (toTheta - fromTheta) * top.bestTheta;
                    if (Math.abs(bottomTheta - topTheta) <= COMPARE_EPS) {
                        feetCrossed = true;
                        headCrossed = true;
                        bestTheta = Math.min(bottomTheta, topTheta);
                    } else if (bottomTheta < topTheta) {
                        feetCrossed = true;
                        bestTheta = bottomTheta;
                    } else {
                        headCrossed = true;
                        bestTheta = topTheta;
                    }
                } else if (bottom.crossed) {
                    feetCrossed = true;
                    bestTheta = fromTheta + (toTheta - fromTheta) * bottom.bestTheta;
                } else {
                    headCrossed = true;
                    bestTheta = fromTheta + (toTheta - fromTheta) * top.bestTheta;
                }
            } else {
                for (int i = 0; i < fromCorners.length; i++) {
                    double signedFrom = signedDistanceToPlane(fromSurface, fromCorners[i]);
                    double signedTo = signedDistanceToPlane(toSurface, toCorners[i]);
                    Double t = crossingTheta(signedFrom, signedTo);
                    if (t != null && crossesWithinEitherSurface(fromSurface, toSurface, fromCorners[i], toCorners[i], t)) {
                        double theta = fromTheta + (toTheta - fromTheta) * t;
                        bestTheta = Math.min(bestTheta, theta);
                        found = true;
                    }
                }
            }
        }

        if (!found) {
            return Optional.empty();
        }
        if (bestTheta == Double.POSITIVE_INFINITY) {
            bestTheta = toTheta;
        }
        double localTheta = (toTheta <= fromTheta) ? 1.0 : (bestTheta - fromTheta) / (toTheta - fromTheta);
        localTheta = Math.max(0.0, Math.min(1.0, localTheta));
        OBBSurfaceState surfaceAtTheta = st.interpolate(bestTheta);
        PlayerState playerAtTheta = fromPlayer.interpolate(toPlayer, localTheta);
        InteractionCandidate<T> res = new InteractionCandidate<>(st, surfaceAtTheta, playerAtTheta, bestTheta, isVertical,
                feetCrossed, headCrossed);
        return Optional.of(res);
    }

    private static boolean isVerticalSurface(OBBSurfaceState surface) {
        return Math.abs(surface.normal.getY()) < VERTICAL_NORMAL_EPS;
    }

    private static boolean boxTouchesVerticalSurface(OBBSurfaceState surface, PlayerState player) {
        return boxTouchesVerticalSurface(surface, player.bounds);
    }

    private static boolean boxTouchesVerticalSurface(OBBSurfaceState surface, AABBHandle aabb) {
        boolean overlapsSurface = false;
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (Vector corner : allCorners(aabb)) {
            double signed = signedDistanceToPlane(surface, corner);
            if (signed >= -CROSSING_MARGIN) hasPositive = true;
            if (signed <= CROSSING_MARGIN) hasNegative = true;
            Vector proj = surface.projectPointOntoPlane(corner, new Vector());
            if (surface.containsPointOnPlane(proj)) {
                overlapsSurface = true;
                if (Math.abs(signed) <= CROSSING_MARGIN) {
                    return true;
                }
            }
        }
        return overlapsSurface && hasPositive && hasNegative;
    }

    private static boolean boxTouchesSurfaceFootprint(AABBHandle aabb, OBBSurfaceState surface) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector corner : allCorners(aabb)) {
            Vector local = surface.worldToLocal(corner, new Vector());
            minX = Math.min(minX, local.getX());
            maxX = Math.max(maxX, local.getX());
            minZ = Math.min(minZ, local.getZ());
            maxZ = Math.max(maxZ, local.getZ());
        }
        return maxX >= -surface.halfSize.getX() - 1e-6
                && minX <= surface.halfSize.getX() + 1e-6
                && maxZ >= -surface.halfSize.getZ() - 1e-6
                && minZ <= surface.halfSize.getZ() + 1e-6;
    }

    public static final class Result<T> {
        public final AABBHandle bounds;
        public final T lastSurface;
        public final CollisionMode lastCollisionMode;
        public final OBBSurfaceState lastSurfaceFromState;
        public final OBBSurfaceState lastSurfaceToState;
        /** Player bounding box transition that caused this result */
        public final PlayerTransition playerTransition;
        /** All surface transitions that caused the solver to modify the result */
        public final java.util.List<OBBSurfaceTransition<T>> involvedTransitions;

        private Result(
                CollisionContext<T> ctx
        ) {
            this(ctx.result, ctx.lastSurface, ctx.lastCollisionMode, ctx.lastSurfaceFromState, ctx.lastSurfaceToState, ctx.playerTransition, ctx.involved);
        }

        public Result(
                AABBHandle bounds,
                T lastSurface,
                CollisionMode lastCollisionMode,
                OBBSurfaceState lastSurfaceFromState,
                OBBSurfaceState lastSurfaceToState,
                PlayerTransition playerTransition,
                java.util.List<OBBSurfaceTransition<T>> involvedTransitions
        ) {
            this.bounds = bounds;
            this.lastSurface = lastSurface;
            this.lastCollisionMode = lastCollisionMode;
            this.lastSurfaceFromState = lastSurfaceFromState;
            this.lastSurfaceToState = lastSurfaceToState;
            this.playerTransition = playerTransition;
            this.involvedTransitions = involvedTransitions;
        }

        public boolean hasCollision() {
            return lastCollisionMode != CollisionMode.NONE;
        }

        /**
         * Prints code used in unit tests to recreate the situation that spawned this collision solver result.
         * This will include the surface transitions involved and the player from/to bbox translation.
         *
         * @return debug test output
         */
        public String printDebugTest() {
            StringBuilder str = new StringBuilder();
            printDebugTest(str, "");
            return str.toString();
        }

        /**
         * Prints code used in unit tests to recreate the situation that spawned this collision solver result.
         * This will include the surface transitions involved and the player from/to bbox translation.
         *
         * @param str StringBuilder to write to
         * @param indentStr Indent string to prefix each line by
         */
        public void printDebugTest(StringBuilder str, String indentStr) {
            str.append(indentStr).append("PlayerTransition playerTransition = ");
            playerTransition.printDebugCreate(str, indentStr);
            str.append("\n\n");

            if (involvedTransitions.size() >= 2) {
                str.append(indentStr).append("List<OBBSurfaceTransition<String>> transitions = ");
                OBBSurfaceTransition.printMultiDebugCreate(involvedTransitions, str, indentStr);

                str.append("\n\n");
                str.append(indentStr).append("PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(\n")
                        .append("transitions, playerTransition);\n");
            } else {
                str.append(indentStr).append("OBBSurfaceTransition<String> transition = ");
                involvedTransitions.get(0).printDebugCreate(str, indentStr);

                str.append("\n\n");
                str.append(indentStr).append("PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(\n")
                        .append("Collections.singletonList(transition), playerTransition);\n");
            }
        }

        @Override
        public String toString() {
            return "Result{mode=" + lastCollisionMode + ", bounds=" + bounds + "}";
        }
    }

    /**
     * Backwards-compatible non-generic solver convenience. Accepts raw/unknown
     * typed surface lists so callers that use Collections.singletonList(...) or
     * raw lists do not require explicit generic type hints.
     */
    public <T> AABBHandle solve(List<? extends OBBSurfaceTransition<T>> surfaces, PlayerTransition player) {
        return this.solveDetailed(surfaces, player).bounds;
    }

    /**
     * Instance variant of solveDetailed. Prefer using instance methods so the solver
     * can use its own logger.
     */
    public <T> Result<T> solveDetailed(List<? extends OBBSurfaceTransition<T>> surfaces, PlayerTransition player) {
        if (surfaces == null || surfaces.isEmpty()) {
            return new Result<>(player.to.bounds, null, CollisionMode.NONE, null, null, player, java.util.Collections.emptyList());
        }

        CollisionContext<T> ctx = new CollisionContext<>(player);

        // Iterate surfaces repeatedly until a stable result is reached. Each pass
        // processes surfaces ordered by the first-encountered theta so nearer
        // collisions cannot be overridden by further-away surfaces.
        final int MAX_ITERS = 8;
        int loopGuard = 0;
        double thetaLower = 0.0;
        for (int iter = 0; iter < MAX_ITERS; iter++) {
            AABBHandle prev = ctx.result;

            double bestTheta = Double.POSITIVE_INFINITY;
            boolean changedAtTheta;
            java.util.Set<OBBSurfaceTransition<T>> processedAtTheta = new java.util.HashSet<>();
            do {
                AABBHandle beforeBatch = ctx.result;

                // Find the earliest interaction candidates among surfaces. If multiple
                // surfaces are hit at essentially the same theta, process all of them
                // before advancing the search window. This lets perpendicular walls
                // naturally combine into a corner block without a special-case early exit.
                java.util.List<InteractionCandidate<T>> bestCands = new java.util.ArrayList<>();
                bestTheta = Double.POSITIVE_INFINITY;
                for (OBBSurfaceTransition<T> st : surfaces) {
                    if (st == null || st.from == null || st.to == null) continue;
                    if (processedAtTheta.contains(st)) continue;
                    // Skip interpolated crossing detection for the current floor surface.
                    // When isFloor is true, the surface is the one the player is already
                    // standing on. Checking it against interpolated player movement produces
                    // false FEET collisions whenever the surface rotates (animation). The
                    // dedicated floor-clamp pass below still prevents actual clipping.
                    if (st.isFloor) continue;
                    Optional<InteractionCandidate<T>> copt = this.computeInteractionCandidate(st, player, thetaLower);
                    if (!copt.isPresent()) continue;
                    InteractionCandidate<T> cand = copt.get();
                    if (cand.theta + COMPARE_EPS < bestTheta) {
                        bestTheta = cand.theta;
                        bestCands.clear();
                        bestCands.add(cand);
                    } else if (Math.abs(cand.theta - bestTheta) <= COMPARE_EPS) {
                        bestCands.add(cand);
                    }
                }

                // If no interaction candidate, we are done.
                if (bestCands.isEmpty()) {
                    bestTheta = Double.POSITIVE_INFINITY;
                    break;
                }

                // Process less level surfaces first so the most level surface wins when
                // multiple surfaces block at the same theta.
                bestCands.sort((a, b) -> Double.compare(
                        surfaceLevelScore(a.surfaceAtTheta),
                        surfaceLevelScore(b.surfaceAtTheta)
                ));

                // Process all candidates that occur at the same earliest theta.
                for (InteractionCandidate<T> bestCand : bestCands) {
                    this.processSurface(bestCand, player, ctx, surfaces);
                    processedAtTheta.add(bestCand.st);
                }

                // If processing the current theta changed the result, search the
                // same theta again so another wall revealed by the clamp can be
                // found before we advance further along the motion.
                changedAtTheta = !areAABBsEqual(beforeBatch, ctx.result);
                if (++loopGuard > LOOP_GUARD_LIMIT) {
                    throw new IllegalStateException("PlayerCollisionSolver exceeded " + LOOP_GUARD_LIMIT
                            + " stabilization iterations; possible infinite loop. player=" + player
                            + ", result=" + ctx.result + ", thetaLower=" + thetaLower
                            + ", bestTheta=" + bestTheta);
                }
            } while (changedAtTheta);

            if (bestTheta == Double.POSITIVE_INFINITY) break;

            // Advance past the theta that was just stabilized.
            thetaLower = Math.min(1.0, bestTheta + COMPARE_EPS);

            // If none of the same-theta candidates changed the AABB, we reached stability.
            if (areAABBsEqual(prev, ctx.result)) break;
        }

        // If multiple surfaces were involved, do one deterministic stabilization pass using
        // the player's starting side relative to each surface. This prevents the result from
        // bouncing back and forth between adjacent surfaces after the main sweep has found
        // the first collision.
        this.pickBestMultiSurfaceResultIfNeeded(ctx, player, surfaces);

        // Floor-clamp pass: for surfaces marked isFloor (the surface the player is currently
        // standing on), the interpolated crossing sweep was skipped above to avoid false FEET
        // collisions during surface-rotation animations. This pass runs AFTER all other surface
        // collision adjustments (walls, ceilings, pickBestMultiSurface), so any AABB drift
        // introduced by those collisions is caught here. Only corners within the surface
        // footprint are considered, so walking off an edge is never incorrectly clamped.
        for (OBBSurfaceTransition<T> st : surfaces) {
            if (st == null || st.from == null || st.to == null || !st.isFloor) continue;
            AABBHandle clamped = clampFeetAbovePlane(ctx.result, st.to);
            double dy = clamped.getMinY() - ctx.result.getMinY();
            if (Math.abs(dy) > EPS) {
                ctx.result = clamped;
                ctx.lastSurface = st.source;
                ctx.lastCollisionMode = CollisionMode.FEET;
                ctx.lastSurfaceFromState = st.from;
                ctx.lastSurfaceToState = st.to;
                if (!ctx.involved.contains(st)) {
                    ctx.involved.add(st);
                }
            }
        }

        // Defensive sanity check on produced result
        this.validateResultBounds(ctx, player);

        return new Result<>(ctx);
    }

    /**
     * Tests whether the player is standing on a surface directly below their feet.
     * This performs a very small downward sweep and returns the same {@link Result}
     * type as {@link #solveDetailed(List, PlayerTransition)} so callers can inspect
     * the surface that is currently supporting the player.
     *
     * @param surfaces Surface transitions to test against
     * @param playerBounds Player bounding box at the current position
     * @param <T> Surface source type
     * @return Solver result describing the supporting surface, or {@link CollisionMode#NONE}
     *         when no surface is directly below the player's feet
     */
    public <T> Result<T> solveBelowFeetDetailed(List<? extends OBBSurfaceTransition<T>> surfaces, AABBHandle playerBounds) {
        if (surfaces == null || surfaces.isEmpty() || playerBounds == null) {
            return new Result<>(playerBounds, null, CollisionMode.NONE, null, null,
                    new PlayerTransition(playerBounds, playerBounds), java.util.Collections.emptyList());
        }

        PlayerTransition supportCheck = new PlayerTransition(
                playerBounds,
                translate(playerBounds, 0.0, -SUPPORT_SEARCH_DISTANCE, 0.0)
        );
        Result<T> result = this.solveDetailed(surfaces, supportCheck);
        if (result.lastCollisionMode != CollisionMode.FEET || result.lastSurface == null) {
            return new Result<>(playerBounds, null, CollisionMode.NONE, null, null, supportCheck,
                    java.util.Collections.emptyList());
        }
        return result;
    }

    // Helper mutable context to collect result state while iterating surfaces
    private static final class CollisionContext<T> {
        PlayerTransition playerTransition;
        AABBHandle result;
        T lastSurface = null;
        CollisionMode lastCollisionMode = CollisionMode.NONE;
        OBBSurfaceState lastSurfaceFromState = null;
        OBBSurfaceState lastSurfaceToState = null;
        java.util.List<OBBSurfaceTransition<T>> involved = new java.util.ArrayList<>();

        CollisionContext(PlayerTransition playerTransition) {
            this.playerTransition = playerTransition;
            this.result = playerTransition.to.bounds;
        }
    }

    private <T> void processSurface(InteractionCandidate<T> cand, PlayerTransition player,
                                    CollisionContext<T> ctx, List<? extends OBBSurfaceTransition<T>> surfaces) {
        if (cand.isVertical) {
            this.processVerticalWallSurfaceAtInterp(cand, player, ctx, surfaces);
            return;
        }

        boolean changed = false;
        if (cand.feetCrossed) {
            // Clamp the final result so the player's feet do not end below the surface
            AABBHandle clampedFinal = clampFeetAbovePlane(ctx.result, cand.st.to);
            double dy = clampedFinal.getMinY() - ctx.result.getMinY();
            this.logger.debugFeetClamp(cand.st, dy, cand.theta);
            if (Math.abs(dy) > EPS) {
                ctx.result = clampedFinal;
                ctx.lastSurface = cand.st.source;
                ctx.lastCollisionMode = CollisionMode.FEET;
                ctx.lastSurfaceFromState = cand.st.from;
                ctx.lastSurfaceToState = cand.st.to;
                ctx.involved.add(cand.st);
                changed = true;
            }
        }
        if (cand.headCrossed) {
            // Clamp the final result so the player's head does not end above the surface
            AABBHandle clampedFinal = clampHeadBelowPlane(ctx.result, cand.st.to);
            double dy = clampedFinal.getMaxY() - ctx.result.getMaxY();
            this.logger.debugHeadClamp(cand.st, dy, cand.theta);
            if (Math.abs(dy) > EPS) {
                ctx.result = clampedFinal;
                ctx.lastSurface = cand.st.source;
                ctx.lastCollisionMode = CollisionMode.HEAD;
                ctx.lastSurfaceFromState = cand.st.from;
                ctx.lastSurfaceToState = cand.st.to;
                ctx.involved.add(cand.st);
                changed = true;
            }
        }

        if (!cand.feetCrossed && !cand.headCrossed) {
            if (boxTouchesSurfaceFootprint(ctx.result, cand.surfaceAtTheta)) {
                changed |= this.clampResolvedToOneSide(cand, ctx);
            }
        } else if (changed) {
            Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, cand.surfaceAtTheta);
            if (preservePositiveSide == null) {
                double centerSigned = signedDistanceToPlane(cand.surfaceAtTheta, center(cand.playerAtTheta.bounds));
                preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
            }

            boolean violatesPreferredSide = preservePositiveSide
                    ? (minSignedDistanceToPlane(ctx.result, cand.surfaceAtTheta) < -COMPARE_EPS)
                    : (maxSignedDistanceToPlane(ctx.result, cand.surfaceAtTheta) > COMPARE_EPS);
            if (violatesPreferredSide) {
                changed |= this.clampResolvedToOneSide(cand, ctx);
            }
        }
    }

    private <T> boolean clampResolvedToOneSide(InteractionCandidate<T> cand, CollisionContext<T> ctx) {
        double centerSigned = signedDistanceToPlane(cand.surfaceAtTheta, center(cand.playerAtTheta.bounds));
        Boolean preservePositiveSide;
        if (Math.abs(centerSigned) > COMPARE_EPS) {
            preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
        } else {
            preservePositiveSide = preferredWallClampPositiveSide(ctx.playerTransition.from, ctx.playerTransition.to, cand.surfaceAtTheta);
            if (preservePositiveSide == null) {
                preservePositiveSide = Boolean.TRUE;
            }
        }

        AABBHandle clampedFinal = preservePositiveSide
                ? clampToPositiveSideOfPlane(ctx.result, cand.surfaceAtTheta)
                : clampToNegativeSideOfPlane(ctx.result, cand.surfaceAtTheta);

        double dx = clampedFinal.getMinX() - ctx.result.getMinX();
        double dy = clampedFinal.getMinY() - ctx.result.getMinY();
        double dz = clampedFinal.getMinZ() - ctx.result.getMinZ();
        if (Math.abs(dx) > EPS || Math.abs(dy) > EPS || Math.abs(dz) > EPS) {
            this.logger.debugWallClamp(cand.st, dx, dy, dz);
            ctx.result = clampedFinal;
            ctx.lastSurface = cand.st.source;
            // For inverted-normal surfaces (normal.Y < 0) the "positive side" is physically
            // below the surface in world space, so clamping to the positive side pushes the
            // player DOWN (HEAD) not up (FEET). Flip the FEET/HEAD determination accordingly
            // so that "above the surface in world Y" always maps to FEET.
            boolean feetMode = preservePositiveSide == (cand.surfaceAtTheta.normal.getY() >= 0.0);
            ctx.lastCollisionMode = isWrongDirectionWallClamp(ctx.playerTransition.from, ctx.playerTransition.to, cand.surfaceAtTheta)
                    ? CollisionMode.WALL
                    : (feetMode ? CollisionMode.FEET : CollisionMode.HEAD);
            ctx.lastSurfaceFromState = cand.st.from;
            ctx.lastSurfaceToState = cand.st.to;
            ctx.involved.add(cand.st);
            return true;
        }
        return false;
    }

    private <T> void processVerticalWallSurfaceAtInterp(InteractionCandidate<T> cand, PlayerTransition player,
                                                        CollisionContext<T> ctx, List<? extends OBBSurfaceTransition<T>> surfaces) {
        OBBSurfaceState surface = cand.surfaceAtTheta;

        if (!boxTouchesVerticalSurface(surface, cand.playerAtTheta.bounds)) {
            return;
        }

        Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, surface);
        if (preservePositiveSide == null) {
            double centerSigned = signedDistanceToPlane(surface, center(cand.playerAtTheta.bounds));
            preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
        }
        AABBHandle clampedFinal;
        if (preservePositiveSide) {
            clampedFinal = clampToPositiveSideOfPlane(ctx.result, cand.st.to);
        } else {
            clampedFinal = clampToNegativeSideOfPlane(ctx.result, cand.st.to);
        }

        double dx = clampedFinal.getMinX() - ctx.result.getMinX();
        double dy = clampedFinal.getMinY() - ctx.result.getMinY();
        double dz = clampedFinal.getMinZ() - ctx.result.getMinZ();
        if (Math.abs(dx) > EPS || Math.abs(dy) > EPS || Math.abs(dz) > EPS) {
            this.logger.debugWallClamp(cand.st, dx, dy, dz);
            ctx.result = clampedFinal;
            ctx.lastSurface = cand.st.source;
            ctx.lastCollisionMode = CollisionMode.WALL;
            ctx.lastSurfaceFromState = cand.st.from;
            ctx.lastSurfaceToState = cand.st.to;
            ctx.involved.add(cand.st);
        }
    }

    // Helper result for face crossing checks (feet/head)
    private static final class FaceCross {
        boolean crossed = false;
        double bestTheta = Double.POSITIVE_INFINITY;
    }

    /**
     * Check crossings for a single face (either bottom/feet or top/head) for non-vertical surfaces.
     * Returns a FaceCross indicating whether a crossing was detected and the earliest theta found.
     *
     * @param fromSurface Surface state at the start of the interval
     * @param toSurface Surface state at the end of the interval
     * @param fromFace Corner positions for the face being checked at the start of motion
     * @param toFace Corner positions for the face being checked at the end of motion
     * @param minTheta Minimum theta to consider for crossings (to avoid re-checking previously processed candidates)
     * @param isCrossingDown Whether we are checking for a crossing downwards (onto player feet) or upwards (against player head)
     */
    private static FaceCross checkFaceCrossing(OBBSurfaceState fromSurface, OBBSurfaceState toSurface, Vector[] fromFace, Vector[] toFace, double minTheta, boolean isCrossingDown) {
        FaceCross res = new FaceCross();
        for (int i = 0; i < 4; i++) {
            double planeFromY = planeYAtXZ(fromSurface, fromFace[i].getX(), fromFace[i].getZ());
            double planeToY = planeYAtXZ(toSurface, toFace[i].getX(), toFace[i].getZ());
            double fromDiff = fromFace[i].getY() - planeFromY;
            double toDiff = toFace[i].getY() - planeToY;
            if (!res.crossed) {
                boolean isCrossed;
                if (isCrossingDown) {
                    // When testing for a crossing downwards (onto player feet)
                    isCrossed = (fromDiff > -COMPARE_EPS && toDiff < COMPARE_EPS);
                } else {
                    // When testing for a crossing upwards (against player head)
                    isCrossed = (fromDiff < COMPARE_EPS && toDiff > -COMPARE_EPS);
                }
                if (isCrossed) {
                    double signedFrom = signedDistanceToPlane(fromSurface, fromFace[i]);
                    double signedTo = signedDistanceToPlane(toSurface, toFace[i]);
                    Double t = crossingTheta(signedFrom, signedTo);
                    if (t != null && t + COMPARE_EPS >= minTheta && crossesWithinSurface(fromSurface, fromFace[i], toFace[i], t)) {
                        res.crossed = true;
                        res.bestTheta = Math.min(res.bestTheta, t);
                    }
                }
            }
        }
        return res;
    }

    private <T> void pickBestMultiSurfaceResultIfNeeded(CollisionContext<T> ctx, PlayerTransition player,
                                                        List<? extends OBBSurfaceTransition<T>> surfaces) {
        if (surfaces == null || surfaces.size() < 2) {
            return;
        }

        java.util.List<OBBSurfaceTransition<T>> orderedSurfaces = new java.util.ArrayList<>(surfaces.size());
        for (OBBSurfaceTransition<T> st : surfaces) {
            // isFloor surfaces are excluded from the interpolated sweep and are handled
            // exclusively by the dedicated floor-clamp pass that runs after this method.
            // Including them here would apply an imprecise normal-direction push that may
            // interfere with the more targeted clampFeetAbovePlane done by the floor-clamp pass.
            if (st != null && st.to != null && !st.isFloor) {
                orderedSurfaces.add(st);
            }
        }
        orderedSurfaces.sort((a, b) -> Double.compare(
                surfaceLevelScore(a.to),
                surfaceLevelScore(b.to)
        ));

        for (int iter = 0; iter < 4; iter++) {
            AABBHandle before = ctx.result;
            for (OBBSurfaceTransition<T> st : orderedSurfaces) {
                OBBSurfaceState surface = st.to;

                // Use the from-state surface to determine which side the player was initially on.
                // Comparing player.from against st.to is misleading when the surface moved: the
                // player's touching face may appear on the wrong side of the moved surface even
                // though the player never crossed it.
                OBBSurfaceState initialSurface = (st.from != null) ? st.from : surface;

                if (isVerticalSurface(surface)) {
                    if (!boxTouchesVerticalSurface(surface, ctx.result)) {
                        continue;
                    }

                    Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, initialSurface, surface);
                    if (preservePositiveSide == null) {
                        double centerSigned = signedDistanceToPlane(initialSurface, center(player.from.bounds));
                        preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
                    }

                    AABBHandle clamped = preservePositiveSide
                            ? clampToPositiveSideOfPlane(ctx.result, surface)
                            : clampToNegativeSideOfPlane(ctx.result, surface);
                    if (!areAABBsEqual(clamped, ctx.result)) {
                        ctx.result = clamped;
                        ctx.lastSurface = st.source;
                        ctx.lastCollisionMode = CollisionMode.WALL;
                        ctx.lastSurfaceFromState = st.from;
                        ctx.lastSurfaceToState = st.to;
                        ctx.involved.add(st);
                    }
                } else {
                    double startCenterSigned = signedDistanceToPlane(initialSurface, center(player.from.bounds));
                    if (!boxTouchesSurfaceFootprint(player.from.bounds, surface)
                            && !boxTouchesSurfaceFootprint(player.to.bounds, surface)
                            && !boxTouchesSurfaceFootprint(ctx.result, surface)) {
                        continue;
                    }
                    AABBHandle clamped = (startCenterSigned >= 0.0)
                            ? clampToPositiveSideOfPlane(ctx.result, surface)
                            : clampToNegativeSideOfPlane(ctx.result, surface);
                    if (!areAABBsEqual(clamped, ctx.result)) {
                        ctx.result = clamped;
                        ctx.lastSurface = st.source;
                        // For inverted-normal surfaces (normal.Y < 0) the "positive side" is physically
                        // below the surface in world space, so startCenterSigned >= 0 means the player
                        // was below the surface and should receive HEAD. Flip the FEET/HEAD assignment
                        // so that being physically above the surface (in world Y) always maps to FEET.
                        boolean feetMode = (startCenterSigned >= 0.0) == (surface.normal.getY() >= 0.0);
                        ctx.lastCollisionMode = isWrongDirectionWallClamp(player.from, player.to, surface)
                                ? CollisionMode.WALL
                                : (feetMode ? CollisionMode.FEET : CollisionMode.HEAD);
                        ctx.lastSurfaceFromState = st.from;
                        ctx.lastSurfaceToState = st.to;
                        ctx.involved.add(st);
                    }
                }
            }

            if (areAABBsEqual(before, ctx.result)) {
                break;
            }
        }
    }

    private <T> void validateResultBounds(CollisionContext<T> ctx, PlayerTransition player) {
        try {
            double deltaMinY = Math.abs(ctx.result.getMinY() - player.to.bounds.getMinY());
            double deltaMaxY = Math.abs(ctx.result.getMaxY() - player.to.bounds.getMaxY());
            boolean finite = Double.isFinite(ctx.result.getMinY()) && Double.isFinite(ctx.result.getMaxY());
            if (!finite || deltaMinY > 100.0 || deltaMaxY > 100.0) {
                this.logger.warn("PlayerCollisionSolver produced extreme result bounds; reverting to requested bounds. deltaMinY=" + deltaMinY + " deltaMaxY=" + deltaMaxY + " result=" + ctx.result + " requested=" + player.to.bounds);
                ctx.result = player.to.bounds;
                ctx.lastSurface = null;
                ctx.lastCollisionMode = CollisionMode.NONE;
                ctx.lastSurfaceFromState = null;
                ctx.lastSurfaceToState = null;
                ctx.involved = java.util.Collections.emptyList();
            }
        } catch (Throwable ignored) {}
    }

    private AABBHandle clampFeetAbovePlane(AABBHandle aabb, OBBSurfaceState surf) {
        double y = maxPlaneYAtFace(aabb, surf, false);
        if (!Double.isFinite(y)) {
            return aabb;
        }
        double minY = aabb.getMinY();
        this.logger.debugFeetLog(aabb, surf, y, minY);
        if (minY + EPS >= y + RESOLVE_OFFSET) {
            return aabb;
        }
        double dy = (y + RESOLVE_OFFSET) - minY;
        return translate(aabb, 0.0, dy, 0.0);
    }

    private AABBHandle clampHeadBelowPlane(AABBHandle aabb, OBBSurfaceState surf) {
        double y = minPlaneYAtFace(aabb, surf, true);
        if (!Double.isFinite(y)) {
            return aabb;
        }
        double maxY = aabb.getMaxY();
        this.logger.debugHeadLog(aabb, surf, y, maxY);
        if (maxY - EPS <= y - RESOLVE_OFFSET) {
            return aabb;
        }
        double dy = (y - RESOLVE_OFFSET) - maxY;
        return translate(aabb, 0.0, dy, 0.0);
    }

    private static AABBHandle translate(AABBHandle aabb, double dx, double dy, double dz) {
        return AABBHandle.createNew(
                aabb.getMinX() + dx, aabb.getMinY() + dy, aabb.getMinZ() + dz,
                aabb.getMaxX() + dx, aabb.getMaxY() + dy, aabb.getMaxZ() + dz);
    }

    private static AABBHandle clampToPositiveSideOfPlane(AABBHandle aabb, OBBSurfaceState surf) {
        double minSigned = minSignedDistanceToPlane(aabb, surf);
        if (minSigned + EPS >= RESOLVE_OFFSET) {
            return aabb;
        }
        double distance = RESOLVE_OFFSET - minSigned;
        return translate(aabb,
                surf.normal.getX() * distance,
                surf.normal.getY() * distance,
                surf.normal.getZ() * distance);
    }

    private static AABBHandle clampToNegativeSideOfPlane(AABBHandle aabb, OBBSurfaceState surf) {
        double maxSigned = maxSignedDistanceToPlane(aabb, surf);
        if (maxSigned - EPS <= -RESOLVE_OFFSET) {
            return aabb;
        }
        double distance = -RESOLVE_OFFSET - maxSigned;
        return translate(aabb,
                surf.normal.getX() * distance,
                surf.normal.getY() * distance,
                surf.normal.getZ() * distance);
    }

    private static boolean isWrongDirectionWallClamp(PlayerState from, PlayerState to, OBBSurfaceState surface) {
        Vector movement = center(to.bounds).subtract(center(from.bounds));
        if (movement.lengthSquared() < 1e-20) {
            return false;
        }

        // Purely vertical motion should remain support-like. The wall case only applies when the
        // player is also moving sideways along the surface while approaching it from the wrong side.
        if ((movement.getX() * movement.getX()) + (movement.getZ() * movement.getZ()) < 1e-20) {
            return false;
        }

        Vector uphillDir = projectOntoPlane(new Vector(0.0, 1.0, 0.0), surface.groundNormal);
        if (uphillDir.lengthSquared() < 1e-20) {
            return false;
        }

        Vector planarMovement = projectOntoPlane(movement, surface.groundNormal);
        if (planarMovement.lengthSquared() < 1e-20) {
            return false;
        }

        planarMovement.normalize();
        uphillDir.normalize();
        return planarMovement.dot(uphillDir) <= 1e-6;
    }

    private static Boolean preferredWallClampPositiveSide(PlayerState from, PlayerState to, OBBSurfaceState surface) {
        return preferredWallClampPositiveSide(from, to, surface, surface);
    }

    /**
     * Determines which side of the wall the player was on before the collision, taking separate
     * from-state and to-state surface references. This is important when the surface has moved:
     * {@code player.from} should be compared against {@code fromSurface} (the surface state at
     * the beginning of the tick) to avoid mis-detecting the side when the wall shifted toward
     * the player's initial touching face.
     */
    private static Boolean preferredWallClampPositiveSide(PlayerState from, PlayerState to,
                                                          OBBSurfaceState fromSurface, OBBSurfaceState toSurface) {
        double fromMinSigned = minSignedDistanceToPlane(from.bounds, fromSurface);
        double fromMaxSigned = maxSignedDistanceToPlane(from.bounds, fromSurface);
        if (fromMinSigned >= -COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (fromMaxSigned <= COMPARE_EPS) {
            return Boolean.FALSE;
        }

        double toMinSigned = minSignedDistanceToPlane(to.bounds, toSurface);
        double toMaxSigned = maxSignedDistanceToPlane(to.bounds, toSurface);
        if (toMinSigned >= -COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (toMaxSigned <= COMPARE_EPS) {
            return Boolean.FALSE;
        }

        double fromCenterSigned = signedDistanceToPlane(fromSurface, center(from.bounds));
        double toCenterSigned = signedDistanceToPlane(toSurface, center(to.bounds));
        double deltaSigned = toCenterSigned - fromCenterSigned;
        if (deltaSigned < -COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (deltaSigned > COMPARE_EPS) {
            return Boolean.FALSE;
        }

        if (fromCenterSigned > COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (fromCenterSigned < -COMPARE_EPS) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static double surfaceLevelScore(OBBSurfaceState surface) {
        return surface.groundNormal.getY();
    }

    protected static Double crossingTheta(double signedFrom, double signedTo) {
        double denom = (signedFrom - signedTo);
        if (Math.abs(denom) < EPS) {
            return null;
        }
        double t = signedFrom / denom;
        // Only consider crossings that occur during the motion [0..1]. If the
        // computed parameter lies outside this range, treat it as no crossing.
        if (t < 0.0 || t > 1.0) return null;
        return t;
    }

    protected static boolean crossesPlaneDownwardWithinSurface(OBBSurfaceState surface, Vector from, Vector to) {
        double signedFrom = signedDistanceToPlane(surface, from);
        double signedTo = signedDistanceToPlane(surface, to);
        // Interpret distances relative to an upward-facing normal so that upside-down
        // surfaces (with inverted normals) are treated consistently as walkable.
        if (surface.normal.getY() < 0.0) signedFrom = -signedFrom;
        if (surface.normal.getY() < 0.0) signedTo = -signedTo;
        if (signedFrom < (-CROSSING_MARGIN + COMPARE_EPS) || signedTo > (-CROSSING_MARGIN + COMPARE_EPS)) {
            return false;
        }

        Double theta = crossingTheta(signedFrom, signedTo);
        return theta != null && crossesWithinSurface(surface, from, to, theta);
    }

    private static boolean crossesPlaneUpwardWithinSurface(OBBSurfaceState surface, Vector from, Vector to) {
        double signedFrom = signedDistanceToPlane(surface, from);
        double signedTo = signedDistanceToPlane(surface, to);
        // Interpret distances relative to an upward-facing normal so that upside-down
        // surfaces (with inverted normals) are treated consistently as walkable.
        if (surface.normal.getY() < 0.0) signedFrom = -signedFrom;
        if (surface.normal.getY() < 0.0) signedTo = -signedTo;
        if (signedFrom > (CROSSING_MARGIN - COMPARE_EPS) || signedTo < (CROSSING_MARGIN - COMPARE_EPS)) {
            return false;
        }

        Double theta = crossingTheta(signedFrom, signedTo);
        return theta != null && crossesWithinSurface(surface, from, to, theta);
    }

    private static Vector[] faceCorners(AABBHandle aabb, boolean top) {
        double y = top ? aabb.getMaxY() : aabb.getMinY();
        return new Vector[] {
                new Vector(aabb.getMinX(), y, aabb.getMinZ()),
                new Vector(aabb.getMinX(), y, aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMinZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMaxZ())
        };
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

    private static Vector center(AABBHandle aabb) {
        return new Vector(
                0.5 * (aabb.getMinX() + aabb.getMaxX()),
                0.5 * (aabb.getMinY() + aabb.getMaxY()),
                0.5 * (aabb.getMinZ() + aabb.getMaxZ())
        );
    }

    private static double maxPlaneYAtFace(AABBHandle aabb, OBBSurfaceState surf, boolean top) {
        java.util.List<Double> list = gatherFacePlaneYs(aabb, surf, top);
        return representativePlaneY(list, true);
    }

    private static double minPlaneYAtFace(AABBHandle aabb, OBBSurfaceState surf, boolean top) {
        java.util.List<Double> list = gatherFacePlaneYs(aabb, surf, top);
        return representativePlaneY(list, false);
    }

    private static java.util.List<Double> gatherFacePlaneYs(AABBHandle aabb, OBBSurfaceState surf, boolean top) {
        java.util.ArrayList<Double> list = new java.util.ArrayList<>();
        for (Vector corner : faceCorners(aabb, top)) {
            Vector proj = surf.projectPointOntoPlane(corner, new Vector());
            if (surf.containsPointOnPlane(proj)) {
                list.add(planeYAtXZ(surf, corner.getX(), corner.getZ()));
            }
        }
        return list;
    }

    private static double representativePlaneY(java.util.List<Double> list, boolean wantMax) {
        int count = list.size();
        if (count == 0) return Double.NaN;
        if (count == 1) return list.get(0);

        double[] vals = new double[count];
        for (int i = 0; i < count; i++) vals[i] = list.get(i);
        java.util.Arrays.sort(vals);
        if (count > 2) {
            if (wantMax) {
                double maxY = Double.NEGATIVE_INFINITY;
                for (int i = 1; i < count-1; i++) maxY = Math.max(maxY, vals[i]);
                return maxY;
            } else {
                double minY = Double.POSITIVE_INFINITY;
                for (int i = 1; i < count-1; i++) minY = Math.min(minY, vals[i]);
                return minY;
            }
        } else {
            if (wantMax) {
                double maxY = Double.NEGATIVE_INFINITY;
                for (double v : vals) maxY = Math.max(maxY, v);
                return maxY;
            } else {
                double minY = Double.POSITIVE_INFINITY;
                for (double v : vals) minY = Math.min(minY, v);
                return minY;
            }
        }
    }

    /**
     * Tests whether a player AABB moved through the footprint of a surface transition.
     * This checks the surface extents and interpolates the moving surface while testing
     * each player corner path, which makes it suitable for debug logging.
     */
    public static boolean passesThroughSurface(OBBSurfaceTransition<?> surface, AABBHandle from, AABBHandle to) {
        if (surface == null || surface.from == null || surface.to == null || from == null || to == null) {
            return false;
        }

        boolean fromPositive = minSignedDistanceToPlane(from, surface.from) > CROSSING_MARGIN;
        boolean fromNegative = maxSignedDistanceToPlane(from, surface.from) < -CROSSING_MARGIN;
        boolean toPositive = minSignedDistanceToPlane(to, surface.to) > CROSSING_MARGIN;
        boolean toNegative = maxSignedDistanceToPlane(to, surface.to) < -CROSSING_MARGIN;
        if (!((fromPositive && toNegative) || (fromNegative && toPositive))) {
            return false;
        }

        Vector[] fromCorners = allCorners(from);
        Vector[] toCorners = allCorners(to);
        for (int i = 0; i < fromCorners.length; i++) {
            double signedFrom = signedDistanceToPlane(surface.from, fromCorners[i]);
            double signedTo = signedDistanceToPlane(surface.to, toCorners[i]);
            Double t = crossingTheta(signedFrom, signedTo);
            if (t != null && crossesWithinSurface(surface, fromCorners[i], toCorners[i], t)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean crossesWithinSurface(OBBSurfaceState surface, Vector from, Vector to, double theta) {
        Vector p = new Vector(
                from.getX() + (to.getX() - from.getX()) * theta,
                from.getY() + (to.getY() - from.getY()) * theta,
                from.getZ() + (to.getZ() - from.getZ()) * theta);

        Vector proj = surface.projectPointOntoPlane(p, new Vector());
        if (surface.containsPointOnPlane(proj)) {
            return true;
        }

        return surface.containsPointOnPlane(proj);
    }

    protected static boolean crossesWithinEitherSurface(OBBSurfaceState fromSurface, OBBSurfaceState toSurface, Vector from, Vector to, double theta) {
        return crossesWithinSurface(fromSurface, from, to, theta) || crossesWithinSurface(toSurface, from, to, theta);
    }

    protected static boolean crossesWithinSurface(OBBSurfaceTransition<?> surface, Vector from, Vector to, double theta) {
        return crossesWithinSurface(surface.interpolate(theta), from, to, theta);
    }

    protected static double signedDistanceToPlane(OBBSurfaceState surf, Vector p) {
        Vector proj = surf.projectPointOntoPlane(p, new Vector());
        return (p.getX() - proj.getX()) * surf.normal.getX()
                + (p.getY() - proj.getY()) * surf.normal.getY()
                + (p.getZ() - proj.getZ()) * surf.normal.getZ();
    }

    private static Vector projectOntoPlane(Vector vector, Vector normal) {
        double dot = vector.getX() * normal.getX() + vector.getY() * normal.getY() + vector.getZ() * normal.getZ();
        return new Vector(
                vector.getX() - normal.getX() * dot,
                vector.getY() - normal.getY() * dot,
                vector.getZ() - normal.getZ() * dot);
    }

    private static double minSignedDistanceToPlane(AABBHandle aabb, OBBSurfaceState surf) {
        double min = Double.POSITIVE_INFINITY;
        for (Vector corner : allCorners(aabb)) {
            min = Math.min(min, signedDistanceToPlane(surf, corner));
        }
        return min;
    }

    private static double maxSignedDistanceToPlane(AABBHandle aabb, OBBSurfaceState surf) {
        double max = Double.NEGATIVE_INFINITY;
        for (Vector corner : allCorners(aabb)) {
            max = Math.max(max, signedDistanceToPlane(surf, corner));
        }
        return max;
    }

    private static double planeYAtXZ(OBBSurfaceState surf, double x, double z) {
        double ny = surf.normal.getY();
        // If the normal Y is very small (plane almost vertical), the direct algebraic
        // computation below becomes numerically unstable due to division by ny. In
        // that case, fall back to projecting the point onto the plane and returning
        // the projected Y coordinate. This avoids huge values when dealing with
        // near-vertical surfaces.
        // We also guard against cases where the algebraic result is finite but
        // wildly far from the surface center Y (likely caused by division by a
        // small ny). In such a case, prefer the projection-based result.
        double yAlgebraic = Double.NaN;
        if (Math.abs(ny) >= VERTICAL_NORMAL_EPS) {
            yAlgebraic = surf.center.getY() - (((x - surf.center.getX()) * surf.normal.getX())
                    + ((z - surf.center.getZ()) * surf.normal.getZ())) / ny;
        }

        // Also compute the projection-based Y so we can compare both results. If they
        // disagree significantly, prefer the projection because it is numerically more
        // stable for near-vertical or skewed planes.
        Vector proj = surf.projectPointOntoPlane(new Vector(x, surf.center.getY(), z), new Vector());
        double yProj = proj.getY();

        // Consider algebraic invalid when the horizontal normal components dominate the
        // vertical component by a large factor: that makes division by ny unstable.
        double nx = surf.normal.getX();
        double nz = surf.normal.getZ();
        boolean algebraicValid = Double.isFinite(yAlgebraic) && Math.abs(yAlgebraic - surf.center.getY()) < 200.0;
        // Reject algebraic if worst-case vertical deviation across the surface
        // (based on half extents and horizontal normal components) is excessively large.
        if (algebraicValid && Math.abs(ny) > 0.0) {
            double worstDelta = (Math.abs(surf.halfSize.getX() * nx) + Math.abs(surf.halfSize.getZ() * nz)) / Math.abs(ny);
            // Determine a dynamic threshold based on surface size: larger surfaces
            // can tolerate larger algebraic deviations. Use max of a base limit and
            // a multiple of the largest half-size dimension.
            double dynamicLimit = Math.max(ALGEBRAIC_WORST_DELTA_LIMIT,
                    5.0 * Math.max(surf.halfSize.getX(), surf.halfSize.getZ()));
            if (worstDelta > dynamicLimit) {
                algebraicValid = false;
            }
        }
        // No separate horizontal/vertical ratio test: prefer projection when
        // algebraic appears extreme relative to center or differs strongly from
        // the projection result. The existing checks below handle selection.
        boolean projValid = Double.isFinite(yProj) && Math.abs(yProj - surf.center.getY()) < 200.0;

        if (algebraicValid && projValid) {
            // If both are valid but disagree significantly, prefer the projection
            // result because projecting is numerically stable for skewed/near-
            // vertical planes. Only use algebraic when both agree closely.
            if (Math.abs(yAlgebraic - yProj) > ALGEBRAIC_PROJECTION_DIFF_EPS) {
                return yProj;
            }
            return yAlgebraic;
        }

        if (algebraicValid) {
            return yAlgebraic;
        }

        if (projValid) {
            return yProj;
        }

        // Fallback: use the surface center Y when nothing reasonable can be derived.
        return surf.center.getY();
    }
}
