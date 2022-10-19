package com.bergerkiller.bukkit.tc.signactions.mutex;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexZone {
    public final OfflineBlock signBlock;
    public final IntVector3 start;
    public final IntVector3 end;
    public final String statement;
    public final MutexZoneSlot slot;
    public final MutexZoneSlotType type;
    private final BoundingBox bb;
    private Boolean leversDown = null; // Avoids excessive block access

    private MutexZone(OfflineBlock signBlock, IntVector3 start, IntVector3 end, MutexZoneSlotType type, String name, String statement) {
        this.signBlock = signBlock;
        this.statement = statement;
        this.start = start;
        this.end = end;
        this.bb = new BoundingBox(new Vector(start.x, start.y, start.z), new Vector(end.x + 1.0, end.y + 1.0, end.z + 1.0));
        this.slot = MutexZoneCache.findSlot(name, this);
        this.type = type;
    }

    public boolean containsBlock(IntVector3 block) {
        return block.x >= start.x && block.y >= start.y && block.z >= start.z &&
               block.x <= end.x && block.y <= end.y && block.z <= end.z;
    }

    public boolean containsBlock(Block block) {
        return block.getX() >= start.x && block.getY() >= start.y && block.getZ() >= start.z &&
               block.getX() <= end.x && block.getY() <= end.y && block.getZ() <= end.z;
    }

    public boolean isNearby(IntVector3 block, int radius) {
        return block.x>=(start.x-radius) && block.y>=(start.y-radius) && block.z>=(start.z-radius) &&
               block.x<=(end.x + radius) && block.y<=(end.y + radius) && block.z<=(end.z + radius);
    }

    public Block getSignBlock() {
        return this.signBlock.getLoadedBlock();
    }

    public static IntVector3 getPosition(SignActionEvent info) {
        Location middlePos = info.getCenterLocation();
        if (middlePos != null) {
            return new IntVector3(middlePos);
        } else {
            return new IntVector3(info.getBlock());
        }
    }

    protected void setLevers(boolean down) {
        // Avoid spamming block data calls lots of times per tick
        // This function is just called a lot...
        {
            Boolean bState = Boolean.valueOf(down);
            if (leversDown != bState) {
                leversDown = bState;
            } else {
                return;
            }
        }

        Block signBlock = getSignBlock();
        if (signBlock != null) {
            signBlock.getChunk();
            BlockData data = WorldUtil.getBlockData(signBlock);
            if (MaterialUtil.ISSIGN.get(data)) {
                BlockUtil.setLeversAroundBlock(signBlock.getRelative(data.getAttachedFace()), down);
            }
        }
    }

    public static MutexZone create(OfflineWorld world, IntVector3 signPosition, MutexSignMetadata metadata) {
        return new MutexZone(world.getBlockAt(signPosition), metadata.start, metadata.end, metadata.type, metadata.name, metadata.statement);
    }

    /**
     * Performs a hit collision test from a starting position moving into the direction motion vector
     * specified. Taken from OrientedBoundingBox in BKCommonLib, without the rotating part.
     *
     * @param posX
     * @param posY
     * @param posZ
     * @param motX
     * @param motY
     * @param motZ
     * @return Distance to this mutex zone's bounding box. or {@link Double#MAX_VALUE} if not hit
     */
    public double hitTest(double posX, double posY, double posZ,
                          double motX, double motY, double motZ
    ) {
        return bb.hitTest(posX, posY, posZ, motX, motY, motZ);
    }

    /**
     * Can be replaced with BKCL OrientedBoundingBox and its naturalFromTo method once that
     * can be safely used as a hard-dep.
     *
     * TODO: Replace with OrientedBoundingBox when BKCommonLib 1.19.2-v3 or later.
     */
    private static class BoundingBox {
        public final Vector middle;
        public final Vector radius;

        public BoundingBox(Vector p1, Vector p2) {
            this.middle = p1.clone().add(p2).multiply(0.5);
            this.radius = p2.clone().subtract(p1).multiply(0.5);
        }

        /**
         * Performs a hit collision test from a starting position moving into the direction motion vector
         * specified. Taken from OrientedBoundingBox in BKCommonLib, without the rotating part.
         *
         * @param posX
         * @param posY
         * @param posZ
         * @param motX
         * @param motY
         * @param motZ
         * @return Distance to this mutex zone's bounding box. or {@link Double#MAX_VALUE} if not hit
         */
        public double hitTest(double posX, double posY, double posZ,
                              double motX, double motY, double motZ
        ) {
            // Compute start point
            Vector mid = this.middle;
            double localPosX = posX - mid.getX();
            double localPosY = posY - mid.getY();
            double localPosZ = posZ - mid.getZ();

            // Check start point already inside box
            Vector rad = this.radius;
            if (Math.abs(localPosX) <= rad.getX() && Math.abs(localPosY) <= rad.getY() && Math.abs(localPosZ) <= rad.getZ()) {
                return 0.0;
            }

            // Check all 6 faces and find the intersection point with this axis
            // Then check whether these points are within the range of the box
            // If true, compute the distance from the start point and track the smallest value
            final double ERR = 1e-6;
            double min_distance = Double.MAX_VALUE;
            for (BlockFace dir : FaceUtil.BLOCK_SIDES) {
                double a, b, c;
                if (dir.getModX() != 0) {
                    // x
                    a = rad.getX() * dir.getModX();
                    b = localPosX;
                    c = motX;
                } else if (dir.getModY() != 0) {
                    // y
                    a = rad.getY() * dir.getModY();
                    b = localPosY;
                    c = motY;
                } else {
                    // z
                    a = rad.getZ() * dir.getModZ();
                    b = localPosZ;
                    c = motZ;
                }
                if (c == 0.0) {
                    continue;
                }

                // Find how many steps of d (c) it takes to reach the box border (a) from p (b)
                double f = ((a - b) / c);
                if (f < 0.0) {
                    continue;
                }

                // Check is potential minimum distance first
                if (f > min_distance) {
                    continue;
                }

                // Check hit point within bounds of box
                if ((Math.abs(localPosX + f * motX) - rad.getX()) > ERR) {
                    continue;
                }
                if ((Math.abs(localPosY + f * motY) - rad.getY()) > ERR) {
                    continue;
                }
                if ((Math.abs(localPosZ + f * motZ) - rad.getZ()) > ERR) {
                    continue;
                }

                // Since d is a unit vector, f is now the distance we need
                min_distance = f;
            }
            return min_distance;
        }
    }
}
