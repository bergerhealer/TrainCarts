package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class MemberActionWaitOccupied extends MemberAction implements WaitAction {
    private final int maxsize;
    private final long delay;
    private final double launchDistance;
    private final BlockFace launchDirection;
    private final Double launchVelocity;
    private BlockFace direction;
    private Block start;
    private double launchforce;
    private int counter = 20;
    private boolean breakCode = false;

    public MemberActionWaitOccupied(final int maxsize, final long delay, final double launchDistance, BlockFace launchDirection, Double launchVelocity) {
        this.maxsize = maxsize;
        this.delay = delay;
        this.launchDistance = launchDistance;
        this.launchDirection = launchDirection;
        this.launchVelocity = launchVelocity;
    }

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

    @Override
    public void bind() {
        this.direction = getMember().getDirectionTo();
        this.start = getMember().getBlock();
        this.launchforce = this.getGroup().getAverageForce();
    }

    @Override
    public void start() {
        if (this.handleOccupied()) {
            this.getGroup().stop(true);
        } else {
            breakCode = true;
        }
    }

    public boolean handleOccupied() {
        return handleOccupied(this.start, this.direction, this.getMember(), this.maxsize);
    }

    @Override
    public boolean update() {
        if (breakCode) return true;
        if (counter++ >= 20) {
            if (!this.handleOccupied()) {
                // Add Delay
                if (this.delay > 0) {
                    this.getGroup().getActions().addActionWait(this.delay);
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
    public boolean isMovementSuppressed() {
        return true;
    }
}
