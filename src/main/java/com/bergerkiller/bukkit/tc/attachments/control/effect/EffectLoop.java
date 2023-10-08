package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.controller.global.EffectLoopPlayer;

/**
 * Plays a sequence of effects over time. Can be registered into the
 * {@link EffectLoopPlayer} so it is played back automatically.
 * Runs asynchronously.
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
        public boolean advance(double dt) {
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
     * @param dt Amount of time to advance in seconds
     * @return True to continue calling advance on this effect loop,
     *         False when the effect loop has finished.
     */
    boolean advance(double dt);

    /**
     * Changes the behavior of {@link #advance(double)} using a modifier function.
     *
     * @param modifier Advance behavior modifier
     * @return new EffectLoop that uses the modifier
     */
    default EffectLoop withAdvance(AdvanceModifier modifier) {
        return new EffectLoopAdvanceModifier(this, modifier);
    }

    /**
     * Changes the speed of {@link #advance(double)}. If speed is (close to) zero,
     * returns {@link #NONE} as nothing would play in that case.
     *
     * @param speed Speed Modifier
     * @return new EffectLoop that uses the speed modifier
     * @see #withAdvance(AdvanceModifier)
     */
    default EffectLoop withSpeed(double speed) {
        if (speed < 1e-8) {
            return NONE;
        } else {
            final double mult = 1.0 / speed;
            return withAdvance((base, dt) -> base.advance(dt * mult));
        }
    }

    /**
     * Intercepts {@link #advance(double)} and allows for custom modifications,
     * like speed changes or early termination.
     */
    @FunctionalInterface
    interface AdvanceModifier {
        boolean advance(EffectLoop base, double dt);
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
}
