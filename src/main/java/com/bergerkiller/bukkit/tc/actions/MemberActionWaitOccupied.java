package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class MemberActionWaitOccupied extends MemberAction implements WaitAction {
    private final double maxDistance;
    private final long delay;
    private final double launchDistance;
    private final BlockFace launchDirection;
    private final Double launchVelocity;
    private BlockFace direction;
    private double launchforce;
    private int counter = 20;
    private boolean breakCode = false;
    private Block toggleLeversOf = null;

    public MemberActionWaitOccupied(final double maxDistance, final long delay, final double launchDistance, BlockFace launchDirection, Double launchVelocity) {
        this.maxDistance = maxDistance;
        this.delay = delay;
        this.launchDistance = launchDistance;
        this.launchDirection = launchDirection;
        this.launchVelocity = launchVelocity;
    }

    /**
     * Toggles levers as part of this action
     * 
     * @param block
     * @return this
     */
    public MemberActionWaitOccupied setToggleLeversOf(Block block) {
        this.toggleLeversOf = block;
        BlockUtil.setLeversAroundBlock(block, true);
        return this;
    }

    // Old code. Stop using it, and use getSpeedAhead instead.
    /*
    public static boolean handleOccupied(Block start, BlockFace direction, MinecartMember<?> ignore, int maxdistance) {
        TrackIterator iter = new TrackIterator(start, direction);
        while (iter.hasNext() && --maxdistance >= 0) {
            MinecartMember<?> mm = MinecartMemberStore.getAt(iter.next());
            if (mm != null && mm.getGroup() != ignore.getGroup()) {
                ignore.setIgnoreCollisions(true);
                return true;
            }
        }
        ignore.setIgnoreCollisions(false);
        return false;
    }
    */

    @Override
    public void bind() {
        this.direction = getMember().getDirection();
        this.launchforce = this.getGroup().getAverageForce();
    }

    @Override
    public void start() {
        if (this.handleOccupied()) {
            this.getGroup().stop(true);
            if (this.toggleLeversOf != null) {
                BlockUtil.setLeversAroundBlock(this.toggleLeversOf, true);
            }
        } else {
            breakCode = true;
        }
    }

    public boolean handleOccupied() {
        // return handleOccupied(this.start, this.direction, this.getMember(), this.maxsize);
        return this.getGroup().getSpeedAhead(this.maxDistance) != Double.MAX_VALUE;
    }

    @Override
    public boolean update() {
        // It can happen the action starts in a state that the area isn't occupied
        // Since no stop() was done, there's no need to wait or launch
        if (breakCode) {
            return true;
        }

        // Every second
        if (counter++ >= 20) {
            if (!this.handleOccupied()) {
                // Add Delay
                if (this.delay > 0) {
                    this.getGroup().getActions().addActionWait(this.delay);
                }

                // Toggle levers back up, after the delay if a delay is used
                if (this.toggleLeversOf != null) {
                    if (this.delay > 0) {
                        this.getGroup().getActions().addActionSetLevers(this.toggleLeversOf, false);
                    } else {
                        BlockUtil.setLeversAroundBlock(this.toggleLeversOf, false);
                    }
                }

                // Launch
                if (this.launchVelocity != null && this.launchDirection != null) {
                    this.getMember().getActions().addActionLaunch(this.launchDirection, this.launchDistance, this.launchVelocity);
                } else {
                    this.getMember().getActions().addActionLaunch(this.direction, this.launchDistance, this.launchforce);
                }
                return true;
            } else {
                //this.wasoccupied = this.handleOccupied();
            }
            counter = 0;
        }
        return false;
    }

    @Override
    public void cancel() {
        // Toggle lever back up
        if (this.toggleLeversOf != null) {
            BlockUtil.setLeversAroundBlock(this.toggleLeversOf, false);
        }
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }
}
