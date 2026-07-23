package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

/**
 * Snapshot of a flat OrientedBoundingBox (surface). The OBB is assumed to be flat
 * (local Y = 0), so only four corners are needed. Precomputes axes, corners and
 * world AABB and provides fast world&lt;&gt;local helpers and plane projection.
 */
public class OBBSurfaceState {
    public static final double SUPPORT_CONTACT_MARGIN = 1e-6;
    /** Threshold used to treat a surface as near-vertical for stability in various calculations */
    public static final double VERTICAL_NORMAL_EPS = 1e-2;
    /** If algebraic plane Y and projection-based plane Y differ by more than this, prefer projection. */
    public static final double ALGEBRAIC_PROJECTION_DIFF_EPS = 15.0;
    /** If the worst-case algebraic Y deviation across the surface exceeds this, reject algebraic. */
    public static final double ALGEBRAIC_WORST_DELTA_LIMIT = 20.0;
    /** Epsilon for clamping player bounds against surfaces */
    public static final double CLAMP_EPS = 1e-9;
    /** Distance that counts as definitely penetrating the plane */
    public static final double CROSSING_MARGIN = 1e-3;

    public final Vector center;
    public final Quaternion orientation;
    public final Vector halfSize; // half extents (Y typically unused for the plane)

    // Derived axes and normal (unit)
    public final Vector xAxis;
    public final Vector zAxis;
    public final Vector normal;
    /** {@link #normal} but Y is always positive. Up-vector when walking on top of this surface. */
    public final Vector groundNormal;

    // Four world-space corners on the plane (order: -X/-Z, +X/-Z, -X/+Z, +X/+Z)
    public final Vector[] corners = new Vector[4];

    // World AABB of the plane (from corners)
    public final AABBHandle aabb;

    public OBBSurfaceState(OrientedBoundingBox obb) {
        this(obb.getPosition(), obb.getOrientation(), obb.getSize());
    }

    public OBBSurfaceState(Vector center, Quaternion orientation, Vector size) {
        this.center = center;
        this.orientation = orientation;
        this.halfSize = size.clone().multiply(0.5);

        // axes
        this.xAxis = orientation.rightVector();
        this.normal = orientation.upVector();
        this.zAxis = orientation.forwardVector();

        // ground angle is used for player walking maths. Always points upwards to avoid glitches.
        this.groundNormal = this.normal.clone();
        if (this.groundNormal.getY() < 0.0) {
            this.groundNormal.multiply(-1.0);
        }

        // build 4 corners in world space (local Y = 0)
        int i = 0;
        final Vector aabbMin = new Vector(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        final Vector aabbMax = new Vector(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Vector local = new Vector(sx * halfSize.getX(), 0.0, sz * halfSize.getZ());
                // rotate local into world-space and translate to center
                Vector worldCorner = local.clone();
                orientation.transformPoint(worldCorner);
                worldCorner.add(this.center);
                corners[i++] = worldCorner;

                aabbMin.setX(Math.min(aabbMin.getX(), worldCorner.getX()));
                aabbMin.setY(Math.min(aabbMin.getY(), worldCorner.getY()));
                aabbMin.setZ(Math.min(aabbMin.getZ(), worldCorner.getZ()));
                aabbMax.setX(Math.max(aabbMax.getX(), worldCorner.getX()));
                aabbMax.setY(Math.max(aabbMax.getY(), worldCorner.getY()));
                aabbMax.setZ(Math.max(aabbMax.getZ(), worldCorner.getZ()));
            }
        }
        this.aabb = AABBHandle.createNew(
                aabbMin.getX(), aabbMin.getY(), aabbMin.getZ(),
                aabbMax.getX(), aabbMax.getY(), aabbMax.getZ());
    }

    /**
     * Transforms a world-space point into OBB local space:
     * local = invRot * (world - center)
     * Reuses the provided Vector instance as output.
     */
    public Vector worldToLocal(Vector world, Vector reuse) {
        reuse.setX(world.getX() - center.getX());
        reuse.setY(world.getY() - center.getY());
        reuse.setZ(world.getZ() - center.getZ());
        orientation.invTransformPoint(reuse);
        return reuse;
    }

    /**
     * Transforms a local-space point into world-space:
     * world = rot * local + center
     * Reuses the provided Vector instance as output.
     */
    public Vector localToWorld(Vector local, Vector reuse) {
        reuse.setX(local.getX());
        reuse.setY(local.getY());
        reuse.setZ(local.getZ());
        orientation.transformPoint(reuse);
        reuse.add(center);
        return reuse;
    }

    public double signedDistanceToPlane(Vector p) {
        Vector proj = projectPointOntoPlane(p, new Vector());
        return (p.getX() - proj.getX()) * normal.getX()
                + (p.getY() - proj.getY()) * normal.getY()
                + (p.getZ() - proj.getZ()) * normal.getZ();
    }

    public double minSignedDistanceToPlane(PlayerBoundsState state) {
        double min = Double.POSITIVE_INFINITY;
        for (Vector corner : state.corners()) {
            min = Math.min(min, signedDistanceToPlane(corner));
        }
        return min;
    }

    public double maxSignedDistanceToPlane(PlayerBoundsState state) {
        double max = Double.NEGATIVE_INFINITY;
        for (Vector corner : state.corners()) {
            max = Math.max(max, signedDistanceToPlane(corner));
        }
        return max;
    }

    public double planeYAtXZ(double x, double z) {
        double ny = normal.getY();
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
            yAlgebraic = center.getY() - (((x - center.getX()) * normal.getX())
                    + ((z - center.getZ()) * normal.getZ())) / ny;
        }

        // Also compute the projection-based Y so we can compare both results. If they
        // disagree significantly, prefer the projection because it is numerically more
        // stable for near-vertical or skewed planes.
        Vector proj = projectPointOntoPlane(new Vector(x, center.getY(), z), new Vector());
        double yProj = proj.getY();

        // Consider algebraic invalid when the horizontal normal components dominate the
        // vertical component by a large factor: that makes division by ny unstable.
        double nx = normal.getX();
        double nz = normal.getZ();
        boolean algebraicValid = Double.isFinite(yAlgebraic) && Math.abs(yAlgebraic - center.getY()) < 200.0;
        // Reject algebraic if worst-case vertical deviation across the surface
        // (based on half extents and horizontal normal components) is excessively large.
        if (algebraicValid && Math.abs(ny) > 0.0) {
            double worstDelta = (Math.abs(halfSize.getX() * nx) + Math.abs(halfSize.getZ() * nz)) / Math.abs(ny);
            // Determine a dynamic threshold based on surface size: larger surfaces
            // can tolerate larger algebraic deviations. Use max of a base limit and
            // a multiple of the largest half-size dimension.
            double dynamicLimit = Math.max(ALGEBRAIC_WORST_DELTA_LIMIT,
                    5.0 * Math.max(halfSize.getX(), halfSize.getZ()));
            if (worstDelta > dynamicLimit) {
                algebraicValid = false;
            }
        }
        // No separate horizontal/vertical ratio test: prefer projection when
        // algebraic appears extreme relative to center or differs strongly from
        // the projection result. The existing checks below handle selection.
        boolean projValid = Double.isFinite(yProj) && Math.abs(yProj - center.getY()) < 200.0;

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
        return center.getY();
    }

    private java.util.List<Double> gatherFacePlaneYs(Vector[] faceCorners) {
        java.util.ArrayList<Double> list = new java.util.ArrayList<>();
        for (Vector corner :  faceCorners) {
            Vector proj = projectPointOntoPlane(corner, new Vector());
            if (containsPointOnPlane(proj)) {
                list.add(planeYAtXZ(corner.getX(), corner.getZ()));
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

    public double maxPlaneYAtFace(PlayerBoundsState state) {
        java.util.List<Double> list = gatherFacePlaneYs(state.bottomFaceCorners());
        return representativePlaneY(list, true);
    }

    public double minPlaneYAtFace(PlayerBoundsState state) {
        java.util.List<Double> list = gatherFacePlaneYs(state.topFaceCorners());
        return representativePlaneY(list, false);
    }

    public PlayerBoundsState clampToPositiveSideOfPlane(PlayerBoundsState state) {
        double minSigned = minSignedDistanceToPlane(state);
        if (minSigned + CLAMP_EPS >= 0.0) {
            return state;
        }
        double distance = -minSigned;
        return state.translate(
                normal.getX() * distance,
                normal.getY() * distance,
                normal.getZ() * distance);
    }

    public PlayerBoundsState clampToNegativeSideOfPlane(PlayerBoundsState state) {
        double maxSigned = maxSignedDistanceToPlane(state);
        if (maxSigned - CLAMP_EPS <= 0.0) {
            return state;
        }
        double distance = -maxSigned;
        return state.translate(
                normal.getX() * distance,
                normal.getY() * distance,
                normal.getZ() * distance);
    }

    public boolean boxTouchesVerticalSurface(PlayerBoundsState state) {
        boolean overlapsSurface = false;
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (Vector corner : state.corners()) {
            double signed = signedDistanceToPlane(corner);
            if (signed >= -CROSSING_MARGIN) hasPositive = true;
            if (signed <= CROSSING_MARGIN) hasNegative = true;
            Vector proj = projectPointOntoPlane(corner, new Vector());
            if (containsPointOnPlane(proj)) {
                overlapsSurface = true;
                if (Math.abs(signed) <= CROSSING_MARGIN) {
                    return true;
                }
            }
        }
        return overlapsSurface && hasPositive && hasNegative;
    }

    public boolean boxTouchesSurfaceFootprint(PlayerBoundsState state) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector corner : state.corners()) {
            Vector local = worldToLocal(corner, new Vector());
            minX = Math.min(minX, local.getX());
            maxX = Math.max(maxX, local.getX());
            minZ = Math.min(minZ, local.getZ());
            maxZ = Math.max(maxZ, local.getZ());
        }
        return maxX >= -halfSize.getX() - 1e-6
                && minX <= halfSize.getX() + 1e-6
                && maxZ >= -halfSize.getZ() - 1e-6
                && minZ <= halfSize.getZ() + 1e-6;
    }

    /**
     * Projects a world-space point onto the OBB plane (local Y = 0).
     * The returned Vector (reuse) is the world-space point on the plane.
     */
    public Vector projectPointOntoPlane(Vector worldPoint, Vector reuse) {
        // world -> local, zero Y, local -> world
        Vector local = worldToLocal(worldPoint, reuse);
        local.setY(0.0);
        // transform back into world; we can reuse a temporary vector for the transform
        Vector out = new Vector(local.getX(), local.getY(), local.getZ());
        orientation.transformPoint(out);
        out.add(center);
        // copy result back into reuse and return
        reuse.setX(out.getX());
        reuse.setY(out.getY());
        reuse.setZ(out.getZ());
        return reuse;
    }

    /**
     * Translates the given AABB vertically (world Y) so that its bottom face sits on top
     * of this surface. For each of the four bottom corners of the AABB the Y value on the
     * surface plane at that corner's (X, Z) position is computed, and the AABB is shifted
     * upward by the largest required delta so every bottom corner is at or above the surface.<br>
     * <br>
     * For corners whose (X, Z) projects outside the surface's finite bounds (i.e. they hang
     * off the edge), the surface's world-AABB maximum Y is used as the surface height for that
     * corner. This prevents the AABB from being lifted excessively when it partially overhangs
     * a sloped surface edge.<br>
     * <br>
     * If the surface is nearly vertical ({@code |normal.Y| < 1e-6}) or the AABB is already
     * sitting on or above the surface, the original bounds are returned unchanged.
     *
     * @param state Player state to place on the surface
     * @return New player state translated vertically to sit on this surface, or the original if no
     *         upward shift is needed / applicable
     */
    public PlayerBoundsState placeBoundsOnSurface(PlayerBoundsState state) {
        double ny = this.normal.getY();
        if (Math.abs(ny) < 1e-6) {
            // Nearly vertical surface - a pure Y translation cannot place the AABB onto it
            return state;
        }

        double maxShift = Double.NEGATIVE_INFINITY;
        double surfaceTopY = this.aabb.getMaxY();
        double minY = state.bounds.getMinY();

        double[] xs = { state.bounds.getMinX(), state.bounds.getMaxX() };
        double[] zs = { state.bounds.getMinZ(), state.bounds.getMaxZ() };

        for (double x : xs) {
            for (double z : zs) {
                // Solve for Y such that (x, Y, z) lies exactly on the (infinite) surface plane:
                //   normal · ((x,Y,z) - center) = 0
                //   => Y = center.Y - (normal.X*(x-center.X) + normal.Z*(z-center.Z)) / normal.Y
                double surfaceY = center.getY()
                        - (normal.getX() * (x - center.getX())
                        +  normal.getZ() * (z - center.getZ())) / ny;

                // For corners within the surface bounds surfaceY is the exact answer.
                // For corners overhanging the high edge the plane extends above surfaceTopY,
                // which would launch the player upward – clamp it.
                // For corners overhanging the low or side edges the plane extends at or below
                // surfaceTopY, so the natural value is correct and no spurious lift occurs.
                double requiredShift = Math.min(surfaceY, surfaceTopY) - minY;
                if (requiredShift > maxShift) {
                    maxShift = requiredShift;
                }
            }
        }

        if (maxShift <= 0.0) {
            // AABB is already at or above the surface - no adjustment needed.
            return state;
        }

        return state.translate(0.0, maxShift, 0.0);
    }

   public Vector projectOntoNormalPlane(Vector vector) {
        Vector result = vector.clone();
        double dot = result.dot(normal);
        result.subtract(normal.clone().multiply(dot));
        return result;
    }

    public Vector projectOntoGroundPlane(Vector vector) {
        Vector result = vector.clone();
        double dot = result.dot(groundNormal);
        result.subtract(groundNormal.clone().multiply(dot));
        return result;
    }

    public boolean bottomFaceIntersectsSurfacePlane(AABBHandle aabb) {
        Vector[] bottomCorners = bottomFaceCorners(aabb);
        double minSigned = Double.POSITIVE_INFINITY;
        double maxSigned = Double.NEGATIVE_INFINITY;
        double minLocalX = Double.POSITIVE_INFINITY;
        double maxLocalX = Double.NEGATIVE_INFINITY;
        double minLocalZ = Double.POSITIVE_INFINITY;
        double maxLocalZ = Double.NEGATIVE_INFINITY;
        for (Vector corner : bottomCorners) {
            double signed = signedDistanceToPlane(corner);
            minSigned = Math.min(minSigned, signed);
            maxSigned = Math.max(maxSigned, signed);

            Vector local = worldToLocal(corner, new Vector());
            minLocalX = Math.min(minLocalX, local.getX());
            maxLocalX = Math.max(maxLocalX, local.getX());
            minLocalZ = Math.min(minLocalZ, local.getZ());
            maxLocalZ = Math.max(maxLocalZ, local.getZ());
        }

        if (minSigned > SUPPORT_CONTACT_MARGIN || maxSigned < -SUPPORT_CONTACT_MARGIN) {
            return false;
        }

        return maxLocalX >= (-halfSize.getX() - SUPPORT_CONTACT_MARGIN)
                && minLocalX <= (halfSize.getX() + SUPPORT_CONTACT_MARGIN)
                && maxLocalZ >= (-halfSize.getZ() - SUPPORT_CONTACT_MARGIN)
                && minLocalZ <= (halfSize.getZ() + SUPPORT_CONTACT_MARGIN);
    }

    private static Vector[] bottomFaceCorners(AABBHandle aabb) {
        double y = aabb.getMinY();
        return new Vector[] {
                new Vector(aabb.getMinX(), y, aabb.getMinZ()),
                new Vector(aabb.getMinX(), y, aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMinZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMaxZ())
        };
    }

    /**
     * Gets whether the player state is partially clipping through this surface. This is the case
     * when not all 8 corner points of the player AABB are on the same side of this surface (or on),
     * but only considering corners whose projection onto the surface's plane actually falls within
     * the surface's finite bounds (its local X/Z half-size extents). Corners that project outside
     * the surface footprint are ignored, since the (infinite) plane they're being compared against
     * doesn't actually exist there.
     *
     * @param playerState PlayerBoundsState
     * @return True if the player is clipping through this surface (straddling both sides of its plane
     *         within its bounds)
     */
    public boolean isClippingThrough(PlayerBoundsState playerState) {
        // Small tolerance to avoid flagging corners that are merely touching the plane
        // (within floating point rounding error) as a "clip" on the wrong side.
        final double EPS = 1e-6;
        boolean hasPositive = false;
        boolean hasNegative = false;
        Vector reuse = new Vector();
        for (Vector corner : playerState.corners()) {
            Vector projected = projectPointOntoPlane(corner, reuse);
            if (!containsPointOnPlane(projected)) {
                continue;
            }
            double signed = signedDistanceToPlane(corner);
            if (signed > EPS) {
                hasPositive = true;
            } else if (signed < -EPS) {
                hasNegative = true;
            }
            if (hasPositive && hasNegative) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes whether a world-space point is within the plane bounds (inclusive).
     * Uses local-space projection (ignores local Y).
     */
    public boolean containsPointOnPlane(Vector worldPoint) {
        Vector tmp = worldToLocal(worldPoint, new Vector());
        // Use a slightly larger tolerance to account for floating point projection
        // errors near the surface edges. 1e-6 is large enough to cover tiny
        // inaccuracies but small enough to not affect correctness for real cases.
        return Math.abs(tmp.getX()) <= halfSize.getX() + 1e-6 && Math.abs(tmp.getZ()) <= halfSize.getZ() + 1e-6;
    }

    /**
     * Prints the construction of a new OBBSurfaceState using the values of this current state.
     * This is for debugging and creating unit tests for failure cases.
     *
     * @param str StringBuilder to write to
     * @param indentStr indent string to prepend to each line (for better formatting in tests)
     */
    public void printDebugCreate(StringBuilder str, String indentStr) {
        str.append(indentStr).append("new OBBSurfaceState(\n");
        str.append(indentStr).append("    new Vector(").append(center.getX()).append(", ").append(center.getY()).append(", ").append(center.getZ()).append("),\n");
        str.append(indentStr).append("    new Quaternion(")
                .append(orientation.getX()).append(", ")
                .append(orientation.getY()).append(", ")
                .append(orientation.getZ()).append(", ")
                .append(orientation.getW()).append("),\n");
        str.append(indentStr).append("    new Vector(")
                .append(2.0 * halfSize.getX()).append(", ")
                .append(2.0 * halfSize.getY()).append(", ")
                .append(2.0 * halfSize.getZ()).append(")\n");
        str.append(indentStr).append(")");
    }
}
