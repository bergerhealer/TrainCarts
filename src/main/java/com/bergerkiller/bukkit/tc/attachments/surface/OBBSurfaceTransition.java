package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

/**
 * Transition between two surface states. This is a moving surface, and the player must not pass through
 * this surface during the transition.
 */
public class OBBSurfaceTransition<T> {
    public OBBSurfaceState from;
    public OBBSurfaceState to;
    public T source;
    /**
     * When {@code true}, this surface is the floor the simulated player is currently standing on.
     * The collision solver will skip the interpolated crossing-detection sweep for this surface
     * (preventing false FEET collisions when the surface rotates mid-tick) but will still apply
     * a final clamp at the to-state to prevent actual clipping through the floor.
     */
    public boolean isFloor = false;

    public OBBSurfaceTransition(OrientedBoundingBox from, OrientedBoundingBox to) {
        this(from, to, null);
    }

    public OBBSurfaceTransition(OrientedBoundingBox from, OrientedBoundingBox to, T source) {
        this.from = new OBBSurfaceState(from);
        this.to = new OBBSurfaceState(to);
        this.source = source;
    }

    public OBBSurfaceTransition(OBBSurfaceState from, OBBSurfaceState to) {
        this(from, to, null);
    }

    public OBBSurfaceTransition(OBBSurfaceState from, OBBSurfaceState to, T source) {
        this.from = from;
        this.to = to;
        this.source = source;
    }

    /**
     * Epsilon used by {@link #hasCornerPassedThrough} to decide whether a corner's
     * signed distance is close enough to the plane that the opposite corner should
     * be consulted. Values this small are indistinguishable from "touching the
     * surface" due to floating-point rounding.
     */
    private static final double NEAR_SURFACE_EPS = 1e-6;

    /**
     * Returns true if any of the 8 AABB corners moved from one side of this surface
     * to the other during the given player transition (i.e. the player clipped through
     * the surface).
     *
     * <p>When a corner's signed distance at the <em>old</em> or <em>new</em> position
     * is within {@link #NEAR_SURFACE_EPS} of zero the distance is considered unreliable
     * (floating-point noise right at the surface boundary). In that case the
     * <em>diagonally opposite</em> corner of the AABB – which lies deep inside the
     * player body, far from the surface – is used as a proxy to decide which side
     * the player body is actually on. This prevents false positives when the solver
     * correctly places a corner exactly on the surface (signedDist ≈ 0).</p>
     */
    public boolean hasCornerPassedThrough(PlayerBoundsTransition transition) {
        for (int i = 0; i < 8; i++) {
            Vector fromPos = transition.from.corners[i];
            Vector toPos   = transition.to.corners[i];

            double signedFrom = from.signedDistanceToPlane(fromPos);
            double signedTo   = to  .signedDistanceToPlane(toPos);

            // Corner indices are laid out as y-outer, z-middle, x-inner
            // (see PlayerBoundsState constructor), so index j = 7 - i is always
            // the diagonally opposite corner.
            int j = 7 - i;

            // If the old position is essentially on the surface, use the
            // opposite corner's old position as the reference side.
            if (Math.abs(signedFrom) < NEAR_SURFACE_EPS) {
                signedFrom = from.signedDistanceToPlane(transition.from.corners[j]);
            }

            // If the new position is essentially on the surface, use the
            // opposite corner's new position to decide if the player body
            // is on the positive side (just touching) or the negative side
            // (genuinely passed through).
            if (Math.abs(signedTo) < NEAR_SURFACE_EPS) {
                double signedToOpposite = to.signedDistanceToPlane(transition.to.corners[j]);
                if (signedToOpposite >= -NEAR_SURFACE_EPS) {
                    // Opposite corner is on the correct (positive) side – the
                    // player is merely touching the surface, not passing through.
                    continue;
                }
                // Opposite corner is also on the wrong side; use it for the
                // crossing computation so theta/bounds-check are meaningful.
                signedTo = signedToOpposite;
            }

            Double theta = crossingTheta(signedFrom, signedTo);
            if (theta == null) {
                continue;
            }

            Vector crossingPoint = new Vector(
                    fromPos.getX() + (toPos.getX() - fromPos.getX()) * theta,
                    fromPos.getY() + (toPos.getY() - fromPos.getY()) * theta,
                    fromPos.getZ() + (toPos.getZ() - fromPos.getZ()) * theta);
            OBBSurfaceState surfaceAtTheta = interpolate(theta);
            Vector projected = surfaceAtTheta.projectPointOntoPlane(crossingPoint, new Vector());
            if (surfaceAtTheta.containsPointOnPlane(projected)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPassedThrough(Vector oldPosition, Vector newPosition) {
        if (oldPosition == null || newPosition == null) {
            return false;
        }

        double signedFrom = from.signedDistanceToPlane(oldPosition);
        double signedTo = to.signedDistanceToPlane(newPosition);

        Double theta = crossingTheta(signedFrom, signedTo);
        if (theta == null) {
            return false;
        }

        Vector crossingPoint = new Vector(
                oldPosition.getX() + (newPosition.getX() - oldPosition.getX()) * theta,
                oldPosition.getY() + (newPosition.getY() - oldPosition.getY()) * theta,
                oldPosition.getZ() + (newPosition.getZ() - oldPosition.getZ()) * theta);
        OBBSurfaceState surfaceAtTheta = interpolate(theta);
        Vector projected = surfaceAtTheta.projectPointOntoPlane(crossingPoint, new Vector());
        return surfaceAtTheta.containsPointOnPlane(projected);
    }

    public boolean hasSurfaceSupport(AABBHandle actualBounds, Vector worldPosition, double gravity, PlayerCollisionSolver collisionSolver) {
        Vector actualFeetPosition = PlayerBoundsState.feetPosition(actualBounds);
        AABBHandle bboxAtPosition = actualBounds.translate(
                worldPosition.getX() - actualFeetPosition.getX(),
                worldPosition.getY() - actualFeetPosition.getY(),
                worldPosition.getZ() - actualFeetPosition.getZ());
        if (to.bottomFaceIntersectsSurfacePlane(bboxAtPosition)) {
            return true;
        }
        AABBHandle bboxBelow = bboxAtPosition.translate(0.0, -gravity, 0.0);
        PlayerCollisionSolver.Result<T> result = collisionSolver.solveDetailed(
                Collections.singletonList(this),
                new PlayerBoundsTransition(bboxAtPosition, bboxBelow)
        );
        return result.lastCollisionMode == PlayerCollisionSolver.CollisionMode.FEET;
    }

    private static Double crossingTheta(double signedFrom, double signedTo) {
        double denom = signedFrom - signedTo;
        if (Math.abs(denom) < 1e-10) {
            return null;
        }

        double theta = signedFrom / denom;
        if (theta < 0.0 || theta > 1.0) {
            return null;
        }
        return theta;
    }

    public OBBSurfaceState interpolate(double theta) {
        double t = Math.max(0.0, Math.min(1.0, theta));
        Vector center = new Vector(
                from.center.getX() + (to.center.getX() - from.center.getX()) * t,
                from.center.getY() + (to.center.getY() - from.center.getY()) * t,
                from.center.getZ() + (to.center.getZ() - from.center.getZ()) * t);
        Quaternion orientation = Quaternion.slerp(from.orientation, to.orientation, t);
        Vector size = new Vector(
                (from.halfSize.getX() + (to.halfSize.getX() - from.halfSize.getX()) * t) * 2.0,
                (from.halfSize.getY() + (to.halfSize.getY() - from.halfSize.getY()) * t) * 2.0,
                (from.halfSize.getZ() + (to.halfSize.getZ() - from.halfSize.getZ()) * t) * 2.0);
        return new OBBSurfaceState(center, orientation, size);
    }

    /**
     * Transforms the movement (velocity) vector from the "from" state to the "to" state.
     *
     * @param movement Movement vector
     * @return Transformed movement vector relative to "to" state
     */
    public Vector transformMovement(Vector movement) {
        if (movement.lengthSquared() < 1e-20) {
            return new Vector();
        }

        double localX = movement.dot(from.xAxis);
        double localY = movement.dot(from.normal);
        double localZ = movement.dot(from.zAxis);
        return to.xAxis.clone().multiply(localX)
                .add(to.normal.clone().multiply(localY))
                .add(to.zAxis.clone().multiply(localZ));
    }

    /**
     * Prints the construction of a new OBBSurfaceTransition using the values of this current transition.
     * This is for debugging and creating unit tests for failure cases.
     *
     * @param str StringBuilder to write to
     * @param indentStr indent string to prepend to each line (for better formatting in tests)
     */
    public void printDebugCreate(StringBuilder str, String indentStr) {
        str.append("new OBBSurfaceTransition<>(\n");
        from.printDebugCreate(str, indentStr + "    ");
        str.append(",\n");
        to.printDebugCreate(str, indentStr + "    ");
        str.append("\n").append(indentStr).append(")");
    }

    public static void printMultiDebugCreate(List<? extends OBBSurfaceTransition<?>> transitions, StringBuilder sb, String indentStr) {
        sb.append("Arrays.asList(\n");
        boolean first = true;
        for (OBBSurfaceTransition<?> t : transitions) {
            if (first) {
                first = false;
            } else {
                sb.append(",\n");
            }
            sb.append(indentStr).append("    ");
            t.printDebugCreate(sb, indentStr + "    ");
        }
        sb.append("\n").append(indentStr).append(");\n");
    }
}
