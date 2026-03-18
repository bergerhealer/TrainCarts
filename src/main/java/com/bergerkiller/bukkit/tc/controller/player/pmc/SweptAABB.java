package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
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

        @Override
        public String toString() {
            return "CollisionResult{block=[" + blockX + "," + blockY + "," + blockZ + "], block_bbox=" + block +", theta=" + theta + ", face=" + face + "}";
        }
    }

    private static class BestCollisionResult {
        private CollisionResult best = null;

        public CollisionResult get() {
            return best;
        }

        public void promote(CollisionResult result) {
            if (result != null && (best == null || result.theta < best.theta)) {
                best = result;
            }
        }
    }

    /**
     * Swept AABB test: movingBoxStart moves to movingBoxEnd, check collision with static blockBox.
     *
     * @param movingBoxStart Starting AABB of the moving object
     * @param movingBoxEnd Ending AABB of the moving object
     * @param blockBox AABB of the static block
     * @param bx Block X coordinate
     * @param by Block Y coordinate
     * @param bz Block Z coordinate
     * @return CollisionResult if a collision occurs, or null if no collision
     */
    public static CollisionResult sweepTest(
            AxisAlignedBBHandle movingBoxStart,
            AxisAlignedBBHandle movingBoxEnd,
            AxisAlignedBBHandle blockBox,
            int bx, int by, int bz
    ) {

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
     *
     * @param movingBoxStart Starting AABB of the moving object
     * @param movingBoxEnd Ending AABB of the moving object
     * @param provider Block shape provider to get block AABBs
     * @param viewer Attachment viewer to check for spawned stationary collision surfaces
     * @return CollisionResult of the first collision, or null if no collision
     */
    public static CollisionResult findFirstBlockCollision(
            AxisAlignedBBHandle movingBoxStart,
            AxisAlignedBBHandle movingBoxEnd,
            BlockShapeProvider provider,
            AttachmentViewer viewer
    ) {

        // Compute bounding region of motion
        int minX = MathUtil.floor(Math.min(movingBoxStart.getMinX(), movingBoxEnd.getMinX()));
        int minY = MathUtil.floor(Math.min(movingBoxStart.getMinY(), movingBoxEnd.getMinY()));
        int minZ = MathUtil.floor(Math.min(movingBoxStart.getMinZ(), movingBoxEnd.getMinZ()));

        int maxX = MathUtil.ceil(Math.max(movingBoxStart.getMaxX(), movingBoxEnd.getMaxX()));
        int maxY = MathUtil.ceil(Math.max(movingBoxStart.getMaxY(), movingBoxEnd.getMaxY()));
        int maxZ = MathUtil.ceil(Math.max(movingBoxStart.getMaxZ(), movingBoxEnd.getMaxZ()));

        final BestCollisionResult best = new BestCollisionResult();

        // Brute force over candidate blocks in swept volume
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    AxisAlignedBBHandle blockBox = provider.getShape(x, y, z);
                    if (blockBox == null) continue;

                    best.promote(sweepTest(movingBoxStart, movingBoxEnd, blockBox, x, y, z));
                }
            }
        }

        // Check collision surfaces visible to the viewer
        viewer.forAllStationaryCollisionElements(
                minX, minY, minZ, maxX, maxY, maxZ,
                (element) -> {
                    AxisAlignedBBHandle blockBox = element.getBoundingBox();
                    best.promote(sweepTest(movingBoxStart, movingBoxEnd, blockBox, 0, 0, 0));
                }
        );

        return best.get();
    }
}
