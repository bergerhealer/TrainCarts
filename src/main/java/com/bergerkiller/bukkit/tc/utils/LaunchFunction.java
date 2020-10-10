package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Algorithms for calculating a desired launched distance over time to launch from one
 * velocity to another. Two modes can be used: fixed distance, or fixed time.
 */
public abstract class LaunchFunction {
    protected double vstart, vend;
    protected double vmin = 0.0;
    protected double vmax = Double.MAX_VALUE;
    protected int totalTime = 1;
    protected double dfactor = 1.0;

    /**
     * Gets the lower velocity limit of the launch function. By default this is 0.0.
     * 
     * @return minimum velocity
     */
    public final double getMinimumVelocity() {
        return this.vmin;
    }

    /**
     * Sets the lower velocity limit of the launch function. By default this is 0.0.
     * 
     * @param minimumVelocity to set to
     */
    public final void setMinimumVelocity(double minimumVelocity) {
        this.vmin = minimumVelocity;
        if (this.vstart < this.vmin) {
            this.vstart = this.vmin;
        }
        if (this.vend < this.vmin) {
            this.vend = this.vmin;
        }
    }

    /**
     * Gets the upper velocity limit of the launch function. By default this is MAX_VALUE.
     * 
     * @return maximum velocity
     */
    public final double getMaximumVelocity() {
        return this.vmax;
    }

    /**
     * Sets the upper velocity limit of the launch function. By default this is MAX_VALUE.
     * 
     * @param maximumVelocity to set to
     */
    public final void setMaximumVelocity(double maximumVelocity) {
        this.vmax = maximumVelocity;
        if (this.vstart > this.vmax) {
            this.vstart = this.vmax;
        }
        if (this.vend > this.vmax) {
            this.vend = this.vmax;
        }
    }

    /**
     * Sets the start and end velocities maintained by the algorithm.
     * 
     * @param startVelocity
     * @param endVelocity
     */
    public final void setVelocityRange(double startVelocity, double endVelocity) {
        this.vstart = startVelocity;
        this.vend = endVelocity;
        if (this.vstart < this.vmin) {
            this.vstart = this.vmin;
        } else if (this.vstart > this.vmax) {
            this.vstart = this.vmax;
        }
        if (this.vend < this.vmin) {
            this.vend = this.vmin;
        } else if (this.vend > this.vmax) {
            this.vend = this.vmax;
        }
    }

    /**
     * Sets the velocity enforced at the start of the launch
     * 
     * @param startVelocity to set to
     */
    public final void setStartVelocity(double startVelocity) {
        this.vstart = startVelocity;
        if (this.vstart < this.vmin) {
            this.vstart = this.vmin;
        } else if (this.vstart > this.vmax) {
            this.vstart = this.vmax;
        }
    }

    /**
     * Sets the velocity enforced at the end of the launch
     * 
     * @param endVelocity to set to
     */
    public final void setEndVelocity(double endVelocity) {
        this.vend = endVelocity;
        if (this.vend < this.vmin) {
            this.vend = this.vmin;
        } else if (this.vend > this.vmax) {
            this.vend = this.vmax;
        }
    }

    /**
     * Gets the velocity enforced at the start of the launch
     * 
     * @return start launch velocity
     */
    public final double getStartVelocity() {
        return this.vstart;
    }

    /**
     * Gets the velocity enforced at the end of the launch
     * 
     * @return end launch velocity
     */
    public final double getEndVelocity() {
        return this.vend;
    }

    /**
     * Configures this launch function, taking over the relevant configuration
     * for launching. The launch duration, distance or acceleration is set.
     * 
     * @param config
     */
    public final void configure(LauncherConfig config) {
        if (config.hasAcceleration()) {
            this.setAcceleration(config.getAcceleration());
        } else if (config.hasDuration()) {
            this.setTotalTime(config.getDuration());
        } else if (config.hasDistance()) {
            this.setTotalDistance(config.getDistance());
        } else {
            // Fallback: instant
            this.setInstantaneous();
        }
    }

    /**
     * Calibrates the algorithm to launch at a given acceleration
     * 
     * @param acceleration to calibrate in blocks/tick per tick
     */
    public final void setAcceleration(double acceleration) {
        // Detect when the acceleration is so high that we reach the target speed instantly
        // If so, set to an instantaneous launch. Also do this for invalid accelerations.
        double velocityDiff = Math.abs(this.vend - this.vstart);
        if (acceleration <= 0.0 || acceleration >= velocityDiff) {
            this.setInstantaneous();
            return;
        }

        // Approximate how many ticks it takes to go from vstart to vend
        // at this acceleration
        this.setTotalTime(MathUtil.floor(velocityDiff / acceleration));
    }

    /**
     * Calibrates the algorithm to launch a given distance
     * 
     * @param distance to calibrate
     */
    public final void setTotalDistance(double distance) {
        if (distance <= 0.0) {
            this.setInstantaneous();
            return;
        }

        // Try as many time ticks needed until we exceed this distance.
        // We calibrate the rest with a constant factor
        int time = 0;
        while (time < 10000) {
            this.setTotalTime(time);
            double currDistance = this.getDistance(time);
            if (currDistance >= distance) {
                this.dfactor = (distance / currDistance);
                break;
            } else {
                time++;
            }
        }
    }

    /**
     * Gets the total distance this algorithm will launch a train with the current settings
     * 
     * @return total distance
     */
    public final double getTotalDistance() {
        return this.getDistance(this.totalTime);
    }

    /**
     * Gets whether this launch function is instantaneous, meaning the time
     * it takes to change speeds is 0.
     * 
     * @return True if the launch is instantaneous
     */
    public final boolean isInstantaneous() {
        return this.totalTime == 0;
    }

    /**
     * Calibrates the algorithm to perform an instantaneous launch to the
     * target velocity.
     */
    public final void setInstantaneous() {
        this.totalTime = 0;
        this.dfactor = 0.0;
    }

    /**
     * Calibrates the algorithm to launch within the given amount of ticks.
     * {@link #getDistance()} will return the total distance launched when the same
     * tick value is specified.
     * 
     * @param ticks
     */
    public final void setTotalTime(int ticks) {
        this.totalTime = ticks;
        this.dfactor = 1.0;
    }

    /**
     * Gets the total amount of ticks this launch function takes to complete
     * 
     * @return total time
     */
    public final int getTotalTime() {
        return this.totalTime;
    }

    /**
     * Gets the expected distance launched at the given tick.
     * 
     * @param tick time
     * @return total distance launched
     */
    public double getDistance(int tick) {
        if (tick < 0) {
            tick = 0;
        } else if (tick >= this.totalTime) {
            tick = this.totalTime;
        }
        return calculateDistance(tick) * this.dfactor;
    }

    @Override
    public String toString() {
        return "{" +this.getClass().getSimpleName() + " vstart=" + vstart +
                " vend=" + vend + " time=" + this.getTotalTime() +
                " distance=" + this.getTotalDistance() + "}";
    }

    /**
     * Function to be implemented for calculating the distance at a given ticks timestamp.
     * Implementations must ensure that the distance will always increase when the ticks is increased.
     * 
     * @param ticks timestamp, where 0 is the start and {@link #totalTime} is the end
     * @return distance traveled at this ticks timestamp
     */
    protected abstract double calculateDistance(int ticks);

    /**
     * Launch function that uses a linear function to adjust velocity over time
     */
    public static class Linear extends LaunchFunction {
        @Override
        protected double calculateDistance(int ticks) {
            // This is the primitive of the linear equation:
            // v = vstart + (ticks / totalTime) * (vend - vstart)
            double m = ((vend - vstart) / (double) totalTime);
            return (ticks + 1) * (vstart + 0.5 * m * ticks);
        }
    }

    /**
     * Launch function that uses a bezier curve to adjust velocity over time
     */
    public static class Bezier extends LaunchFunction {
        @Override
        protected double calculateDistance(int ticks) {
            // This is the primitive of the easing bezier curve:
            // t = (ticks / totalTime)
            // v = vstart + (t * t) * (3.0 - 2.0 * t) * (vend - vstart)

            // Precompute these squares
            final double d1 = (double) ticks / (double) totalTime;
            final double d2 = (d1 * d1);
            final double d3 = (d1 * d2);
            final double d4 = (d2 * d2);

            // Approximated primitive of x^2
            double a = 0.0;
            a += d3 / 3.0 * (double) totalTime;
            a += d2 / 2.0;
            a += d1 / 6.0 / (double) totalTime;

            // Approximated primitive of x^3
            double b = 0.0;
            b += d4 / 4.0 * (double) totalTime;
            b += d3 / 2.0;
            b += d2 / 4.0 / (double) totalTime;

            return vstart * (ticks + 1) + (vend - vstart) * ((3.0 * a) - (2.0 * b));
        }
    }

}
