package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
    private BlockFace direction;

    public MemberActionLaunchDirection() {
        this.direction = BlockFace.SELF;
    }

    /**
     * Deprecated: use initDistance/initTime instead, combined with the default constructor
     */
    @Deprecated
    public MemberActionLaunchDirection(double targetdistance, double targetvelocity, final BlockFace direction) {
        this.initDistance(targetdistance, targetvelocity, direction);
    }

    public void init(LauncherConfig config, double targetvelocity, BlockFace direction) {
        this.init(config, targetvelocity);
        this.direction = direction;
    }

    public void initTime(int timeTicks, double targetvelocity, BlockFace direction) {
        this.initTime(timeTicks, targetvelocity);
        this.direction = direction;
    }

    public void initDistance(double targetdistance, double targetvelocity, BlockFace direction) {
        this.initDistance(targetdistance, targetvelocity);
        this.direction = direction;
    }

    public void setDirection(BlockFace direction) {
        this.direction = direction;
    }

    @Override
    public boolean update() {
        if (super.update()) {
            return true;
        }
        if (super.getDistance() < 1 && this.getMember().isDirectionTo(this.direction.getOppositeFace())) {
            this.getGroup().reverse();
        }
        return false;
    }
}
