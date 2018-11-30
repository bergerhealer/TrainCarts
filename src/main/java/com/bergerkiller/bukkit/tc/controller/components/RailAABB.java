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
    private final AxisCompute[] compute;

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

        // Cache these optimizations
        this.compute = new AxisCompute[] {
                new AxisComputeX(this, BlockFace.EAST),
                new AxisComputeX(this, BlockFace.WEST),
                new AxisComputeY(this, BlockFace.UP),
                new AxisComputeY(this, BlockFace.DOWN),
                new AxisComputeZ(this, BlockFace.NORTH),
                new AxisComputeZ(this, BlockFace.SOUTH)
        };
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

        // Optimize the cases when the direction vector is perfectly along an axis
        // This situation occurs very frequently on straight rails
        double ls = direction.lengthSquared();
        if (ls == (direction.getX()*direction.getX())) {
            return (direction.getX() >= 0.0) ? BlockFace.EAST : BlockFace.WEST;
        }
        if (ls == (direction.getZ()*direction.getZ())) {
            return (direction.getZ() >= 0.0) ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        if (ls == (direction.getY()*direction.getY())) {
            return (direction.getY() >= 0.0) ? BlockFace.UP : BlockFace.DOWN;
        }

        double result_end_x = 0.0;
        double result_end_y = 0.0;
        double result_end_z = 0.0;
        BlockFace result = null;

        for (AxisCompute comp : this.compute) {
            double c = comp.coord(direction);
            if (c == 0.0) {
                continue;
            }

            double f = (comp.coord(position) - comp.a) / c;
            double end_x = position.getX() - f * direction.getX();
            double end_y = position.getY() - f * direction.getY();
            double end_z = position.getZ() - f * direction.getZ();
            if (end_x >= this.x_min_err && end_y >= this.y_min_err && end_z >= this.z_min_err &&
                end_x <= this.x_max_err && end_y <= this.y_max_err && end_z <= this.z_max_err) {
                if (result != null) {
                    double dot = ((end_x - result_end_x) * direction.getX() +
                                  (end_y - result_end_y) * direction.getY() +
                                  (end_z - result_end_z) * direction.getZ());
                    if (dot > 0.0) {
                        continue;
                    }
                }
                result = comp.face;
                result_end_x = end_x;
                result_end_y = end_y;
                result_end_z = end_z;
            }
        }
        if (result == null) {
            return BlockFace.DOWN; // Fallback
        }
        return result;
    }

    private abstract static class AxisCompute {
        public final BlockFace face;
        public final double a;

        public AxisCompute(RailAABB aabb, BlockFace dir) {
            this.face = dir;
            if (dir.getModX() != 0) {
                // x
                a = aabb.x_min + (aabb.x_max - aabb.x_min) * 0.5 * (1 - dir.getModX());
            } else if (dir.getModY() != 0) {
                // y
                a = aabb.y_min + (aabb.y_max - aabb.y_min) * 0.5 * (1 - dir.getModY());
            } else {
                // z
                a = aabb.z_min + (aabb.z_max - aabb.z_min) * 0.5 * (1 - dir.getModZ());
            }
        }

        public abstract double coord(Vector v);
    }

    private static final class AxisComputeX extends AxisCompute {

        public AxisComputeX(RailAABB aabb, BlockFace dir) {
            super(aabb, dir);
        }

        @Override
        public double coord(Vector v) {
            return v.getX();
        }
    }

    private static final class AxisComputeY extends AxisCompute {

        public AxisComputeY(RailAABB aabb, BlockFace dir) {
            super(aabb, dir);
        }

        @Override
        public double coord(Vector v) {
            return v.getY();
        }
    }

    private static final class AxisComputeZ extends AxisCompute {

        public AxisComputeZ(RailAABB aabb, BlockFace dir) {
            super(aabb, dir);
        }

        @Override
        public double coord(Vector v) {
            return v.getZ();
        }
    }
}
