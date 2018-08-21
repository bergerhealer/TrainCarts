package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.Effect;
import com.bergerkiller.bukkit.tc.utils.LaunchFunction;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;
import org.bukkit.entity.Player;

public class MemberActionLaunch extends MemberAction implements MovementAction {
    private static final double minVelocity = 0.001;
    private static final double minLaunchVelocity = 0.05;

    private double distanceoffset;
    private int timeoffset;
    private int targettime;
    private double targetvelocity;
    private double targetdistance;
    private double distance;
    private double lastVelocity;
    private double lastspeedlimit;
    private LaunchFunction function;

    public MemberActionLaunch() {
        this.init(LauncherConfig.createDefault(), 0.0);
    }

    /**
     * Sets the launch function and launch duration or time based on Launch Config.
     * 
     * @param config to apply
     * @param targetvelocity goal final speed
     */
    public void init(LauncherConfig config, double targetvelocity) {
        this.targetvelocity = targetvelocity;
        this.timeoffset = 0;
        this.distanceoffset = 0.0;

        try {
            this.function = config.getFunction().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            this.function = new LaunchFunction.Linear();
        }

        if (config.hasDuration()) {
            this.targetdistance = -1.0;
            this.targettime = config.getDuration();
        } else if (config.hasDistance()) {
            this.targetdistance = config.getDistance();
            this.targettime = -1;
        } else {
            // No time, no duration.
            this.targetdistance = -1.0;
            this.targettime = 0;
        }

        // There seems to be a bug when distance/time is too short
        // It's unable to initiate the right launch direction then
        if (this.targetdistance >= 0.0 && this.targetdistance < 0.001) {
            this.targetdistance = 0.001;
        }
        if (this.targettime >= 0 && this.targettime < 1) {
            this.targettime = 1;
        }

        this.distance = 0;
        this.lastVelocity = 0.0;
    }

    /**
     * Deprecated: please use {@link #initDistance(ticks, vel)} or {@link #initTime(time, vel)} instead,
     * combined with the default constructor.
     */
    @Deprecated
    public MemberActionLaunch(double targetdistance, double targetvelocity) {
        this();
        this.initDistance(targetdistance, targetvelocity);
    }

    public void initTime(int timeTicks, double targetvelocity) {
        LauncherConfig newConfig = new LauncherConfig();
        newConfig.setFunction(this.function.getClass());
        newConfig.setDuration(timeTicks);
        this.init(newConfig, targetvelocity);
    }

    public void initDistance(double targetdistance, double targetvelocity) {
        LauncherConfig newConfig = new LauncherConfig();
        newConfig.setFunction(this.function.getClass());
        newConfig.setDistance(targetdistance);
        this.init(newConfig, targetvelocity);
    }

    /**
     * Sets the type of launch function to use.
     * This should be the first function called during initialization.
     * 
     * @param function class to use
     */
    public void setFunction(Class<? extends LaunchFunction> function) {
        LauncherConfig newConfig = new LauncherConfig();
        newConfig.setFunction(function);
        if (this.targettime > 0) {
            newConfig.setDuration(this.targettime);
        } else {
            newConfig.setDistance(this.targetdistance);
        }
        this.init(newConfig, this.targetvelocity);
    }

    @Override
    public void start() {
        this.lastVelocity = this.getMember().getRealSpeed();
        this.lastspeedlimit = this.getGroup().getProperties().getSpeedLimit();
        this.function.setMinimumVelocity(minVelocity);
        this.function.setMaximumVelocity(this.lastspeedlimit);
        this.function.setVelocityRange(this.lastVelocity, this.targetvelocity);
        if (this.function.getStartVelocity() < minLaunchVelocity && this.function.getEndVelocity() < minLaunchVelocity) {
            this.function.setStartVelocity(minLaunchVelocity);
        }

        for(MinecartMember<?> member : getGroup()) {
            if(this.lastVelocity == 0) {
                Effect effect = new Effect();
                effect.parseEffect(member.getProperties().getDriveSound());
                effect.volume = 100;
                for(Player p : member.getEntity().getPlayerPassengers()) {
                    effect.play(p);
                }
                effect.volume = 2;
                effect.play(member.getEntity().getLocation());

            }
        }

        if (this.targettime >= 0) {
            this.function.setTotalTime(this.targettime);
        } else {
            // The world may never know why this is needed for trains >1 in size
            // ...if you know, let me know, k?
            // Now we know! It's because using getMovedDistance() does not show true applied velocities.
            //if (getGroup().size() > 1) {
            //    this.targetdistance += getMember().getEntity().getMovedDistance();
            //}

            this.function.setTotalDistance(this.targetdistance);
        }
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    public double getTargetVelocity() {
        return this.targetvelocity;
    }

    public double getTargetDistance() {
        return this.targetdistance;
    }

    protected void setTargetDistance(double distance) {
        this.targetdistance = distance;
    }

    public double getDistance() {
        return this.distance;
    }

    @Override
    public boolean update() {
        // Abort when derailed. We do permit vertical 'air-launching'
        if (this.getMember().isDerailed() && !this.getMember().isMovingVerticalOnly()) {
            return true;
        }

        // Did the maximum speed of the train change? If so we have to recalibrate the algorithm.
        if (this.lastspeedlimit != this.getGroup().getProperties().getSpeedLimit()) {
            this.lastspeedlimit = this.getGroup().getProperties().getSpeedLimit();
            this.function.setMaximumVelocity(this.lastspeedlimit);
            this.function.setVelocityRange(this.lastVelocity, this.targetvelocity);
            this.timeoffset = this.elapsedTicks();
            this.distanceoffset = this.distance;

            if (this.targettime > 0) {
                // Launch from current speed to new speed limit in the time remaining
                this.function.setTotalTime(this.targettime - this.timeoffset);
            } else {
                // Launch from current speed to new speed limit for the distance remaining
                this.function.setTotalDistance(this.targetdistance - this.distanceoffset);
            }
        }

        // Did any of the carts in the group stop?
        if (this.distance != 0) {
            for (MinecartMember<?> mm : this.getGroup()) {
                if (mm.getRealSpeed() < minVelocity && this.lastVelocity > (10.0 * minVelocity)) {
                    return true;
                }
            }
        }

        // Check if we completed the function
        int time = this.elapsedTicks() - this.timeoffset;
        if (time > this.function.getTotalTime()) {
            // Finish with the desired end-velocity
            this.getGroup().setForwardForce(this.targetvelocity * this.getGroup().getUpdateSpeedFactor());
            return true;
        }

        // Update velocity based on the distance difference
        this.lastVelocity = (this.function.getDistance(time) - this.distance + this.distanceoffset);
        this.getGroup().setForwardForce(this.lastVelocity * this.getGroup().getUpdateSpeedFactor());

        if (this.getGroup().isLastUpdateStep()) {
            this.distance += this.lastVelocity;
            // this.distance += this.getEntity().getMovedDistance();
        }
        return false;
    }

}
