package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * A 3D box rotated in 3D space that allows for simple collision hit testing.
 * Radius is the x/y/z cube radius around the position. Orientation is around
 * the position also.
 */
public class CollisionBox {
    public double posX;
    public double posY;
    public double posZ;
    public double radX;
    public double radY;
    public double radZ;
    private Quaternion orientation_inv = new Quaternion();

    public void setPosition(Vector pos) {
        this.posX = pos.getX();
        this.posY = pos.getY();
        this.posZ = pos.getZ();
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setRadius(double rx, double ry, double rz) {
        this.radX = 0.5 * rx;
        this.radY = 0.5 * ry;
        this.radZ = 0.5 * rz;
    }

    public void setOrientation(Quaternion orientation) {
        this.orientation_inv.setTo(orientation);
        this.orientation_inv.invert();
    }

    /**
     * Performs a hit test to see whether this collision box is hit when
     * looked at from a known eye location
     * 
     * @param eyeLocation
     * 
     * @return distance to the box, Double.MAX_VALUE when not touching.
     */
    public double hittest(Location eyeLocation) {
        // Compute start point
        Vector p = new Vector(eyeLocation.getX() - this.posX,
                              eyeLocation.getY() - this.posY,
                              eyeLocation.getZ() - this.posZ);
        this.orientation_inv.transformPoint(p);

        // Check start point already inside box
        if (Math.abs(p.getX()) <= this.radX && Math.abs(p.getY()) <= this.radY && Math.abs(p.getZ()) <= this.radZ) {
            return 0.0;
        }

        // Compute direction after rotation
        Vector d = eyeLocation.getDirection();
        this.orientation_inv.transformPoint(d);

        // Check all 6 faces and find the intersection point with this axis
        // Then check whether these points are within the range of the box
        // If true, compute the distance from the start point and track the smallest value
        final double ERR = 1e-6;
        double min_distance = Double.MAX_VALUE;
        for (BlockFace dir : FaceUtil.BLOCK_SIDES) {
            double a, b, c;
            if (dir.getModX() != 0) {
                // x
                a = this.radX * dir.getModX();
                b = p.getX();
                c = d.getX();
            } else if (dir.getModY() != 0) {
                // y
                a = this.radY * dir.getModY();
                b = p.getY();
                c = d.getY();
            } else {
                // z
                a = this.radZ * dir.getModZ();
                b = p.getZ();
                c = d.getZ();
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
            if ((Math.abs(p.getX() + f * d.getX()) - this.radX) > ERR) {
                continue;
            }
            if ((Math.abs(p.getY() + f * d.getY()) - this.radY) > ERR) {
                continue;
            }
            if ((Math.abs(p.getZ() + f * d.getZ()) - this.radZ) > ERR) {
                continue;
            }

            // Since d is a unit vector, f is now the distance we need
            min_distance = f;
        }
        return min_distance;
    }
}
