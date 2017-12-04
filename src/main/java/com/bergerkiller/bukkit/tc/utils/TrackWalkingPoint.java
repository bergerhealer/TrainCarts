package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * A Moving point implementation that allows one to 'walk' along rails without
 * restricting to full-block movement, allowing for accurate distance calculations
 * and accurate Minecart positioning information for spawning on rails.
 */
public class TrackWalkingPoint extends TrackMovingPoint {
    /**
     * The current position of the current rails
     */
    public Location position;
    /**
     * The direction Vector along which is currently moved
     */
    public Vector direction;
    /**
     * The actual distance moved during the last {@link #move(double)} call
     */
    public double moved;

    private boolean first = true;

    public TrackWalkingPoint(Block startRail, BlockFace startDirection) {
        this(startRail, startDirection, null);
    }

    public TrackWalkingPoint(Block startRail, BlockFace startDirection, Location startPos) {
        super(startRail, startDirection);
        if (startPos == null) {
            if (super.hasNext()) {
                this.position = this.currentRail.getSpawnLocation(this.currentTrack, this.currentDirection);
            } else {
                this.position = null;
            }
        } else {
            this.position = startPos.clone();
        }
        this.direction = FaceUtil.faceToVector(startDirection).normalize();

        // Skip the first block, as to avoid moving 'backwards' one block
        if (super.hasNext()) {
            super.next(true);
        }
    }

    /**
     * Tells the walker to skip the very first starting point when running
     * {@link #move(double)} for the first time. This means the first position
     * returned after a move is a distance from the origin.
     */
    public void skipFirst() {
        this.first = false;
    }

    /**
     * Moves the distance specified, calling {@link #next()} as often as is needed.
     * The {@link #position} and {@link #direction} is updated.
     *
     * @param distance to move
     * @return True if movement was successful, False if not
     */
    public boolean move(final double distance) {
        // If no position is known, then we did not have a valid starting point at all
        if (this.position == null) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Walk as much distance as we can along the current rails
        double remainingDistance = distance;
        while (true) {
            Block block = this.currentTrack;
            BlockFace faceDirection = this.currentDirection;
            RailType type = this.currentRail;
            RailLogic logic = type.getLogic(null, block, faceDirection);
            RailPath path = logic.getPath();

            // Move along the path
            Vector position = new Vector(this.position.getX() - block.getX(), this.position.getY() - block.getY(), this.position.getZ() - block.getZ());
            double moved;
            if (((moved = path.move(position, this.direction, remainingDistance)) != 0.0) || (remainingDistance <= 0.0001)) {
                remainingDistance -= moved;
                this.position.setX(position.getX() + block.getX());
                this.position.setY(position.getY() + block.getY());
                this.position.setZ(position.getZ() + block.getZ());
                if (remainingDistance <= 0.00001) {
                    // Assign current direction vector as yaw/pitch
                    this.position.setYaw(MathUtil.getLookAtYaw(this.direction));
                    this.position.setPitch(MathUtil.getLookAtPitch(this.direction.getX(), this.direction.getY(), this.direction.getZ()));

                    // Moved the full distance
                    this.moved = distance;
                    return true;
                }
            }

            // Load next rails information
            if (this.hasNext()) {
                this.next(true);
            } else {
                // No next rail available. This is it.
                this.moved = (distance - remainingDistance);
                return false;
            }
        }
    }

}
