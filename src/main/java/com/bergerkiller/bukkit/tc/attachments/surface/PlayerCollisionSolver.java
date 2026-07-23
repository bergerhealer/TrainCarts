package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Comparator;
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
class PlayerCollisionSolver {
    private static final double EPS = 1e-9;
    /** Small downward sweep used when checking what surface the player is standing on */
    private static final double SUPPORT_SEARCH_DISTANCE = 0.15;
    /** Threshold used to treat a surface as near-vertical for stability in various calculations */
    private static final double VERTICAL_NORMAL_EPS = OBBSurfaceState.VERTICAL_NORMAL_EPS;
    /** Distance that counts as definitely penetrating the plane */
    private static final double CROSSING_MARGIN = OBBSurfaceState.CROSSING_MARGIN;
    /** Tiny comparison tolerance for floating-point rounding around the crossing margin */
    private static final double COMPARE_EPS = 1e-6;
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
        final PlayerBoundsState playerAtTheta;
        final double theta; // first encounter parameter along motion [0..1]
        final boolean isVertical;
        final boolean feetCrossed;
        final boolean headCrossed;

        InteractionCandidate(OBBSurfaceTransition<T> st, OBBSurfaceState surfaceAtTheta, PlayerBoundsState playerAtTheta, double theta, boolean isVertical,
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

    private static boolean areAABBsSimilar(AABBHandle a, AABBHandle b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Math.abs(a.getMinX() - b.getMinX()) <= COMPARE_EPS
                && Math.abs(a.getMinY() - b.getMinY()) <= COMPARE_EPS
                && Math.abs(a.getMinZ() - b.getMinZ()) <= COMPARE_EPS
                && Math.abs(a.getMaxX() - b.getMaxX()) <= COMPARE_EPS
                && Math.abs(a.getMaxY() - b.getMaxY()) <= COMPARE_EPS
                && Math.abs(a.getMaxZ() - b.getMaxZ()) <= COMPARE_EPS;
    }

    private <T> Optional<InteractionCandidate<T>> computeInteractionCandidate(OBBSurfaceTransition<T> st,
                                                                              PlayerBoundsTransition player,
                                                                              double minTheta) {
        double startTheta = Math.max(0.0, Math.min(1.0, minTheta));
        if (startTheta >= 1.0) {
            return Optional.empty();
        }

        final int ROUGH_STEPS = 12;
        double prevTheta = startTheta;
        OBBSurfaceState prevSurface = st.interpolate(prevTheta);
        PlayerBoundsState prevPlayer = player.interpolate(prevTheta);

        for (int i = 1; i <= ROUGH_STEPS; i++) {
            double theta = startTheta + (1.0 - startTheta) * (i / (double) ROUGH_STEPS);
            OBBSurfaceState currSurface = st.interpolate(theta);
            PlayerBoundsState currPlayer = player.interpolate(theta);

            Optional<InteractionCandidate<T>> cand = evaluateInteractionCandidateInterval(
                    st, prevSurface, currSurface, prevPlayer, currPlayer, prevTheta, theta);
            if (cand.isPresent()) {
                InteractionCandidate<T> best = cand.get();
                this.logger.debugCandidate(st, best.theta, best.isVertical, best.feetCrossed, best.headCrossed,
                        player.from.corners(), best.playerAtTheta.corners());
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
                                                                                              PlayerBoundsState fromPlayer, PlayerBoundsState toPlayer,
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
        Vector[] fromCorners = fromPlayer.corners();
        Vector[] toCorners = toPlayer.corners();

        if (isVertical) {
            for (int i = 0; i < fromCorners.length; i++) {
                double signedFrom = fromSurface.signedDistanceToPlane(fromCorners[i]);
                double signedTo = toSurface.signedDistanceToPlane(toCorners[i]);
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
                    double signedFrom = fromSurface.signedDistanceToPlane(fromCorners[i]);
                    double signedTo = toSurface.signedDistanceToPlane(toCorners[i]);
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
        PlayerBoundsState playerAtTheta = fromPlayer.interpolate(toPlayer, localTheta);
        InteractionCandidate<T> res = new InteractionCandidate<>(st, surfaceAtTheta, playerAtTheta, bestTheta, isVertical,
                feetCrossed, headCrossed);
        return Optional.of(res);
    }

    private static boolean isVerticalSurface(OBBSurfaceState surface) {
        return Math.abs(surface.normal.getY()) < VERTICAL_NORMAL_EPS;
    }

    public static final class Result<T> {
        /** The player state after all surface interactions have been processed */
        public final PlayerBoundsState state;
        public final T lastSurface;
        public final CollisionMode lastCollisionMode;
        public final OBBSurfaceState lastSurfaceFromState;
        public final OBBSurfaceState lastSurfaceToState;
        /** Player bounding box transition that caused this result */
        public final PlayerBoundsTransition playerTransition;
        /** All surface transitions that caused the solver to modify the result */
        public final java.util.List<OBBSurfaceTransition<T>> involvedTransitions;

        private Result(
                CollisionContext<T> ctx
        ) {
            this(ctx.result, ctx.lastSurface, ctx.lastCollisionMode, ctx.lastSurfaceFromState, ctx.lastSurfaceToState, ctx.playerTransition, ctx.involved);
        }

        public Result(
                PlayerBoundsState bounds,
                T lastSurface,
                CollisionMode lastCollisionMode,
                OBBSurfaceState lastSurfaceFromState,
                OBBSurfaceState lastSurfaceToState,
                PlayerBoundsTransition playerTransition,
                java.util.List<OBBSurfaceTransition<T>> involvedTransitions
        ) {
            this.state = bounds;
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
            str.append(indentStr).append("PlayerBoundsTransition playerTransition = ");
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
            return "Result{mode=" + lastCollisionMode + ", bounds=" + state + "}";
        }
    }

    /**
     * Backwards-compatible non-generic solver convenience. Accepts raw/unknown
     * typed surface lists so callers that use Collections.singletonList(...) or
     * raw lists do not require explicit generic type hints.
     */
    public <T> AABBHandle solve(List<? extends OBBSurfaceTransition<T>> surfaces, PlayerBoundsTransition player) {
        return this.solveDetailed(surfaces, player).state.bounds;
    }

    /**
     * Instance variant of solveDetailed. Prefer using instance methods so the solver
     * can use its own logger.
     */
    public <T> Result<T> solveDetailed(List<? extends OBBSurfaceTransition<T>> surfaces, PlayerBoundsTransition player) {
        if (surfaces == null || surfaces.isEmpty()) {
            return new Result<>(player.to, null, CollisionMode.NONE, null, null, player, java.util.Collections.emptyList());
        }

        CollisionContext<T> ctx = new CollisionContext<>(player);

        // Iterate surfaces repeatedly until a stable result is reached. Each pass
        // processes surfaces ordered by the first-encountered theta so nearer
        // collisions cannot be overridden by further-away surfaces.
        final int MAX_ITERS = 8;
        int loopGuard = 0;
        double thetaLower = 0.0;
        for (int iter = 0; iter < MAX_ITERS; iter++) {
            AABBHandle prev = ctx.result.bounds;

            double bestTheta;
            boolean changedAtTheta;
            java.util.Set<OBBSurfaceTransition<T>> processedAtTheta = new java.util.HashSet<>();
            do {
                AABBHandle beforeBatch = ctx.result.bounds;

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
                bestCands.sort(Comparator.comparingDouble(a -> surfaceLevelScore(a.surfaceAtTheta)));

                // Process all candidates that occur at the same earliest theta.
                for (InteractionCandidate<T> bestCand : bestCands) {
                    this.processSurface(bestCand, player, ctx, surfaces);
                    processedAtTheta.add(bestCand.st);
                }

                // If processing the current theta changed the result, search the
                // same theta again so another wall revealed by the clamp can be
                // found before we advance further along the motion.
                changedAtTheta = !areAABBsSimilar(beforeBatch, ctx.result.bounds);
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
            if (areAABBsSimilar(prev, ctx.result.bounds)) break;
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
            PlayerBoundsState clamped = clampFeetAbovePlane(st.to, ctx.result);
            double dy = clamped.bounds.getMinY() - ctx.result.bounds.getMinY();
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
     * type as {@link #solveDetailed(List, PlayerBoundsTransition)} so callers can inspect
     * the surface that is currently supporting the player.
     *
     * @param surfaces Surface transitions to test against
     * @param playerBounds Player bounding box at the current position
     * @param <T> Surface source type
     * @return Solver result describing the supporting surface, or {@link CollisionMode#NONE}
     *         when no surface is directly below the player's feet
     */
    public <T> Result<T> solveBelowFeetDetailed(List<? extends OBBSurfaceTransition<T>> surfaces, PlayerBoundsState playerBounds) {
        if (surfaces == null || surfaces.isEmpty() || playerBounds == null) {
            return new Result<>(playerBounds, null, CollisionMode.NONE, null, null,
                    new PlayerBoundsTransition(playerBounds, playerBounds), java.util.Collections.emptyList());
        }

        PlayerBoundsTransition supportCheck = new PlayerBoundsTransition(
                playerBounds,
                playerBounds.translate( 0.0, -SUPPORT_SEARCH_DISTANCE, 0.0)
        );
        Result<T> result = this.solveDetailed(surfaces, supportCheck);
        if (result.lastCollisionMode != CollisionMode.FEET || result.lastSurface == null) {
            return new Result<>(playerBounds, null, CollisionMode.NONE, null, null, supportCheck,
                    java.util.Collections.emptyList());
        }
        return result;
    }

    public boolean hasSurfaceSupport(OBBSurfaceTransition<?> transition, PlayerBoundsState actualBounds, Vector worldPosition, double gravity) {
        Vector actualFeetPosition = actualBounds.feetPosition();
        PlayerBoundsState bboxAtPosition = actualBounds.translate(
                worldPosition.getX() - actualFeetPosition.getX(),
                worldPosition.getY() - actualFeetPosition.getY(),
                worldPosition.getZ() - actualFeetPosition.getZ());
        if (transition.to.bottomFaceIntersectsSurfacePlane(bboxAtPosition.bounds)) {
            return true;
        }
        PlayerBoundsState bboxBelow = bboxAtPosition.translate(0.0, -gravity, 0.0);
        PlayerCollisionSolver.Result<?> result = solveDetailed(
                Collections.singletonList(transition),
                new PlayerBoundsTransition(bboxAtPosition, bboxBelow)
        );
        return result.lastCollisionMode == PlayerCollisionSolver.CollisionMode.FEET;
    }

    // Helper mutable context to collect result state while iterating surfaces
    private static final class CollisionContext<T> {
        PlayerBoundsTransition playerTransition;
        PlayerBoundsState result;
        T lastSurface = null;
        CollisionMode lastCollisionMode = CollisionMode.NONE;
        OBBSurfaceState lastSurfaceFromState = null;
        OBBSurfaceState lastSurfaceToState = null;
        java.util.List<OBBSurfaceTransition<T>> involved = new java.util.ArrayList<>();

        CollisionContext(PlayerBoundsTransition playerTransition) {
            this.playerTransition = playerTransition;
            this.result = playerTransition.to;
        }
    }

    private <T> void processSurface(InteractionCandidate<T> cand, PlayerBoundsTransition player,
                                    CollisionContext<T> ctx, List<? extends OBBSurfaceTransition<T>> surfaces) {
        if (cand.isVertical) {
            this.processVerticalWallSurfaceAtInterp(cand, player, ctx, surfaces);
            return;
        }

        boolean changed = false;
        if (cand.feetCrossed) {
            // Clamp the final result so the player's feet do not end below the surface
            PlayerBoundsState clampedFinal = clampFeetAbovePlane(cand.st.to, ctx.result);
            double dy = clampedFinal.bounds.getMinY() - ctx.result.bounds.getMinY();
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
            PlayerBoundsState clampedFinal = clampHeadBelowPlane(cand.st.to, ctx.result);
            double dy = clampedFinal.bounds.getMaxY() - ctx.result.bounds.getMaxY();
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
            if (cand.surfaceAtTheta.boxTouchesSurfaceFootprint(ctx.result)) {
                this.clampResolvedToOneSide(cand, ctx);
            }
        } else if (changed) {
            Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, cand.surfaceAtTheta);
            if (preservePositiveSide == null) {
                double centerSigned = cand.surfaceAtTheta.signedDistanceToPlane(cand.playerAtTheta.center());
                preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
            }

            boolean violatesPreferredSide = preservePositiveSide
                    ? (cand.surfaceAtTheta.minSignedDistanceToPlane(ctx.result) < -COMPARE_EPS)
                    : (cand.surfaceAtTheta.maxSignedDistanceToPlane(ctx.result) > COMPARE_EPS);
            if (violatesPreferredSide) {
                this.clampResolvedToOneSide(cand, ctx);
            }
        }
    }

    private <T> void clampResolvedToOneSide(InteractionCandidate<T> cand, CollisionContext<T> ctx) {
        double centerSigned = cand.surfaceAtTheta.signedDistanceToPlane(cand.playerAtTheta.center());
        Boolean preservePositiveSide;
        if (Math.abs(centerSigned) > COMPARE_EPS) {
            preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
        } else {
            preservePositiveSide = preferredWallClampPositiveSide(ctx.playerTransition.from, ctx.playerTransition.to, cand.surfaceAtTheta);
            if (preservePositiveSide == null) {
                preservePositiveSide = Boolean.TRUE;
            }
        }

        PlayerBoundsState clampedFinal = preservePositiveSide
                ? cand.surfaceAtTheta.clampToPositiveSideOfPlane(ctx.result)
                : cand.surfaceAtTheta.clampToNegativeSideOfPlane(ctx.result);

        double dx = clampedFinal.bounds.getMinX() - ctx.result.bounds.getMinX();
        double dy = clampedFinal.bounds.getMinY() - ctx.result.bounds.getMinY();
        double dz = clampedFinal.bounds.getMinZ() - ctx.result.bounds.getMinZ();
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
        }
    }

    private <T> void processVerticalWallSurfaceAtInterp(InteractionCandidate<T> cand, PlayerBoundsTransition player,
                                                        CollisionContext<T> ctx, List<? extends OBBSurfaceTransition<T>> surfaces) {
        OBBSurfaceState surface = cand.surfaceAtTheta;

        if (!surface.boxTouchesVerticalSurface(cand.playerAtTheta)) {
            return;
        }

        Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, surface);
        if (preservePositiveSide == null) {
            double centerSigned = surface.signedDistanceToPlane(cand.playerAtTheta.center());
            preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
        }
        PlayerBoundsState clampedFinal;
        if (preservePositiveSide) {
            clampedFinal =  cand.st.to.clampToPositiveSideOfPlane(ctx.result);
        } else {
            clampedFinal =  cand.st.to.clampToNegativeSideOfPlane(ctx.result);
        }

        double dx = clampedFinal.bounds.getMinX() - ctx.result.bounds.getMinX();
        double dy = clampedFinal.bounds.getMinY() - ctx.result.bounds.getMinY();
        double dz = clampedFinal.bounds.getMinZ() - ctx.result.bounds.getMinZ();
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
            double planeFromY = fromSurface.planeYAtXZ(fromFace[i].getX(), fromFace[i].getZ());
            double planeToY = toSurface.planeYAtXZ(toFace[i].getX(), toFace[i].getZ());
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
                    double signedFrom = fromSurface.signedDistanceToPlane(fromFace[i]);
                    double signedTo = toSurface.signedDistanceToPlane(toFace[i]);
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

    private <T> void pickBestMultiSurfaceResultIfNeeded(CollisionContext<T> ctx, PlayerBoundsTransition player,
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
        orderedSurfaces.sort(Comparator.comparingDouble(a -> surfaceLevelScore(a.to)));

        for (int iter = 0; iter < 4; iter++) {
            AABBHandle before = ctx.result.bounds;
            for (OBBSurfaceTransition<T> st : orderedSurfaces) {
                OBBSurfaceState surface = st.to;

                // Use the from-state surface to determine which side the player was initially on.
                // Comparing player.from against st.to is misleading when the surface moved: the
                // player's touching face may appear on the wrong side of the moved surface even
                // though the player never crossed it.
                OBBSurfaceState initialSurface = (st.from != null) ? st.from : surface;

                if (isVerticalSurface(surface)) {
                    if (!surface.boxTouchesVerticalSurface(ctx.result)) {
                        continue;
                    }

                    Boolean preservePositiveSide = preferredWallClampPositiveSide(player.from, player.to, initialSurface, surface);
                    if (preservePositiveSide == null) {
                        double centerSigned = initialSurface.signedDistanceToPlane(player.from.center());
                        preservePositiveSide = (centerSigned >= 0.0) ? Boolean.TRUE : Boolean.FALSE;
                    }

                    PlayerBoundsState clamped = preservePositiveSide
                            ? surface.clampToPositiveSideOfPlane(ctx.result)
                            : surface.clampToNegativeSideOfPlane(ctx.result);
                    if (!areAABBsSimilar(clamped.bounds, ctx.result.bounds)) {
                        ctx.result = clamped;
                        ctx.lastSurface = st.source;
                        ctx.lastCollisionMode = CollisionMode.WALL;
                        ctx.lastSurfaceFromState = st.from;
                        ctx.lastSurfaceToState = st.to;
                        ctx.involved.add(st);
                    }
                } else {
                    double startCenterSigned = initialSurface.signedDistanceToPlane(player.from.center());
                    if (!surface.boxTouchesSurfaceFootprint(player.from)
                            && !surface.boxTouchesSurfaceFootprint(player.to)
                            && !surface.boxTouchesSurfaceFootprint(ctx.result)) {
                        continue;
                    }
                    PlayerBoundsState clamped = (startCenterSigned >= 0.0)
                            ? surface.clampToPositiveSideOfPlane(ctx.result)
                            : surface.clampToNegativeSideOfPlane(ctx.result);
                    if (!areAABBsSimilar(clamped.bounds, ctx.result.bounds)) {
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

            if (areAABBsSimilar(before, ctx.result.bounds)) {
                break;
            }
        }
    }

    private <T> void validateResultBounds(CollisionContext<T> ctx, PlayerBoundsTransition player) {
        try {
            double deltaMinY = Math.abs(ctx.result.bounds.getMinY() - player.to.bounds.getMinY());
            double deltaMaxY = Math.abs(ctx.result.bounds.getMaxY() - player.to.bounds.getMaxY());
            boolean finite = Double.isFinite(ctx.result.bounds.getMinY()) && Double.isFinite(ctx.result.bounds.getMaxY());
            if (!finite || deltaMinY > 100.0 || deltaMaxY > 100.0) {
                this.logger.warn("PlayerCollisionSolver produced extreme result bounds; reverting to requested bounds. deltaMinY=" + deltaMinY + " deltaMaxY=" + deltaMaxY + " result=" + ctx.result + " requested=" + player.to.bounds);
                ctx.result = player.to;
                ctx.lastSurface = null;
                ctx.lastCollisionMode = CollisionMode.NONE;
                ctx.lastSurfaceFromState = null;
                ctx.lastSurfaceToState = null;
                ctx.involved = java.util.Collections.emptyList();
            }
        } catch (Throwable ignored) {}
    }

    private PlayerBoundsState clampFeetAbovePlane(OBBSurfaceState surface, PlayerBoundsState state) {
        double y = surface.maxPlaneYAtFace(state);
        if (!Double.isFinite(y)) {
            return state;
        }
        double minY = state.bounds.getMinY();
        logger.debugFeetLog(state.bounds, surface, y, minY);
        if (minY + OBBSurfaceState.CLAMP_EPS >= y) {
            return state;
        }
        double dy = y - minY;
        return state.translate(0.0, dy, 0.0);
    }

    private PlayerBoundsState clampHeadBelowPlane(OBBSurfaceState surface, PlayerBoundsState state) {
        double y = surface.minPlaneYAtFace(state);
        if (!Double.isFinite(y)) {
            return state;
        }
        double maxY = state.bounds.getMaxY();
        logger.debugHeadLog(state.bounds, surface, y, maxY);
        if (maxY - OBBSurfaceState.CLAMP_EPS <= y) {
            return state;
        }
        double dy = y - maxY;
        return state.translate(0.0, dy, 0.0);
    }

    private static boolean isWrongDirectionWallClamp(PlayerBoundsState from, PlayerBoundsState to, OBBSurfaceState surface) {
        Vector movement = to.center().clone().subtract(from.center());
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

    private static Boolean preferredWallClampPositiveSide(PlayerBoundsState from, PlayerBoundsState to, OBBSurfaceState surface) {
        return preferredWallClampPositiveSide(from, to, surface, surface);
    }

    /**
     * Determines which side of the wall the player was on before the collision, taking separate
     * from-state and to-state surface references. This is important when the surface has moved:
     * {@code player.from} should be compared against {@code fromSurface} (the surface state at
     * the beginning of the tick) to avoid mis-detecting the side when the wall shifted toward
     * the player's initial touching face.
     */
    private static Boolean preferredWallClampPositiveSide(PlayerBoundsState from, PlayerBoundsState to,
                                                          OBBSurfaceState fromSurface, OBBSurfaceState toSurface) {
        double fromMinSigned = fromSurface.minSignedDistanceToPlane(from);
        double fromMaxSigned = fromSurface.maxSignedDistanceToPlane(from);
        if (fromMinSigned >= -COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (fromMaxSigned <= COMPARE_EPS) {
            return Boolean.FALSE;
        }

        double toMinSigned = toSurface. minSignedDistanceToPlane(to);
        double toMaxSigned = toSurface.maxSignedDistanceToPlane(to);
        if (toMinSigned >= -COMPARE_EPS) {
            return Boolean.TRUE;
        }
        if (toMaxSigned <= COMPARE_EPS) {
            return Boolean.FALSE;
        }

        double fromCenterSigned = fromSurface.signedDistanceToPlane(from.center());
        double toCenterSigned = toSurface.signedDistanceToPlane(to.center());
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
        double signedFrom = surface.signedDistanceToPlane(from);
        double signedTo = surface.signedDistanceToPlane(to);
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
        double signedFrom = surface.signedDistanceToPlane(from);
        double signedTo = surface.signedDistanceToPlane(to);
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

    /**
     * Tests whether a player AABB moved through the footprint of a surface transition.
     * This checks the surface extents and interpolates the moving surface while testing
     * each player corner path, which makes it suitable for debug logging.
     */
    public static boolean passesThroughSurface(OBBSurfaceTransition<?> surface, PlayerBoundsState from, PlayerBoundsState to) {
        if (surface == null || surface.from == null || surface.to == null || from == null || to == null) {
            return false;
        }

        boolean fromPositive = surface.from.minSignedDistanceToPlane(from) > CROSSING_MARGIN;
        boolean fromNegative = surface.from.maxSignedDistanceToPlane(from) < -CROSSING_MARGIN;
        boolean toPositive = surface.to.minSignedDistanceToPlane(to) > CROSSING_MARGIN;
        boolean toNegative = surface.to.maxSignedDistanceToPlane(to) < -CROSSING_MARGIN;
        if (!((fromPositive && toNegative) || (fromNegative && toPositive))) {
            return false;
        }

        Vector[] fromCorners = from.corners();
        Vector[] toCorners = to.corners();
        for (int i = 0; i < fromCorners.length; i++) {
            double signedFrom = surface.from.signedDistanceToPlane(fromCorners[i]);
            double signedTo = surface.to.signedDistanceToPlane(toCorners[i]);
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

    private static Vector projectOntoPlane(Vector vector, Vector normal) {
        double dot = vector.getX() * normal.getX() + vector.getY() * normal.getY() + vector.getZ() * normal.getZ();
        return new Vector(
                vector.getX() - normal.getX() * dot,
                vector.getY() - normal.getY() * dot,
                vector.getZ() - normal.getZ() * dot);
    }
}
