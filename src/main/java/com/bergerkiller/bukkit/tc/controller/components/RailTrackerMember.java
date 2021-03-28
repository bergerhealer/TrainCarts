package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicGround;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * Stores rail information of a Minecart Member
 */
public class RailTrackerMember extends RailTracker {
    private final MinecartMember<?> owner;
    private TrackedRail lastRail, rail;
    private RailLogic lastRailLogic, railLogic;
    private boolean railLogicSnapshotted = false;

    public RailTrackerMember(MinecartMember<?> owner) {
        this.owner = owner;
        this.lastRail = this.rail = new TrackedRail(owner);
        this.lastRailLogic = this.railLogic = RailLogicGround.INSTANCE;
    }

    /**
     * Refreshes the basic information with the information from the owner
     */
    public void onAttached() {
        this.lastRail = this.rail = TrackedRail.create(this.owner, false);
        this.lastRailLogic = this.railLogic = null;
        this.railLogicSnapshotted = false;
    }

    @Override
    public boolean isOnRails(Block railsBlock) {
        return owner.getGroup().getRailTracker().getMemberFromRails(railsBlock) == owner;
    }

    /**
     * Gets the rail the Minecart is on. This includes information such as from
     * what direction the minecart got onto the rail and what position on the rail
     * the minecart is moving.
     * 
     * @return current rail
     */
    public TrackedRail getRail() {
        return this.rail;
    }

    /**
     * Obtains a new track iterator iterating the tracks from this point towards the direction
     * the Minecart is moving.
     *
     * @return forward track iterator
     */
    @Deprecated
    public TrackIterator getTrackIterator() {
        return new TrackIterator(this.rail.state.railBlock(), this.owner.getDirectionTo());
    }

    /**
     * Gets whether the train is split up at this minecart, and the train
     * should be separated into two smaller trains from this Minecart onwards.
     * This is based on whether the minecart can be reached on the rails.
     * 
     * @return True if split up
     */
    public boolean isTrainSplit() {
        return this.rail.disconnected;
    }

    public Vector getMotionVector() {
        return this.rail.state.motionVector();
    }

    /**
     * Gets the rail type of the current tick
     *
     * @return current rail type
     */
    public RailType getRailType() {
        return this.rail.state.railType();
    }

    /**
     * Gets the rail type from the previous tick
     *
     * @return previous rail type
     */
    public RailType getLastRailType() {
        return this.lastRail.state.railType();
    }

    /**
     * Gets the block of the current tick
     *
     * @return current block
     */
    public Block getBlock() {
        return this.rail.state.railBlock();
    }

    public Block getMinecartPos() {
        return this.rail.minecartBlock;
    }

    /**
     * Gets rail state information of this Minecart.
     * This includes rail type, rail block, position and motion vector on the rails.
     * 
     * @return rail state
     */
    public RailState getState() {
        return this.rail.state;
    }

    /**
     * Gets the position of the rail of the current tick
     * 
     * @return current rail position
     */
    public IntVector3 getBlockPos() {
        return this.rail.position;
    }

    /**
     * Gets the block from the previous tick
     *
     * @return previous block
     */
    public Block getLastBlock() {
        return this.lastRail.state.railBlock();
    }

    /**
     * Gets the rail logic of the current tick
     *
     * @return current rail logic
     */
    public RailLogic getRailLogic() {
        if (this.railLogicSnapshotted && this.railLogic != null) {
            return this.railLogic;
        } else {
            try {
                return this.rail.state.loadRailLogic();
            } catch (Throwable t) {
                RailType.handleCriticalError(this.rail.state.railType(), t);

                // Change rail type to rail type none, returning AIR logic as a fallback
                RailState state = this.rail.state.clone();
                state.setRailPiece(RailPiece.create(RailType.NONE, state.positionBlock()));
                state.initEnterDirection();
                this.rail = new TrackedRail(this.rail.member, state, this.rail.disconnected);
                return RailLogicAir.INSTANCE;
            }
        }
    }

    /**
     * Gets the rail logic from the previous tick
     *
     * @return previous rail logic
     */
    public RailLogic getLastLogic() {
        if (lastRailLogic == null) {
            lastRailLogic = this.getRailLogic();
        }
        return lastRailLogic;
    }

    /**
     * Checks whether the current rails block has changed
     *
     * @return True if the block changed, False if not
     */
    public boolean hasBlockChanged() {
        Block a = lastRail.state.railBlock();
        Block b = rail.state.railBlock();
        return a.getX() != b.getX() || a.getY() != b.getY() || a.getZ() != b.getZ();
    }

    /**
     * Stops using the Rail Logic snapshot for the next run
     */
    public void setLiveRailLogic() {
        this.railLogicSnapshotted = false;
    }

    /**
     * Creates a snapshot of the Rail Logic for the entire next run
     */
    public void snapshotRailLogic() {
        this.railLogicSnapshotted = false;
        this.railLogic = this.getRailLogic();
        this.railLogicSnapshotted = true;
    }

    public void updateLast() {
        // Store the last rail information
        this.lastRail = this.rail;
        this.lastRailLogic = this.getRailLogic();
        owner.vertToSlope = false;
    }

    public void refresh(TrackedRail newInfo) {
        //System.out.println("DIR[" + owner.getIndex() + "] = " + newInfo.direction + " [" +
        //           newInfo.block.getX() + " / " + newInfo.block.getY() + " / " + newInfo.block.getZ() + "]");

        // Gather rail information


        // Refresh
        this.rail = newInfo;
        this.railLogic = null;
        this.railLogicSnapshotted = false;
    }

}
