package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
    private BlockFace direction;
    private boolean directionWasCorrected;

    public MemberActionLaunchDirection() {
        this.direction = BlockFace.SELF;
        this.directionWasCorrected = false;
    }

    /**
     * Deprecated: use initDistance/initTime instead, combined with the default constructor
     */
    @Deprecated
    public MemberActionLaunchDirection(double targetdistance, double targetvelocity, final BlockFace direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity, direction);
    }

    public void init(LauncherConfig config, double targetvelocity, double targetspeedlimit, BlockFace direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity, targetspeedlimit);
    }

    public void init(LauncherConfig config, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity);
    }

    public void initTime(int timeTicks, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initTime(timeTicks, targetvelocity);
    }

    public void initDistance(double targetdistance, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity);
    }

    public void setDirection(BlockFace direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Direction is null");
        }

        this.direction = direction;
    }

    @Override
    public boolean update() {
        boolean success = super.update();

        // Once speed of the train increases to the point it is moving, we
        // can correct direction to make sure it leads into the direction that
        // was configured. We then stop checking. The direction should only change
        // at the very beginning of the launch. We do this also when success is
        // true right away, to properly handle instantaneous launches. Note that
        // the member direction property can't be used for this, as it isn't updated
        // when the speed is changed.
        if (!this.directionWasCorrected) {
            Vector vel = this.getMember().getEntity().getVelocity();
            if (vel.lengthSquared() > 1e-20) {
                this.directionWasCorrected = true;
                if (vel.dot(FaceUtil.faceToVector(this.direction)) < 0.0) {
                    this.getGroup().reverse();
                }
            }
        }

        return success;
    }
}
