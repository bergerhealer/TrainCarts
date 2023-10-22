package com.bergerkiller.bukkit.tc.attachments.control.effect;

/**
 * A basic EffectLoop implementation for a sequence of effects played,
 * that optionally repeats (loops). Can be stopped with a stop method.
 * A callback must be implemented to process the actions that need to
 * be performed at every time step, and a method must be implemented that
 * returns the total duration of the loop.
 */
public abstract class SequenceEffectLoop implements EffectLoop {
    private long nanosElapsed = 0;

    /**
     * Advances this sequence from a previous timestamp in nanoseconds to
     * the new, current timestamp. If looping back around, will call
     * one advance of the previous timestamp to the loop duration,
     * followed by an advance from 0 and the remainder time.
     *
     * @param prevNanos Previous timestamp in nanoseconds
     * @param currNanos Current timestamp in nanoseconds
     * @return True to continue calling advance on this effect loop,
     *         False when the effect loop has finished.
     */
    public abstract boolean advance(long prevNanos, long currNanos);

    /**
     * Gets the amount of time that has elapsed playing this sequence. If looping,
     * this elapsed time goes up to that point then goes back to 0 to play again.
     *
     * @return Amount of nanoseconds into this sequence that have elapsed
     */
    public long nanosElapsed() {
        return nanosElapsed;
    }

    @Override
    public boolean advance(Time dt, Time duration, boolean loop) {
        long prev_time_nanos = this.nanosElapsed;
        long curr_time_nanos = prev_time_nanos + dt.nanos;

        if (duration.isZero()) {
            // No end duration set: simply continue playing until advance says to stop because there is no more
            // Some sequence effects, like the MIDI effect, will stop by itself when it has run out of notes
            this.nanosElapsed = curr_time_nanos;
            return advance(prev_time_nanos, curr_time_nanos);
        } else if (curr_time_nanos <= duration.nanos) {
            // Have not reached the end duration yet, so continue playing it
            // If loop is false, then we ignore what advance() says
            this.nanosElapsed = curr_time_nanos;
            return advance(prev_time_nanos, curr_time_nanos) || loop;
        } else if (loop) {
            // Loop is active, play the final bit of the sequence and loop back around
            // Because it has looped it will continue playing indefinitely until somebody
            // higher up the chain aborts playback.
            long remainder = curr_time_nanos - duration.nanos;
            this.nanosElapsed = remainder;
            advance(prev_time_nanos, duration.nanos);
            advance(0, remainder);
            return true;
        } else {
            // Reached the end of playback. Play any remainder amount of time.
            this.nanosElapsed = duration.nanos;
            advance(prev_time_nanos, duration.nanos);
            return false;
        }
    }
}
