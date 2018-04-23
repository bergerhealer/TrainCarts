package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailLogicState;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * A Moving point implementation that allows one to 'walk' along rails without
 * restricting to full-block movement, allowing for accurate distance calculations
 * and accurate Minecart positioning information for spawning on rails.
 */
public class TrackWalkingPoint {
    /**
     * The current track
     */
    public Block currentTrack;
    /**
     * Rail type of {@link #currentTrack}
     */
    public RailType currentRailType;
    /**
     * Rail logic of {@link #currentTrack}
     */
    public RailLogic currentRailLogic;
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
    /**
     * Is used to make sure rails are only crossed once, if enabled
     */
    private Set<Block> loopFilter = null;

    private boolean first = true;

    public TrackWalkingPoint(Block startRail, BlockFace startDirection) {
        this(startRail, startDirection, null);
    }

    public TrackWalkingPoint(Block startRail, BlockFace startDirection, Location startPos) {
        // Retrieve track information of the start rail
        this.currentTrack = startRail;
        this.currentRailType = RailType.getType(this.currentTrack);
        if (this.currentRailType == RailType.NONE) {
            this.position = null;
            this.direction = FaceUtil.faceToVector(startDirection);
            this.currentRailLogic = RailLogicAir.INSTANCE;
            return;
        }

        // Retrieve start rail logic
        RailLogicState state;
        if (startPos == null) {
            state = new RailLogicState(null, new Vector(0.5, 0.5, 0.5), startRail, startDirection);
        } else {
            state = new RailLogicState(null, startPos, startRail, startDirection);
        }
        this.currentRailLogic = this.currentRailType.getLogic(state);

        // TODO: This should not be a Block Face at all!
        // Make use the rail logic path to find the direction at the startDirection vector?
        // Could also put that kind of logic inside getMovementDirection(), though ugly.
        this.direction = FaceUtil.faceToVector(this.currentRailLogic.getMovementDirection(startDirection)).normalize();

        // If startPos is null, ask the start rail (type) for the spawn location and use that
        if (startPos == null) {
            this.position = this.currentRailType.getSpawnLocation(startRail, vecToFace(this.direction, true));
        } else {
            this.position = startPos.clone();
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
        if (this.position == null || this.currentRailType == RailType.NONE) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Walk as much distance as we can along the current rails
        RailPath.Position position = new RailPath.Position();
        position.posX = this.position.getX();
        position.posY = this.position.getY();
        position.posZ = this.position.getZ();
        position.motX = this.direction.getX();
        position.motY = this.direction.getY();
        position.motZ = this.direction.getZ();
        double remainingDistance = distance;
        int infCycleCtr = 0;
        while (true) {
            Block block = this.currentTrack;
            RailPath path = this.currentRailLogic.getPath();

            // Move along the path
            double moved;
            if (((moved = path.move(position, block, remainingDistance)) != 0.0) || (remainingDistance <= 0.0001)) {
                infCycleCtr = 0;
                remainingDistance -= moved;
                this.position.setX(position.posX);
                this.position.setY(position.posY);
                this.position.setZ(position.posZ);
                this.direction.setX(position.motX);
                this.direction.setY(position.motY);
                this.direction.setZ(position.motZ);
                if (remainingDistance <= 0.00001) {
                    // Assign current direction vector as yaw/pitch
                    this.position.setYaw(MathUtil.getLookAtYaw(this.direction));
                    this.position.setPitch(MathUtil.getLookAtPitch(this.direction.getX(), this.direction.getY(), this.direction.getZ()));

                    // Moved the full distance
                    this.moved = distance;
                    return true;
                }
            } else if (++infCycleCtr > 100) {
                // Infinite loop detected. Stop here.
                System.err.println("[TrackWalkingPoint] Infinite rails loop detected at " + block);
                System.err.println("[TrackWalkingPoint] Rail Logic at rail is " + this.currentRailLogic);
                System.err.println("[TrackWalkingPoint] Rail Type at rail is " + this.currentRailType);
                this.moved = (distance - remainingDistance);
                return false;
            }

            // Load next rails information
            // Move the path an infinitesmall amount to beyond the current rail
            this.position.setX(position.posX + 1e-10 * position.motX);
            this.position.setY(position.posY + 1e-10 * position.motY);
            this.position.setZ(position.posZ + 1e-10 * position.motZ);

            // Look up the rails Block at the new found position
            Block nextPosBlock = this.position.getBlock();
            RailInfo nextRailInfo = RailType.findRailInfo(nextPosBlock);
            if (nextRailInfo == null) {
                this.currentTrack = nextPosBlock;
                this.currentRailType = RailType.NONE;
            } else if (!BlockUtil.equals(this.currentTrack, nextRailInfo.railBlock)) {
                // Check loop filter
                this.currentTrack = nextRailInfo.railBlock;
                if (this.loopFilter != null && !this.loopFilter.add(this.currentTrack)) {
                    this.currentRailType = RailType.NONE;
                } else {
                    this.currentRailType = RailType.getType(this.currentTrack);
                }
            }

            // No next rail available. This is it.
            if (this.currentRailType == RailType.NONE) {
                this.moved = (distance - remainingDistance);
                return false;
            }

            // Calculate the face of the rails block position being entered
            BlockFace enteredFace = Util.calculateEnterFace(
                    new Vector(this.position.getX() - nextPosBlock.getX(),
                               this.position.getY() - nextPosBlock.getY(),
                               this.position.getZ() - nextPosBlock.getZ()),
                    new Vector(position.motX, position.motY, position.motZ));

            // Refresh rail logic for the new position and state
            RailLogicState state = new RailLogicState(null, this.position, this.currentTrack, enteredFace);
            this.currentRailLogic = this.currentRailType.getLogic(state);
        }
    }

    /**
     * Sets whether a loop filter is employed, which tracks the positions already returned
     * and stops iteration when encountering a block already visited.
     * 
     * @param enabled
     */
    public void setLoopFilter(boolean enabled) {
        this.loopFilter = enabled ? new HashSet<Block>() : null;
        if (enabled && this.currentTrack != null) {
            this.loopFilter.add(this.currentTrack);
        }
    }

    // some magic to turn a vector into the most appropriate block face
    private static BlockFace vecToFace(Vector vector, boolean useSubCardinalDirections) {
        return vecToFace(vector.getX(), vector.getY(), vector.getZ(), useSubCardinalDirections);
    }

    // some magic to turn a vector into the most appropriate block face
    private static BlockFace vecToFace(double dx, double dy, double dz, boolean useSubCardinalDirections) {
        double sqlenxz = dx*dx + dz*dz;
        double sqleny = dy*dy;
        if (sqleny > (sqlenxz + 1e-6)) {
            return FaceUtil.getVertical(dy);
        } else {
            return FaceUtil.getDirection(dx, dz, useSubCardinalDirections);
        }
    }
}
