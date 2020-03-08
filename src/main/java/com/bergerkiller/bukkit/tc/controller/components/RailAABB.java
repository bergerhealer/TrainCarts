package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * The bounding box of a rails block.
 * This is used to calculate the direction a minecart enters a rails block.
 */
public class RailAABB {
    public static RailAABB BLOCK = new RailAABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public final double x_min, y_min, z_min;
    public final double x_max, y_max, z_max;
    private final double x_min_err, y_min_err, z_min_err;
    private final double x_max_err, y_max_err, z_max_err;
    private final double offset_x_pos, offset_x_neg;
    private final double offset_y_pos, offset_y_neg;
    private final double offset_z_pos, offset_z_neg;

    public RailAABB(double x_min, double y_min, double z_min, double x_max, double y_max, double z_max) {
        this.x_min = x_min; this.y_min = y_min; this.z_min = z_min;
        this.x_max = x_max; this.y_max = y_max; this.z_max = z_max;

        final double CONST_BOX_ERROR = 1e-10;
        this.x_min_err = (this.x_min-CONST_BOX_ERROR);
        this.y_min_err = (this.y_min-CONST_BOX_ERROR);
        this.z_min_err = (this.z_min-CONST_BOX_ERROR);
        this.x_max_err = (CONST_BOX_ERROR+this.x_max);
        this.y_max_err = (CONST_BOX_ERROR+this.y_max);
        this.z_max_err = (CONST_BOX_ERROR+this.z_max);

        this.offset_x_pos = computeAxisOffset(x_min, x_max, 1);
        this.offset_x_neg = computeAxisOffset(x_min, x_max, -1);
        this.offset_y_pos = computeAxisOffset(y_min, y_max, 1);
        this.offset_y_neg = computeAxisOffset(y_min, y_max, -1);
        this.offset_z_pos = computeAxisOffset(z_min, z_max, 1);
        this.offset_z_neg = computeAxisOffset(z_min, z_max, -1);
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

        Vector result_end = new Vector(Double.NaN, Double.NaN, Double.NaN);
        BlockFace result = BlockFace.DOWN; // Fallback is DOWN

        double dx = direction.getX();
        if (dx > 0.0) {
            if (match(position, direction, (position.getX() - this.offset_x_pos) / dx, result_end)) {
                result = BlockFace.EAST;
            }
        } else if (dx < 0.0) {
            if (match(position, direction, (position.getX() - this.offset_x_neg) / dx, result_end)) {
                result = BlockFace.WEST;
            }
        }

        double dy = direction.getY();
        if (dy > 0.0) {
            if (match(position, direction, (position.getY() - this.offset_y_pos) / dy, result_end)) {
                result = BlockFace.UP;
            }
        } else if (dy < 0.0) {
            if (match(position, direction, (position.getY() - this.offset_y_neg) / dy, result_end)) {
                result = BlockFace.DOWN;
            }
        }

        double dz = direction.getZ();
        if (dz > 0.0) {
            if (match(position, direction, (position.getZ() - this.offset_z_pos) / dz, result_end)) {
                result = BlockFace.SOUTH;
            }
        } else if (dz < 0.0) {
            if (match(position, direction, (position.getZ() - this.offset_z_neg) / dz, result_end)) {
                result = BlockFace.NORTH;
            }
        }

        return result;
    }

    public boolean match(Vector position, Vector direction, double factor, Vector prev_result) {
        double end_x = position.getX() - factor * direction.getX();
        double end_y = position.getY() - factor * direction.getY();
        double end_z = position.getZ() - factor * direction.getZ();
        if (end_x < x_min_err || end_y < y_min_err || end_z < z_min_err ||
            end_x > x_max_err || end_y > y_max_err || end_z > z_max_err)
        {
            return false;
        }

        if (!Double.isNaN(prev_result.getX())) {
            double dot = ((end_x - prev_result.getX()) * direction.getX() +
                          (end_y - prev_result.getY()) * direction.getY() +
                          (end_z - prev_result.getZ()) * direction.getZ());
            if (dot > 0.0) {
                return false;
            }
        }

        prev_result.setX(end_x);
        prev_result.setY(end_y);
        prev_result.setZ(end_z);
        return true;
    }

    private static double computeAxisOffset(double min, double max, int axis) {
        return min + (max - min) * 0.5 * (1 - axis);
    }
}
