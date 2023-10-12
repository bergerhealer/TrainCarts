package com.bergerkiller.bukkit.tc.attachments.control.effect;

/**
 * A basic EffectLoop implementation for a sequence of effects played,
 * that optionally repeats (loops). Can be stopped with a stop method.
 * A callback must be implemented to process the actions that need to
 * be performed at every time step, and a method must be implemented that
 * returns the total duration of the loop.
 */
public abstract class SequenceEffectLoop implements EffectLoop {
    private RunMode runMode = RunMode.ASYNCHRONOUS;
    private boolean stopped = false;
    private boolean looped = false;
    private long nanosElapsed = 0;

    /**
     * Gets the total duration of this effect loop sequence in nanoseconds
     *
     * @return Nanoseconds duration
     */
    public abstract long durationNanos();

    /**
     * Advances this sequence from a previous timestamp in nanoseconds to
     * the new, current timestamp. If looping back around, will call
     * one advance of the previous timestamp to {@link #durationNanos()},
     * followed by an advance from 0 and the remainder time.<br>
     * <br>
     * Is not called if {@link #stop()} was called.
     *
     * @param prevNanos Previous timestamp in nanoseconds
     * @param currNanos Current timestamp in nanoseconds
     * @return True to continue calling advance on this effect loop,
     *         False when the effect loop has finished.
     */
    public abstract boolean advance(long prevNanos, long currNanos);

    /**
     * Stops this EffectLoop. Future calls to {@link #advance(double)} will
     * return false.
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Gets whether this effect loop will repeat when reaching the end
     *
     * @return True if looped
     */
    public boolean isLooped() {
        return looped;
    }

    /**
     * Sets whether this effect loop will repeat when reaching the end
     *
     * @param looped Whether it is looped
     */
    public void setLooped(boolean looped) {
        this.looped = looped;
    }

    @Override
    public RunMode runMode() {
        return runMode;
    }

    /**
     * Sets a different run mode. Has no effect if the loop is already playing
     *
     * @param runMode New run mode
     */
    public void setRunMode(RunMode runMode) {
        this.runMode = runMode;
    }

    @Override
    public boolean advance(double dt) {
        if (stopped) {
            return false;
        }

        long dt_nanos = toNanos(dt);
        long prev_time_nanos = this.nanosElapsed;
        long curr_time_nanos = prev_time_nanos + dt_nanos;
        long duration_nanos = durationNanos();

        if (curr_time_nanos <= duration_nanos) {
            this.nanosElapsed = curr_time_nanos;
            return advance(prev_time_nanos, curr_time_nanos);
        } else if (isLooped()) {
            long remainder = curr_time_nanos - duration_nanos;
            this.nanosElapsed = remainder;
            return advance(prev_time_nanos, duration_nanos) && advance(0, remainder);
        } else {
            this.nanosElapsed = duration_nanos;
            advance(prev_time_nanos, duration_nanos);
            return false;
        }
    }

    /**
     * Converts a time duration in seconds into nanoseconds
     *
     * @param seconds Seconds duration
     * @return Nanoseconds
     */
    public static long toNanos(double seconds) {
        return (long) (seconds * 1_000_000_000.0);
    }
}
