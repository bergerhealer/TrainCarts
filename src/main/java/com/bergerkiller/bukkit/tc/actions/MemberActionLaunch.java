package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.LaunchFunction;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

public class MemberActionLaunch extends MemberAction implements MovementAction {
    private static final double minVelocity = 0.001;
    private static final double minLaunchVelocity = 0.05;

    private int targettime;
    private double targetvelocity;
    private double targetdistance;
    private double distance;
    private double lastVelocity;
    private LaunchFunction function;

    public MemberActionLaunch() {
        this.setFunction(LauncherConfig.createDefault().getFunction());
    }

    /**
     * Sets the type of launch function to use.
     * This should be the first function called during initialization.
     * 
     * @param function class to use
     */
    public void setFunction(Class<? extends LaunchFunction> function) {
        try {
            this.function = function.newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            this.function = new LaunchFunction.Linear();
        }
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
        this.targetvelocity = targetvelocity;
        this.targetdistance = -1.0;
        this.targettime = timeTicks;
        this.distance = 0;
        this.lastVelocity = 0.0;
    }

    public void initDistance(double targetdistance, double targetvelocity) {
        this.targetvelocity = targetvelocity;
        this.targetdistance = targetdistance;
        this.distance = 0;
        this.lastVelocity = 0.0;
        this.targettime = -1;
    }

    @Override
    public void start() {
        this.lastVelocity = 0.0;
        this.function.setMinimumVelocity(minVelocity);
        this.function.setMaximumVelocity(this.getEntity().getMaxSpeed());
        this.function.setVelocityRange(this.getMember().getForce(), this.targetvelocity);
        if (this.function.getStartVelocity() < minLaunchVelocity && this.function.getEndVelocity() < minLaunchVelocity) {
            this.function.setStartVelocity(minLaunchVelocity);
        }

        if (this.targettime >= 0) {
            this.function.setTotalTime(this.targettime);
        } else {
            // The world may never know why this is needed for trains >1 in size
            // ...if you know, let me know, k?
            if (getGroup().size() > 1) {
                this.targetdistance += getMember().getEntity().getMovedDistance();
            }

            this.function.setTotalDistance(this.targetdistance);
        }
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    public double getTargetVelocity() {
        return this.function.getEndVelocity();
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

        // Did any of the carts in the group stop?
        if (this.distance != 0) {
            for (MinecartMember<?> mm : this.getGroup()) {
                if (mm.getForce() < minVelocity && this.lastVelocity > (10.0 * minVelocity)) {
                    return true;
                }
            }
        }

        // Check if we completed the function
        if (this.elapsedTicks() > this.function.getTotalTime()) {
            // Finish with the desired end-velocity
            this.getGroup().setForwardForce(this.targetvelocity);
            return true;
        }

        // Update velocity based on the distance difference
        this.lastVelocity = (this.function.getDistance(this.elapsedTicks()) - this.distance);
        if (this.lastVelocity < 0.0) {
            this.lastVelocity = 0.0; // just in case
        }
        this.getGroup().setForwardForce(this.lastVelocity);
        this.distance += this.getEntity().getMovedDistance();
        return false;
    }
}
