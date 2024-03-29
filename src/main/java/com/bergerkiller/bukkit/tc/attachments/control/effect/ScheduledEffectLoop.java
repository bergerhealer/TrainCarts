package com.bergerkiller.bukkit.tc.attachments.control.effect;

/**
 * Runs effects that are scheduled to be played between a start and end timestamp.
 * Does not keep track of time itself, which must be done externally.
 */
public interface ScheduledEffectLoop {
    /**
     * Special ScheduledEffectLoop constant that plays nothing. Always returns
     * false from advance()
     */
    ScheduledEffectLoop NONE = (prevNanos, currNanos) -> false;

    /**
     * Advances from a previous timestamp in nanoseconds to
     * the new, current timestamp. If looping back around, will call
     * one advance of the previous timestamp to the loop duration,
     * followed by an advance from 0 and the remainder time.
     *
     * @param prevNanos Previous timestamp in nanoseconds
     * @param currNanos Current timestamp in nanoseconds
     * @return True if there are additional actions scheduled to run
     *         after the current nanosecond timestamp. False if not,
     *         and no further advance() has to be called.
     */
    boolean advance(long prevNanos, long currNanos);

    /**
     * Wraps this ScheduledEffectLoop as an EffectLoop, allowing it to be played
     * with a set duration or looped.
     *
     * @return EffectLoop advancing this ScheduledEffectLoop
     */
    default SequentialEffectLoop asEffectLoop() {
        return asEffectLoop(null);
    }

    /**
     * Wraps this ScheduledEffectLoop as an EffectLoop, allowing it to be played
     * with a set duration or looped.
     *
     * @param overrideDuration If non-null, sets a duration at which to cut off
     * @return EffectLoop advancing this ScheduledEffectLoop
     */
    default SequentialEffectLoop asEffectLoop(EffectLoop.Time overrideDuration) {
        return new SequentialEffectLoop() {
            private long nanosElapsed = 0;

            @Override
            public boolean advance(Time dt, Time duration, boolean loop) {
                if (overrideDuration != null) {
                    duration = overrideDuration;
                }

                long prev_time_nanos = this.nanosElapsed;
                long curr_time_nanos = prev_time_nanos + dt.nanos;

                if (duration.isZero()) {
                    // No end duration set: simply continue playing until advance says to stop because there is no more
                    // Some sequence effects, like the MIDI effect, will stop by itself when it has run out of notes
                    this.nanosElapsed = curr_time_nanos;
                    return ScheduledEffectLoop.this.advance(prev_time_nanos, curr_time_nanos);
                } else if (curr_time_nanos <= duration.nanos) {
                    // Have not reached the end duration yet, so continue playing it
                    // If loop is false, then we ignore what advance() says
                    this.nanosElapsed = curr_time_nanos;
                    return ScheduledEffectLoop.this.advance(prev_time_nanos, curr_time_nanos) || loop;
                } else if (loop) {
                    // Loop is active, play the final bit of the sequence and loop back around
                    // Because it has looped it will continue playing indefinitely until somebody
                    // higher up the chain aborts playback.
                    long remainder = curr_time_nanos - duration.nanos;
                    this.nanosElapsed = remainder;
                    ScheduledEffectLoop.this.advance(prev_time_nanos, duration.nanos);
                    ScheduledEffectLoop.this.advance(0, remainder);
                    return true;
                } else {
                    // Reached the end of playback. Play any remainder amount of time.
                    this.nanosElapsed = duration.nanos;
                    ScheduledEffectLoop.this.advance(prev_time_nanos, duration.nanos);
                    return false;
                }
            }

            @Override
            public long nanosElapsed() {
                return nanosElapsed;
            }

            @Override
            public void resetToBeginning() {
                nanosElapsed = 0;
            }
        };
    }

    /**
     * Effect loop with sequential playback. Includes a method to get the current
     * play position.
     */
    interface SequentialEffectLoop extends EffectLoop {
        /**
         * Gets the amount of nanoseconds that this effect loop has been played for
         *
         * @return Elapsed time in nanoseconds
         */
        long nanosElapsed();
    }
}
