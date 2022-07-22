package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.components.ObstacleTracker;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

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
    private TrackedSign toggleOutputOf = null;
    private ObstacleTracker.TrainObstacle trainObstacle = null;

    public MemberActionWaitOccupied(final double maxDistance, final long delay, final double launchDistance, BlockFace launchDirection, Double launchVelocity) {
        this.maxDistance = maxDistance;
        this.delay = delay;
        this.launchDistance = launchDistance;
        this.launchDirection = launchDirection;
        this.launchVelocity = launchVelocity;
    }

    /**
     * Toggles output state of a sign as part of this action
     * 
     * @param sign
     * @return this
     */
    public MemberActionWaitOccupied setToggleOutputOf(TrackedSign sign) {
        this.toggleOutputOf = sign;
        if (!sign.isRemoved()) {
            sign.setOutput(true);
        }
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
            if (this.toggleOutputOf != null && !toggleOutputOf.isRemoved()) {
                toggleOutputOf.setOutput(true);
            }
        } else {
            breakCode = true;
        }
    }

    public boolean handleOccupied() {
        for (ObstacleTracker.Obstacle obstacle : this.getGroup().findObstaclesAhead(this.maxDistance, true, false)) {
            if (obstacle instanceof ObstacleTracker.TrainObstacle) {
                this.trainObstacle = (ObstacleTracker.TrainObstacle) obstacle;
                return true;
            }
        }
        this.trainObstacle = null;
        return false;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        if (this.trainObstacle == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new TrainStatus.WaitingForTrain(
                this.trainObstacle.member, this.trainObstacle.fullDistance));
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
                if (this.toggleOutputOf != null && !toggleOutputOf.isRemoved()) {
                    if (this.delay > 0) {
                        toggleOutputOf.setOutput(false);
                        this.getGroup().getActions().addActionSetSignOutput(toggleOutputOf, false);
                    } else {
                        toggleOutputOf.setOutput(false);
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
        if (toggleOutputOf != null && !toggleOutputOf.isRemoved()) {
            toggleOutputOf.setOutput(false);
        }
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }
}
