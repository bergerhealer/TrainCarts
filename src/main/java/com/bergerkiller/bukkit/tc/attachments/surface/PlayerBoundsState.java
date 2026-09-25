package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

/**
 * Snapshot of a player bounding box. Keeps the bounds and commonly used corner
 * points together so collision code can reason about a player state directly.
 */
public class PlayerBoundsState {
    public final AABBHandle bounds;
    private final Vector[] corners;
    private final Vector center;

    public PlayerBoundsState(AABBHandle bounds) {
        this.bounds = bounds;
        this.corners = corners(bounds);
        this.center = center(bounds);
    }

    public PlayerBoundsState interpolate(PlayerBoundsState other, double theta) {
        double minX = this.bounds.getMinX() + (other.bounds.getMinX() - this.bounds.getMinX()) * theta;
        double minY = this.bounds.getMinY() + (other.bounds.getMinY() - this.bounds.getMinY()) * theta;
        double minZ = this.bounds.getMinZ() + (other.bounds.getMinZ() - this.bounds.getMinZ()) * theta;
        double maxX = this.bounds.getMaxX() + (other.bounds.getMaxX() - this.bounds.getMaxX()) * theta;
        double maxY = this.bounds.getMaxY() + (other.bounds.getMaxY() - this.bounds.getMaxY()) * theta;
        double maxZ = this.bounds.getMaxZ() + (other.bounds.getMaxZ() - this.bounds.getMaxZ()) * theta;
        return new PlayerBoundsState(AABBHandle.createNew(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public PlayerBoundsState translate(double dx, double dy, double dz) {
        return new PlayerBoundsState(bounds.translate(dx, dy, dz));
    }

    public Vector center() {
        return center;
    }

    public Vector[] bottomFaceCorners() {
        return new Vector[] { corners[0], corners[1], corners[2], corners[3] };
    }

    public Vector[] topFaceCorners() {
        return new Vector[] { corners[4], corners[5], corners[6], corners[7] };
    }

    public Vector[] corners() {
        return corners;
    }

    public void printDebugCreate(StringBuilder str, String indentStr) {
        str.append("new PlayerBoundsState(\n");
        str.append(indentStr).append("    AABBHandle.createNew(\n");
        str.append(indentStr).append("        ").append(bounds.getMinX()).append(", ").append(bounds.getMinY()).append(", ").append(bounds.getMinZ()).append(",\n");
        str.append(indentStr).append("        ").append(bounds.getMaxX()).append(", ").append(bounds.getMaxY()).append(", ").append(bounds.getMaxZ()).append("\n");
        str.append(indentStr).append("    )\n");
        str.append(indentStr).append(")");
    }

    public Vector feetPosition() {
        return feetPosition(bounds);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof PlayerBoundsState) {
            return areAABBsEqual(((PlayerBoundsState) o).bounds, this.bounds);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "PlayerBounds{minX=" + bounds.getMinX() + ", minY=" + bounds.getMinY() + ", minZ=" + bounds.getMinZ() +
                ", maxX=" + bounds.getMaxX() + ", maxY=" + bounds.getMaxY() + ", maxZ=" + bounds.getMaxZ() + "}";
    }

    public static Vector feetPosition(AABBHandle aabb) {
        return new Vector(
                (aabb.getMinX() + aabb.getMaxX()) * 0.5,
                aabb.getMinY(),
                (aabb.getMinZ() + aabb.getMaxZ()) * 0.5
        );
    }

    public static Vector[] corners(AABBHandle aabb) {
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

    public static Vector center(AABBHandle aabb) {
        return new Vector(
                0.5 * (aabb.getMinX() + aabb.getMaxX()),
                0.5 * (aabb.getMinY() + aabb.getMaxY()),
                0.5 * (aabb.getMinZ() + aabb.getMaxZ())
        );
    }

    public static boolean areAABBsEqual(AABBHandle a, AABBHandle b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getMinX() == b.getMinX() &&
                a.getMinY() == b.getMinY() &&
                a.getMinZ() == b.getMinZ() &&
                a.getMaxX() == b.getMaxX() &&
                a.getMaxY() == b.getMaxY() &&
                a.getMaxZ() == b.getMaxZ();
    }
}
