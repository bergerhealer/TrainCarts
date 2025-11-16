package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.generated.net.minecraft.world.phys.AxisAlignedBBHandle;
import org.bukkit.block.BlockFace;

class SweptAABB {

    public static class CollisionResult {
        public final AxisAlignedBBHandle block;
        public final int blockX, blockY, blockZ;
        public final double theta; // fraction of motion [0..1]
        public final BlockFace face;
        public final AxisAlignedBBHandle movingAtCollision; // interpolated moving AABB at collision

        public CollisionResult(AxisAlignedBBHandle block, int bx, int by, int bz,
                               double theta, BlockFace face,
                               AxisAlignedBBHandle movingAtCollision) {
            this.block = block;
            this.blockX = bx;
            this.blockY = by;
            this.blockZ = bz;
            this.theta = theta;
            this.face = face;
            this.movingAtCollision = movingAtCollision;
        }
    }

    /**
     * Swept AABB test: movingBoxStart moves to movingBoxEnd, check collision with static blockBox.
     */
    private static CollisionResult sweepTest(
            AxisAlignedBBHandle movingBoxStart,
            AxisAlignedBBHandle movingBoxEnd,
            AxisAlignedBBHandle blockBox,
            int bx, int by, int bz) {

        //System.out.println("SWEEP TEST");
        //System.out.println("  START: " + movingBoxStart);
        //System.out.println("  END: " + movingBoxStart);
        //System.out.println("  BLOCK: " + blockBox);

        double vx = movingBoxEnd.getMinX() - movingBoxStart.getMinX();
        double vy = movingBoxEnd.getMinY() - movingBoxStart.getMinY();
        double vz = movingBoxEnd.getMinZ() - movingBoxStart.getMinZ();

        double[] minMoving = { movingBoxStart.getMinX(), movingBoxStart.getMinY(), movingBoxStart.getMinZ() };
        double[] maxMoving = { movingBoxStart.getMaxX(), movingBoxStart.getMaxY(), movingBoxStart.getMaxZ() };

        double[] minBlock = { blockBox.getMinX(), blockBox.getMinY(), blockBox.getMinZ() };
        double[] maxBlock = { blockBox.getMaxX(), blockBox.getMaxY(), blockBox.getMaxZ() };

        double[] v = { vx, vy, vz };
        double[] entry = new double[3];
        double[] exit = new double[3];

        for (int axis = 0; axis < 3; axis++) {
            if (v[axis] > 0.0) {
                entry[axis] = (minBlock[axis] - maxMoving[axis]) / v[axis];
                exit[axis]  = (maxBlock[axis] - minMoving[axis]) / v[axis];
            } else if (v[axis] < 0.0) {
                entry[axis] = (maxBlock[axis] - minMoving[axis]) / v[axis];
                exit[axis]  = (minBlock[axis] - maxMoving[axis]) / v[axis];
            } else {
                entry[axis] = Double.NEGATIVE_INFINITY;
                exit[axis]  = Double.POSITIVE_INFINITY;
            }
        }

        //System.out.println("  ENTRY: " + entry[0] + " / " + entry[1] + " / " + entry[2]);
        //System.out.println("  EXIT : " + exit[0] + " / " + exit[1] + " / " + exit[2]);

        double entryTime = Math.max(Math.max(entry[0], entry[1]), entry[2]);
        double exitTime  = Math.min(Math.min(exit[0], exit[1]), exit[2]);



        if (entryTime > exitTime || entryTime < 0.0 || entryTime > 1.0) {
            return null; // No collision
        }

        BlockFace hitFace;
        if (entryTime == entry[0]) {
            hitFace = (vx > 0) ? BlockFace.WEST : BlockFace.EAST;
        } else if (entryTime == entry[1]) {
            hitFace = (vy > 0) ? BlockFace.DOWN : BlockFace.UP;
        } else {
            hitFace = (vz > 0) ? BlockFace.NORTH : BlockFace.SOUTH;
        }

        // Interpolate moving AABB at collision time
        double minX = movingBoxStart.getMinX() + (movingBoxEnd.getMinX() - movingBoxStart.getMinX()) * entryTime;
        double minY = movingBoxStart.getMinY() + (movingBoxEnd.getMinY() - movingBoxStart.getMinY()) * entryTime;
        double minZ = movingBoxStart.getMinZ() + (movingBoxEnd.getMinZ() - movingBoxStart.getMinZ()) * entryTime;

        double maxX = movingBoxStart.getMaxX() + (movingBoxEnd.getMaxX() - movingBoxStart.getMaxX()) * entryTime;
        double maxY = movingBoxStart.getMaxY() + (movingBoxEnd.getMaxY() - movingBoxStart.getMaxY()) * entryTime;
        double maxZ = movingBoxStart.getMaxZ() + (movingBoxEnd.getMaxZ() - movingBoxStart.getMaxZ()) * entryTime;

        AxisAlignedBBHandle movingAtCollision = AxisAlignedBBHandle.createNew(minX, minY, minZ, maxX, maxY, maxZ);

        return new CollisionResult(blockBox, bx, by, bz, entryTime, hitFace, movingAtCollision);
    }

    /**
     * Traverse the grid along the swept path and find the first collision.
     */
    public static CollisionResult findFirstCollision(
            AxisAlignedBBHandle movingBoxStart,
            AxisAlignedBBHandle movingBoxEnd,
            BlockShapeProvider provider) {

        // Compute bounding region of motion
        int minX = MathUtil.floor(Math.min(movingBoxStart.getMinX(), movingBoxEnd.getMinX()));
        int minY = MathUtil.floor(Math.min(movingBoxStart.getMinY(), movingBoxEnd.getMinY()));
        int minZ = MathUtil.floor(Math.min(movingBoxStart.getMinZ(), movingBoxEnd.getMinZ()));

        int maxX = MathUtil.ceil(Math.max(movingBoxStart.getMaxX(), movingBoxEnd.getMaxX()));
        int maxY = MathUtil.ceil(Math.max(movingBoxStart.getMaxY(), movingBoxEnd.getMaxY()));
        int maxZ = MathUtil.ceil(Math.max(movingBoxStart.getMaxZ(), movingBoxEnd.getMaxZ()));

        CollisionResult best = null;

        // Brute force over candidate blocks in swept volume
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    AxisAlignedBBHandle blockBox = provider.getShape(x, y, z);
                    if (blockBox == null) continue;

                    CollisionResult result = sweepTest(movingBoxStart, movingBoxEnd, blockBox, x, y, z);
                    if (result != null) {
                        if (best == null || result.theta < best.theta) {
                            best = result;
                        }
                    }
                }
            }
        }

        return best;
    }
}
