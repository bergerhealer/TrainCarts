package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Plays a sequence of effects over time. Can be played by the  {@link EffectLoop.Player}.
 * Retrieve one using {@link TrainCarts#createEffectLoopPlayer()}. Every player instance
 * is limited to a certain amount of concurrently playing effect loops.
 */
public interface EffectLoop {
    /**
     * Special EffectLoop constant that plays nothing. Will be removed right
     * after it is scheduled.
     */
    EffectLoop NONE = new EffectLoop() {
        @Override
        public RunMode runMode() {
            return RunMode.SYNCHRONOUS;
        }

        @Override
        public boolean advance(Time dt, Time duration, boolean loop) {
            return false;
        }
    };

    /**
     * Gets the running mode of this EffectLoop. This controls whether the loop
     * is run asynchronously or synchronously on the main thread.
     *
     * @return RunMode
     */
    RunMode runMode();

    /**
     * Advances the effect loop by a certain amount of time in seconds.
     * Returns true if the effect loop should remain active, telling the caller
     * it should call it again next time period. Returns false if the effect
     * loop has finished and the caller can discard it.
     *
     * @param dt Amount of time to advance
     * @param duration Configures the duration playback should take.
     *                 If set to non-zero, will limit the range that is played.
     *                 If loop is also true, this defines at what point the
     *                 effect loops. Is by default zero.
     * @param loop Whether the effect should loop while playing.
     *             Is by default False.
     * @return True to continue calling advance on this effect loop,
     *         False when the effect loop has finished.
     */
    boolean advance(Time dt, Time duration, boolean loop);

    /**
     * Changes the behavior of {@link #advance(Time, Time, boolean)} using a modifier function.
     *
     * @param modifier Advance behavior modifier
     * @return new EffectLoop that uses the modifier
     */
    default EffectLoop withAdvance(AdvanceModifier modifier) {
        return new EffectLoopAdvanceModifier(this, modifier);
    }

    /**
     * Changes the speed of {@link #advance(Time, Time, boolean)}. If speed is (close to) zero,
     * returns {@link #NONE} as nothing would play in that case.
     *
     * @param speed Speed Modifier
     * @return new EffectLoop that uses the speed modifier
     * @see #withAdvance(AdvanceModifier)
     */
    default EffectLoop withSpeed(final double speed) {
        if (speed < 1e-8) {
            return NONE;
        } else if (speed == 1.0) {
            return this;
        } else {
            return withAdvance((base, dt, duration, loop) -> base.advance(dt.multiply(speed), duration.multiply(speed), loop));
        }
    }

    /**
     * Intercepts {@link #advance(Time, Time, boolean)} and allows for custom modifications,
     * like speed changes or early termination.
     */
    @FunctionalInterface
    interface AdvanceModifier {
        boolean advance(EffectLoop base, Time dt, Time duration, boolean loop);
    }

    /**
     * Run Mode of the EffectLoop.
     */
    enum RunMode {
        /** EffectLoop runs on the main thread, during the time attachments are updated */
        SYNCHRONOUS,
        /** EffectLoop runs asynchronously on a dedicated Sound Loop thread */
        ASYNCHRONOUS
    }

    /**
     * Plays EffectLoops
     */
    @FunctionalInterface
    interface Player {
        /**
         * Starts playing the EffectLoop instance specified. Will stop playing when
         * the instance {@link EffectLoop#advance(Time, Time, boolean)} returns false.
         * This method may be called asynchronously, even if the run mode of the
         * loop is synchronous.
         *
         * @param loop EffectLoop to play
         */
        void play(EffectLoop loop);
    }

    /**
     * A unit of elapsed time. Represents the time both as seconds (double) as well as
     * the number of nanoseconds (long).
     */
    class Time {
        public static final Time ZERO = new Time(0L) {
            @Override
            public Time multiply(double factor) {
                return this;
            }
        };
        public static final Time ONE_TICK = nanos(50000000L);
        public final double seconds;
        public final long nanos;

        public static Time seconds(double seconds) {
            return new Time(seconds);
        }

        public static Time nanos(long nanoSeconds) {
            return new Time(nanoSeconds);
        }

        private Time(double seconds) {
            this.seconds = seconds;
            this.nanos = (long) (seconds * 1_000_000_000.0);
        }

        private Time(long nanos) {
            this.seconds = (double) nanos / 1_000_000_000.0;
            this.nanos = nanos;
        }

        /**
         * Gets whether this is a Zero Duration
         *
         * @return True if zero
         */
        public boolean isZero() {
            return nanos == 0L;
        }

        /**
         * Multiplies this Time with a factor
         *
         * @param factor Factor
         * @return Updated Time
         */
        public Time multiply(double factor) {
            return seconds(seconds * factor);
        }

        /**
         * Multiplies this Time with a factor
         *
         * @param factor Factor
         * @return Updated Time
         */
        public Time multiply(int factor) {
            return nanos(nanos * factor);
        }

        /**
         * Adds a step duration to this duration
         *
         * @param step Step duration to add
         * @param count Number of times to add. Negative to subtract.
         * @return Updated Time
         */
        public Time add(Time step, int count) {
            if (count == 0) {
                return this;
            } else {
                return nanos(this.nanos + step.nanos * count);
            }
        }

        /**
         * Converts a time duration in seconds into nanoseconds
         *
         * @param seconds Seconds duration
         * @return Nanoseconds
         */
        public static long secondsToNanos(double seconds) {
            return (long) (seconds * 1_000_000_000.0);
        }

        /**
         * Divides this time value by a divisor, rounds the result (rather than flooring)
         *
         * @param divisor Divisor time value. Must not be 0.
         * @return Rounded value of dividing this time by the divisor specified
         */
        public int roundDiv(Time divisor) {
            if (divisor.isZero()) {
                throw new ArithmeticException("Divisor is zero");
            }
            long division = nanos / divisor.nanos;
            long remainder = Math.abs(nanos % divisor.nanos);
            if (2 * remainder >= Math.abs(divisor.nanos)) {
                division += ((nanos < 0) ^ (divisor.nanos < 0)) ? -1 : 1;
            }
            return (int) division;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Time) {
                return ((Time) o).nanos == nanos;
            } else {
                return false;
            }
        }
    }
}
