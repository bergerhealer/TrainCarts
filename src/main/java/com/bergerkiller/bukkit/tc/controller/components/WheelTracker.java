package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Tracks the position of a wheel by looking at past positions, or alternatively,
 * walking the tracks to satisfy the wheel-to-center distance.
 */
public class WheelTracker {
    private final MinecartMember<?> member;
    private final boolean _front;
    private double _distance = 0.4; // Distance from the center this wheel is at
                                    // This will eventually be modified based on the model that is applied
    private Vector _position = null; // Position is relative to the minecart position

    public WheelTracker(MinecartMember<?> member, boolean front) {
        this.member = member;
        this._front = front;
    }

    /**
     * Gets the distance from the center of this wheel
     * 
     * @return wheel distance
     */
    public double getDistance() {
        return this._distance;
    }

    /**
     * Gets the center-relative position of this wheel.
     * The center is the exact coordinates of the Minecart itself.
     * 
     * @return center-relative position
     */
    public Vector getPosition() {
        if (this._position == null) {
            this.update(); // Required
        }
        return this._position;
    }

    /**
     * Recalculates the position of this wheel
     */
    public void update() {
        if (true) return; // Disable
        
        
        MinecartGroup group = this.member.getGroup();

        // Calculate the approximate direction of this Minecart based on yaw/pitch
        // This is used to decide whether we move +/- by the members for front/back
        Vector direction = MathUtil.getDirection(
                this.member.getEntity().loc.getYaw() + 90.0f,
                this.member.getEntity().loc.getPitch());
        if (!this._front) {
            direction.multiply(-1.0);
        }

        if (group.size() == 1) {
            // For single-minecart trains, we can't do anything more
            this._position = direction.multiply(this._distance);

        } else {

            // Calculate the offset from the two neighbouring carts
            Vector orientation = member.calculateOrientation();

            // Detect the delta to use
            int delta =  (orientation.dot(direction) > 0.0) ? -1 : 1;

            int index = this.member.getIndex();
            this._position = new Vector();
            double distanceRemaining = this._distance;
            MinecartMember<?> prevMember = this.member;
            Location prevLoc = this.member.getEntity().getLocation();
            prevLoc.setYaw(prevLoc.getYaw() + 90.0f);

            Vector offset = null;
            while (true) {
                // Get next neighbour
                index += delta;

                // If neighbour does not exist, use the last known offset for the rest of the path
                if (index < 0 || index >= group.size()) {
                    /*
                    BlockFace walkDir;
                    if (delta < 0) {
                        // Forwards
                        walkDir = prevMember.getDirectionTo();
                    } else {
                        // backwards
                        walkDir = prevMember.getDirectionFrom().getOppositeFace();
                    }

                    TrackWalkingPoint point = new TrackWalkingPoint(prevLoc, prevMember.getBlock(), walkDir);
                    point.next();
                    point.move(distanceRemaining);

                    this._position.add(new Vector(
                            point.currentPosition.getX() - prevLoc.getX(),
                            point.currentPosition.getY() - prevLoc.getY(),
                            point.currentPosition.getZ() - prevLoc.getZ()
                            ));
                    distanceRemaining = 0.0;
                    */
                    
                    if (offset == null) {
                        
                        // Walk the rails to figure out the position


                        // No offset known. Use the one from the Minecart
                        offset = direction.clone();

                        // No offset known. Use inverse of the other direction.
                        Location loc = group.get(index - 2 * delta).getEntity().getLocation();
                        offset = new Vector(prevLoc.getX() - loc.getX(),
                                            prevLoc.getY() - loc.getY(),
                                            prevLoc.getZ() - loc.getZ());
                    } else {
                        
                    }
                    MathUtil.setVectorLength(offset, distanceRemaining);
                    this._position.add(offset);
                    distanceRemaining = 0.0;

                    break;
                }

                // Calculate the new vector we move along
                prevMember = group.get(index);
                Location loc = prevMember.getEntity().getLocation();
                offset = new Vector(loc.getX() - prevLoc.getX(),
                                    loc.getY() - prevLoc.getY(),
                                    loc.getZ() - prevLoc.getZ());
                prevLoc = loc;

                // Walk along the offset for distance remaining
                double length = offset.length();
                if (distanceRemaining > length) {
                    // Walk the entire distance
                    this._position.add(offset);
                    distanceRemaining -= length;
                } else {
                    // Walk remaining length and done!
                    MathUtil.setVectorLength(offset, distanceRemaining);
                    this._position.add(offset);
                    distanceRemaining = 0.0;
                    break;
                }
            }
        }

        // Debug: show some effect at the position
        /*
        Location loc = member.getEntity().getLocation().add(_position);
        if (this._front) {
            loc.getWorld().spawnParticle(Particle.WATER_BUBBLE, loc.getX(), loc.getY(), loc.getZ(), 1);
        } else {
            loc.getWorld().spawnParticle(Particle.NOTE, loc.getX(), loc.getY(), loc.getZ(), 1);
        }
        */

    }
}
