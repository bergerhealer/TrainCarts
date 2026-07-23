package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

/**
 * Snapshot of a player bounding box. Keeps the bounds and commonly used corner
 * points together so collision code can reason about a player state directly.
 */
public class PlayerBoundsState {
    public final AABBHandle bounds;
    public final Vector[] corners = new Vector[8];

    public PlayerBoundsState(AABBHandle bounds) {
        this.bounds = bounds;

        int i = 0;
        for (int y = 0; y <= 1; y++) {
            double py = (y == 0) ? bounds.getMinY() : bounds.getMaxY();
            for (int z = 0; z <= 1; z++) {
                double pz = (z == 0) ? bounds.getMinZ() : bounds.getMaxZ();
                for (int x = 0; x <= 1; x++) {
                    double px = (x == 0) ? bounds.getMinX() : bounds.getMaxX();
                    corners[i++] = new Vector(px, py, pz);
                }
            }
        }
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

    public Vector[] bottomFaceCorners() {
        return new Vector[] { corners[0], corners[1], corners[2], corners[3] };
    }

    public Vector[] topFaceCorners() {
        return new Vector[] { corners[4], corners[5], corners[6], corners[7] };
    }

    public Vector[] allCorners() {
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

    public static Vector feetPosition(AABBHandle aabb) {
        return new Vector(
                (aabb.getMinX() + aabb.getMaxX()) * 0.5,
                aabb.getMinY(),
                (aabb.getMinZ() + aabb.getMaxZ()) * 0.5
        );
    }
}
