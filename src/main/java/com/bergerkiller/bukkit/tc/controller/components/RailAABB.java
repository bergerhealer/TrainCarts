package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * The bounding box of a rails block.
 * This is used to calculate the direction a minecart enters a rails block.
 */
public class RailAABB {
    public final double x_min, y_min, z_min;
    public final double x_max, y_max, z_max;
    public static RailAABB BLOCK = new RailAABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public RailAABB(double x_min, double y_min, double z_min, double x_max, double y_max, double z_max) {
        this.x_min = x_min; this.y_min = y_min; this.z_min = z_min;
        this.x_max = x_max; this.y_max = y_max; this.z_max = z_max;
    }

    /**
     * Calculates the face of this AABB that is first entered when moving in a direction from a position.
     * The movement is that of an infinite line. The face direction first hit is returned. The position is
     * relative to this AABB (the rails).
     * 
     * @param position x/y/z of the position relative to the block
     * @param direction x/y/z of the direction in which is moved
     * @return face of the block first entered
     */
    public BlockFace calculateEnterFace(Vector position, Vector direction) {
        // Track back the location of the minecart using the velocity towards the edge of the current block
        // The edge we encounter is the direction we have to use
        // For x/y/z, see how many times we have to inversely multiply the velocity to get to it
        // The one with the lowest multiplication indicates the edge we will hit first
        // Imagine taking steps back at the current velocity until the minecart hits an edge of the current block
        // The first edge encountered is the edge the minecart came from
        double result_end_x = 0.0;
        double result_end_y = 0.0;
        double result_end_z = 0.0;
        final double CONST_BOX_ERROR = 1e-10;
        final double CONST_BOX_MIN = (0.0 - CONST_BOX_ERROR);
        final double CONST_BOX_MAX = (1.0 + CONST_BOX_ERROR);
        BlockFace result = null;
        for (BlockFace dir : FaceUtil.BLOCK_SIDES) {
            double a, b, c;
            if (dir.getModX() != 0) {
                // x
                a = this.x_min + (this.x_max - this.x_min) * 0.5 * (1 - dir.getModX());
                b = position.getX();
                c = direction.getX();
            } else if (dir.getModY() != 0) {
                // y
                a = this.y_min + (this.y_max - this.y_min) * 0.5 * (1 - dir.getModY());
                b = position.getY();
                c = direction.getY();
            } else {
                // z
                a = this.z_min + (this.z_max - this.z_min) * 0.5 * (1 - dir.getModZ());
                b = position.getZ();
                c = direction.getZ();
            }
            if (c == 0.0) {
                continue;
            }

            double f = ((b - a) / c);
            double end_x = position.getX() - f * direction.getX();
            double end_y = position.getY() - f * direction.getY();
            double end_z = position.getZ() - f * direction.getZ();
            if (end_x >= CONST_BOX_MIN && end_y >= CONST_BOX_MIN && end_z >= CONST_BOX_MIN &&
                end_x <= CONST_BOX_MAX && end_y <= CONST_BOX_MAX && end_z <= CONST_BOX_MAX) {
                if (result != null) {
                    double dot = ((end_x - result_end_x) * direction.getX() +
                                  (end_y - result_end_y) * direction.getY() +
                                  (end_z - result_end_z) * direction.getZ());
                    if (dot > 0.0) {
                        continue;
                    }
                }
                result = dir;
                result_end_x = end_x;
                result_end_y = end_y;
                result_end_z = end_z;
            }
        }
        return result;
    }
}
