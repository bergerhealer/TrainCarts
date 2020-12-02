package com.bergerkiller.bukkit.tc.properties.standard.type;

/**
 * Immutable class storing all the train wait behavior options.
 * This defines how much distance trains keep to other trains and mutex zones
 * up ahead, how fast they accelerate and decelerate, and how long
 * they wait once fully stopped.
 */
public final class WaitOptions {
    public static final WaitOptions DEFAULT = new WaitOptions(0.0, 0.0, 0.0, 0.0);

    private final double distance;
    private final double delay;
    private final double deceleration;
    private final double acceleration;

    private WaitOptions(double distance, double delay, double acceleration, double deceleration) {
        this.distance = distance;
        this.delay = delay;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
    }

    /**
     * The distance to keep between the train and other trains up ahead.
     * When above 0.0 the train will stop for other trains and mutex
     * zones. When at 0.0 or below, the train will only stop for mutex
     * zones.
     * 
     * @return The distance in blocks to keep between the train and obstacles ahead
     */
    public double distance() {
        return this.distance;
    }

    /**
     * The number of seconds to wait when the train reaches a standstill because of waiting.
     * 
     * @return delay in seconds between a full stop and moving again
     */
    public double delay() {
        return this.delay;
    }

    /**
     * The de-acceleration in <i>blocks/tick<sup>2</sup></i> at which the train slows down
     * when having to wait for another train. Instant when 0.0.
     * 
     * @return deceleration
     */
    public double deceleration() {
        return this.deceleration;
    }

    /**
     * The acceleration in <i>blocks/tick<sup>2</sup></i> at which the train speeds up
     * again when the obstacle ahead clears. Instant when 0.0.
     * 
     * @return acceleration
     */
    public double acceleration() {
        return this.acceleration;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this.distance);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof WaitOptions) {
            WaitOptions other = (WaitOptions) o;
            return this.distance == other.distance &&
                   this.delay == other.delay &&
                   this.acceleration == other.acceleration &&
                   this.deceleration == other.deceleration;
        } else {
            return false;
        }
    }

    /**
     * Creates new WaitOptions with a configured distance to keep,
     * and all other options left to the defaults.
     * 
     * @param distance See {@link #distance()}
     * @return new WaitOptions
     */
    public static WaitOptions create(double distance) {
        return create(distance, 0.0, 0.0, 0.0);
    }

    /**
     * Creates new WaitOptions using a given configuration
     * 
     * @param distance See {@link #distance()}
     * @param delay See {@link #delay()}
     * @param acceleration See {@link #acceleration()}
     * @param deceleration See {@link #deceleration()}
     * @return new WaitOptions
     */
    public static WaitOptions create(double distance, double delay, double acceleration, double deceleration) {
        return new WaitOptions(
                Math.max(0.0, distance),
                Math.max(0.0, delay),
                Math.max(0.0, acceleration),
                Math.max(0.0, deceleration)
        );
    }
}
