package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.LaunchFunction;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

public class MemberActionLaunch extends MemberAction implements MovementAction {
    private static final double minVelocity = 0.001;
    private static final double minLaunchVelocity = 0.05;

    private double distanceoffset;
    private int timeoffset;
    private double targetvelocity;
    private double distance;
    private double lastVelocity;
    private double lastspeedlimit;
    private LauncherConfig config;
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
        this.config = config;
        this.targetvelocity = targetvelocity;
        this.timeoffset = 0;
        this.distanceoffset = 0.0;

        try {
            this.function = config.getFunction().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            this.function = new LaunchFunction.Linear();
        }

        // There seems to be a bug when distance/time is too short
        // It's unable to initiate the right launch direction then
        if (this.config.hasDistance() && this.config.getDistance() < 0.001) {
            this.config.setDistance(0.001);
        }
        if (this.config.hasDuration() && this.config.getDuration() < 1) {
            this.config.setDuration(1);
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
        LauncherConfig newConfig = this.config.clone();
        newConfig.setFunction(function);
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
        this.function.configure(this.config);
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    public double getTargetVelocity() {
        return this.targetvelocity;
    }

    public double getTargetDistance() {
        return this.config.getDistance();
    }

    protected void setTargetDistance(double distance) {
        this.config.setDistance(distance);
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
            if (this.config.hasDuration()) {
                this.config.setDuration(this.config.getDuration() - this.timeoffset);
            } else if (this.config.hasDistance()) {
                this.config.setDistance(this.config.getDistance() - this.distanceoffset);
            }
            this.function.configure(this.config);
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

        // Refresh distance every full tick
        if (this.isFullTick()) {
            this.distance += this.lastVelocity;
            // this.distance += this.getEntity().getMovedDistance();
        }

        return false;
    }

}
