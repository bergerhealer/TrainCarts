package com.bergerkiller.bukkit.tc.attachments.control.effect;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a callback after a delay has elapsed. This is returned by some
 * helper methods in {@link EffectLoop.Player}. It can be run right away
 * or cancelled so a new delayed task can be scheduled.
 */
public class DelayedEffectTask implements EffectLoop {
    private final Time delay;
    private final Runnable task;
    private final AtomicBoolean hasRun;
    private long elapsed;

    public DelayedEffectTask(Time delay, Runnable task) {
        if (delay == null) {
            throw new IllegalArgumentException("Delay is null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task is null");
        }

        this.delay = delay;
        this.task = task;
        this.hasRun = new AtomicBoolean(false);
        this.elapsed = 0L;
    }

    /**
     * Runs the task scheduled here right away, if it has not already been run.
     * This is multi-thread-safe: the task is guaranteed to only run once.
     * After this is called this delayed effect is cleaned up.
     */
    public void runNow() {
        if (hasRun.compareAndSet(false, true)) {
            task.run();
        }
        elapsed = delay.nanos;
    }

    /**
     * Cancels this delayed effect, NOT running the callback unless it was
     * already called due to an elapsed delay. This delayed effect is then
     * cleaned up (stops playing).
     */
    public void cancel() {
        elapsed = delay.nanos;
        hasRun.set(true);
    }

    @Override
    public boolean advance(Time dt, Time duration, boolean loop) {
        long newElapsed = this.elapsed + dt.nanos;
        if (newElapsed >= delay.nanos) {
            runNow();
            return false; // Stop the effect loop
        } else {
            this.elapsed = newElapsed;
            return true; // Keep running the effect loop until it elapses
        }
    }

    @Override
    public void resetToBeginning() {
        this.elapsed = 0L;
        this.hasRun.set(false);
    }
}
