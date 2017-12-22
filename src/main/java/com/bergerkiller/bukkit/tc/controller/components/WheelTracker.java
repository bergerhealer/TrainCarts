package com.bergerkiller.bukkit.tc.controller.components;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;

/**
 * Tracks the position of a wheel by looking at past positions, or alternatively,
 * walking the tracks to satisfy the wheel-to-center distance.
 */
public class WheelTracker {
    private final WheelTrackerMember owner;
    private final MinecartMember<?> member;
    private final boolean _front;
    private double _distance = 0.0; // Distance from the center this wheel is at
                                    // This will eventually be modified based on the model that is applied
    private Vector _position = null; // Position is relative to the minecart position
    private Vector _up = null;       // Up vector, used for angling the Minecart around the wheels

    public WheelTracker(WheelTrackerMember owner, boolean front) {
        this.owner = owner;
        this.member = owner.getOwner();
        this._front = front;
    }

    /**
     * Sets the distance from the center of the Minecart for this wheel
     * 
     * @param distance to set to
     */
    public void setDistance(double distance) {
        if (this._distance != distance) {
            this._distance = distance;
            this._position = null;
        }
    }

    /**
     * Gets the distance from the center of the Minecart for this wheel
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
     * Gets the up unit vector. This is the vector upwards, which controls
     * the angle of the Minecart around the set of wheels. This is particularly
     * important for rails like upside-down rails and vertical rails.
     * 
     * @return up vector
     */
    public Vector getUp() {
        if (this._up == null) {
            this.update(); // Required
        }
        return this._up;
    }

    /**
     * Recalculates the position of this wheel
     */
    public void update() {
        // Find the index of the rails for this member
        List<TrackedRail> rails = this.member.getGroup().getRailTracker().getRailInformation();

        int railIndex = -1;
        if (!this.member.isDerailed()) {
            for (int i = 0; i < rails.size(); i++) {
                TrackedRail rail = rails.get(i);
                if (rail.member == this.member && rail.block.equals(this.member.getBlock())) {
                    railIndex = i;
                    break;
                }
            }
        }

        // If this Minecart is derailed, set the wheels to point into the direction of the surrounding Minecarts
        if (railIndex == -1) {
            this.updateDerailed();
            return;
        }

        TrackedRail rail = rails.get(railIndex);

        // Calculate the approximate direction of this Minecart based on yaw/pitch
        // This is used to decide whether we move +/- by the members for front/back
        Vector direction = this.owner.getMemberDirection().forwardVector();
        if (!this._front) {
            direction.multiply(-1.0);
        }

        // Walk along the paths we know
        BlockFace memberDir = member.getDirection();
        RailPath.Position position = new RailPath.Position();
        position.posX = this.member.getEntity().loc.getX();
        position.posY = this.member.getEntity().loc.getY();
        position.posZ = this.member.getEntity().loc.getZ();
        position.motX = memberDir.getModX();
        position.motY = memberDir.getModY();
        position.motZ = memberDir.getModZ();
        int order = 0;
        double remainingDistance = this._distance;
        for (int index = railIndex; index >= 0 && index < rails.size() && remainingDistance >= 0.0001; index += order) {
            rail = rails.get(index);
            RailPath path = rail.getPath();
            if (order == 0)  {
                // Calculate the current movement direction of this Minecart
                // This is done by taking the getDirection() blockface and feeding it into the path
                // By using length 0, no actual movement is done, and only direction is retrieved
                // Use this information to figure out whether we iterate +1 or -1
                path.move(position, rail.block, 0.0);
                double dot = (position.motX * direction.getX()) +
                             (position.motY * direction.getY()) +
                             (position.motZ * direction.getZ());

                if (dot >= 0.0) {
                    order = -1;
                } else {
                    order = 1;
                }

                // Restore position motion for actual movement (re-use Position)
                position.motX = direction.getX();
                position.motY = direction.getY();
                position.motZ = direction.getZ();
            }
            remainingDistance -= path.move(position, rail.block, remainingDistance);
        }

        // Any remaining distance, simply 'assume' from the last-known direction information
        position.posX += position.motX * remainingDistance;
        position.posY += position.motY * remainingDistance;
        position.posZ += position.motZ * remainingDistance;

        // Refresh vectors
        this._position = new Vector(
                position.posX - this.member.getEntity().loc.getX(),
                position.posY - this.member.getEntity().loc.getY(),
                position.posZ - this.member.getEntity().loc.getZ());
        this._up = new Vector(position.upX, position.upY, position.upZ);
    }

    private void updateDerailed() {
        Vector direction = owner.getMemberDirection().forwardVector();
        if (!this._front) {
            direction.multiply(-1.0);
        }

        this._up = owner.getMemberDirection().upVector();

        MinecartGroup group = member.getGroup();
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
                    if (offset == null) {
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
    }
}
