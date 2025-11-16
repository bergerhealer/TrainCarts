package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.bases.IntCuboid;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Information calculated about an OrientedBoundingBox surface relative
 * to a particular Player location. Has special optimizations for performing
 * a ray-trace onto this surface for quantization purposes.
 */
class OBBSurfaceContext {
    public final Vector position;
    public final Quaternion orientation;
    public final Vector normal;
    public final Vector halfSize;
    public final Vector playerPos;
    public final boolean isWall;
    public final boolean isBackSide;
    public final Vector planeMin, planeMax;
    public final IntCuboid cuboid;

    // Projection configuration
    public BlockFace face;
    public Vector rayDir;
    // Projection outputs
    public final Vector planePos = new Vector();
    public final Vector projectedPos = new Vector();
    public final Vector projectedPosOnPlane = new Vector();

    public OBBSurfaceContext(OrientedBoundingBox surfaceBbox, Vector playerPos) {
        this.position = surfaceBbox.getPosition();
        this.halfSize = surfaceBbox.getSize().clone().multiply(0.5);
        this.orientation = surfaceBbox.getOrientation();
        this.normal = orientation.upVector();
        this.playerPos = playerPos;

        // Get the axis-aligned bounding box min/max block of the surface plane
        planeMin = new Vector(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        planeMax = new Vector(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
        {
            Vector[] points = new Vector[] {
                    new Vector(-halfSize.getX(), 0.0, -halfSize.getZ()),
                    new Vector(halfSize.getX(), 0.0, -halfSize.getZ()),
                    new Vector(-halfSize.getX(), 0.0, halfSize.getZ()),
                    new Vector(halfSize.getX(), 0.0, halfSize.getZ())
            };
            for (Vector p : points) {
                orientation.transformPoint(p);
                p.add(position);

                planeMin.setX(Math.min(planeMin.getX(), p.getX()));
                planeMin.setY(Math.min(planeMin.getY(), p.getY()));
                planeMin.setZ(Math.min(planeMin.getZ(), p.getZ()));
                planeMax.setX(Math.max(planeMax.getX(), p.getX()));
                planeMax.setY(Math.max(planeMax.getY(), p.getY()));
                planeMax.setZ(Math.max(planeMax.getZ(), p.getZ()));
            }
        }
        this.cuboid = IntCuboid.create(IntVector3.blockOf(planeMin), IntVector3.blockOf(planeMax).add(1, 1, 1));

        this.isWall = Math.abs(normal.getY()) < 0.6;
        if (this.isWall) {
            // Simplified because it's not a walkable surface
            this.isBackSide = playerPos.clone().subtract(position).dot(normal) < 0.0;
        } else {
            // Slope or level surface. Keep into account that there will be a min/max Y value
            // the surface exists within. When the player is above/below that, fix isBackSide based
            // on that. This eliminates an issue that far away horizontally surfaces appear as ceilings,
            // which hinders player movement.
            if (playerPos.getY() > (planeMax.getY() - 1.0)) {
                this.isBackSide = normal.getY() < 0.0;
            } else if (playerPos.getY() < planeMin.getY()) {
                this.isBackSide = normal.getY() > 0.0;
            } else {
                this.isBackSide = playerPos.clone().subtract(position).dot(normal) < -0.5;
            }
        }
    }

    /**
     * Configures this context to perform a projection from a particular axis.
     *
     * @param face BlockFace projection axis
     */
    public void initProjector(BlockFace face) {
        this.face = face;
        this.rayDir = FaceUtil.faceToVector(face);
    }

    /**
     * Projects a ray from an origin point onto this oriented bounding box surface.
     * Make sure to call {@link #initProjector(BlockFace)} first to configure the direction
     * of the ray.
     *
     * @param originX Origin X-coordinate (World Coordinates)
     * @param originY Origin Y-coordinate (World Coordinates)
     * @param originZ Origin Z-coordinate (World Coordinates)
     * @return True if the projection was successful and landed on this surface.
     *         If so, the projection output fields are updated. False if it did
     *         not land on the surface.
     */
    public boolean project(double originX, double originY, double originZ) {
        MathUtil.setVector(planePos,
                position.getX() - originX,
                position.getY() - originY,
                position.getZ() - originZ
        );

        double denom = rayDir.dot(normal);
        if (Math.abs(denom) < 1e-6) {
            return false;
        }

        double t = planePos.dot(normal) / denom;
        MathUtil.setVector(projectedPos,
                originX + rayDir.getX() * t,
                originY + rayDir.getY() * t,
                originZ + rayDir.getZ() * t);

        // Project back to plane space and verify this position is within the size of the plane
        {
            Vector projectedPosOnPlane = this.projectedPosOnPlane;
            MathUtil.setVector(projectedPosOnPlane, projectedPos);
            projectedPosOnPlane.subtract(position);
            orientation.invTransformPoint(projectedPosOnPlane);
            return !((Math.abs(projectedPosOnPlane.getX()) - 0.5) > halfSize.getX()) &&
                    !((Math.abs(projectedPosOnPlane.getZ()) - 0.5) > halfSize.getZ());
        }
    }
}
