package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
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
     * Stores the current state of walking
     */
    public final RailState state;
    /**
     * Rail logic of {@link #state}
     */
    public RailLogic currentRailLogic;
    /**
     * Rail path of {@link #currentRailLogic}
     */
    public RailPath currentRailPath;
    /**
     * The actual distance moved during the last {@link #move(double)} call
     */
    public double moved = 0.0;
    /**
     * The total distance moved since instantiating this walking point
     */
    public double movedTotal = 0.0;
    /**
     * Is used to make sure rails are only crossed once, if enabled
     */
    private Set<Block> loopFilter = null;
    /**
     * Detects a recurring loop when stuck moving on the same location (end of rail?)
     */
    private Vector lastLocation = null;
    /**
     * Counter to track repeated positions, indicating getting stuck
     */
    private int _stuckCtr = 0;

    private boolean first = true;

    public TrackWalkingPoint(RailState state) {
        state.position().assertAbsolute();
        this.state = state.clone();
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
    }

    public TrackWalkingPoint(Location startPos, Vector motionVector) {
        this.state = new RailState();
        this.state.setRailPiece(RailPiece.createWorldPlaceholder(startPos.getWorld()));
        this.state.position().setMotion(motionVector);
        this.state.position().setLocation(startPos);
        RailType.loadRailInformation(this.state);
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
    }

    public TrackWalkingPoint(Block startRail, BlockFace motionFace) {
        this.state = new RailState();
        this.state.position().relative = false;
        if (startRail != null) {
            this.state.setRailPiece(RailPiece.create(RailType.getType(startRail), startRail));
            this.state.position().setMotion(motionFace);
            this.state.position().setLocation(this.state.railType().getSpawnLocation(startRail, motionFace));
            this.state.initEnterDirection();
            this.currentRailLogic = this.state.loadRailLogic();
            this.currentRailPath = this.currentRailLogic.getPath();
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
     * Moves a full step towards the next rails block, limiting the steps taken by the limit parameter.
     * This can be used to discover the rails blocks that covers a particular stretch of track.
     * The {@link #moved} and {@link #movedTotal} parameters are updated with this call.
     * 
     * @param limit distance to move
     * @return True if the step was successful
     */
    public boolean moveStep(double limit) {
        // No rails
        if (isDerailed()) {
            return false;
        }

        // Move the full length of the path, to the end of the path
        this.moved = this.currentRailPath.move(this.state, limit);
        this.movedTotal += this.moved;
        this.state.initEnterDirection();

        // When moved closely equals the limit, we've reached the end of track.
        double diff = (this.moved - limit);
        if (diff > -1e-10 && diff < 1e-10) {
            return false;
        }

        // Attempt loading the next rail information. Return false if no more rails exist.
        if (!this.loadNextRail()) {
            return false;
        }

        // All good, found the next rail!
        return true;
    }

    /**
     * Moves the full distance past the current rail's path to the next rail.
     * The {@link #position} and {@link #direction} is updated.
     * Can be used in a loop to iterate by all the rail blocks.
     * 
     * @return True if movement was successful, False if not
     */
    public boolean moveFull() {
        // No rails
        if (isDerailed()) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Move the full length of the path, to the end of the path
        this.moved = this.currentRailPath.move(this.state, Double.MAX_VALUE);
        this.movedTotal += this.moved;
        this.state.initEnterDirection();

        // Attempt moving to next rails block
        if (!loadNextRail()) {
            return false;
        }

        // Snap onto the path before returning
        this.currentRailPath.snap(this.state.position(), this.state.railBlock());
        return true;
    }

    /**
     * Moves the distance specified over the current rails, moving past multiple rails if needed.
     * The {@link #position} and {@link #direction} is updated.
     *
     * @param distance to move
     * @return True if movement was successful, False if not
     */
    public boolean move(final double distance) {
        // If no position is known, then we did not have a valid starting point at all
        if (isDerailed()) {
            return false;
        }

        // If first time, return the current position
        if (this.first) {
            this.first = false;
            return true;
        }

        // Walk as much distance as we can along the current rails
        double remainingDistance = distance;
        int infCycleCtr = 0;
        while (true) {
            // Move along the path
            double moved;
            if (((moved = this.currentRailPath.move(this.state, remainingDistance)) != 0.0) || (remainingDistance <= 0.0001)) {
                infCycleCtr = 0;
                remainingDistance -= moved;
                if (remainingDistance <= 0.00001) {
                    // Moved the full distance
                    this.moved = distance;
                    this.movedTotal += this.moved;
                    this.state.initEnterDirection();
                    return true;
                }
            } else if (++infCycleCtr > 100) {
                // Infinite loop detected. Stop here.
                System.err.println("[TrackWalkingPoint] Infinite rails loop detected at " + this.state.railBlock());
                System.err.println("[TrackWalkingPoint] Rail Logic at rail is " + this.currentRailLogic);
                System.err.println("[TrackWalkingPoint] Rail Type at rail is " + this.state.railType());
                this.moved = (distance - remainingDistance);
                this.movedTotal += this.moved;
                this.state.initEnterDirection();
                return false;
            }

            // Attempt moving to next rails block
            if (!loadNextRail()) {
                this.moved = (distance - remainingDistance);
                this.movedTotal += this.moved;
                this.state.initEnterDirection();
                return false;
            }
        }
    }

    private boolean loadNextRail() {
        RailPath.Position position = this.state.position();

        // If position is already the same then we ran into a nasty loop that is no good!
        // Break out of it when detected to avoid freezing the server
        if (this.lastLocation == null) {
            this.lastLocation = new Vector(position.posX, position.posY, position.posZ);
            this._stuckCtr = 0;
        } else if (this.lastLocation.getX() == position.posX &&
                   this.lastLocation.getY() == position.posY &&
                   this.lastLocation.getZ() == position.posZ)
        {
            if (++this._stuckCtr > 20) {
                System.err.println("[TrackWalkingPoint] Stuck on rails block " + this.state.railBlock());
                System.err.println("[TrackWalkingPoint] Rail Logic at rail is " + this.currentRailLogic);
                System.err.println("[TrackWalkingPoint] Rail Type at rail is " + this.state.railType());
            }
            return false;
        } else {
            this.lastLocation.setX(position.posX);
            this.lastLocation.setY(position.posY);
            this.lastLocation.setZ(position.posZ);
            this._stuckCtr = 0;
        }

        // Load next rails information
        // Move the path an infinitesmall amount to beyond the current rail
        position.smallAdvance();

        // Rail Type lookup + loop filter logic
        Block prevRailBlock = this.state.railBlock();
        if (RailType.loadRailInformation(this.state) &&
            this.loopFilter != null &&
            !BlockUtil.equals(this.state.railBlock(), prevRailBlock) &&
            !this.loopFilter.add(this.state.railBlock()))
        {
            this.state.setRailType(RailType.NONE);
        }

        // No next rail available. This is it.
        if (isDerailed()) {
            return false;
        }

        // Refresh rail logic for the new position and state
        this.currentRailLogic = this.state.loadRailLogic();
        this.currentRailPath = this.currentRailLogic.getPath();
        return true;
    }

    private boolean isDerailed() {
        return this.state.railType() == RailType.NONE || this.currentRailPath.isEmpty();
    }

    /**
     * Sets whether a loop filter is employed, which tracks the positions already returned
     * and stops iteration when encountering a block already visited.
     * 
     * @param enabled
     */
    public void setLoopFilter(boolean enabled) {
        this.loopFilter = enabled ? new HashSet<Block>() : null;
        if (enabled && !isDerailed()) {
            this.loopFilter.add(this.state.railBlock());
        }
    }

    /**
     * Moves full or smaller steps until a particular rails block is reached.
     * The Spawn Location of the rails block is moved towards.
     * 
     * @param railsBlock to find
     * @param maxDistance when to stop looking (and return False)
     * @return True when the rails was found, False if not.
     */
    public boolean moveFindRail(Block railsBlock, double maxDistance) {
        // Move full rail distances until the rails block is found. if not starting out on the rail
        this.movedTotal = 0.0;
        boolean startedOnRail = BlockUtil.equals(this.state.railBlock(), railsBlock);
        if (!startedOnRail) {
            do {
                // Out of tracks or distance exceeded
                if (!this.moveFull() || this.movedTotal > maxDistance) {
                    return false;
                }
            } while (!BlockUtil.equals(this.state.railBlock(), railsBlock));
        }

        // Found our rails Block! Move a tiny step further onto it.
        // Query the desired spawn location that we should move towards.
        Location spawnLocation = this.state.railType().getSpawnLocation(railsBlock, this.state.enterFace());
        for (int i = 0; i < 10; i++) {
            double distance = this.state.position().distance(spawnLocation);
            if (distance < 1e-4) {
                // Reached spawn location
                break;
            }
            double moved = this.currentRailPath.move(this.state, distance);
            this.movedTotal += moved;
            if (moved < 1e-4) {
                // When we start out on the rail, we could be walking into the wrong direction
                // In that case, fail the walker, as we are really moving <off> the current rail,
                // never reaching the intended center position.
                if (startedOnRail) {
                    return false;
                }

                // End of path
                break;
            }
        }
        this.moved = this.movedTotal;
        return this.movedTotal <= maxDistance;
    }
}
