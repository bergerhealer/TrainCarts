package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/**
 * Provides {@link EffectLoop.Player} instances for playing effect loops.
 * Each Player is limited to a configurable limit of simultaneously playing
 * effect loops. EffectLoops can be scheduled to play synchronously (main thread)
 * or asynchronously (dedicated asynchronous thread).
 */
public class EffectLoopPlayerController implements LibraryComponent, TrainCarts.Provider {
    private final TrainCarts plugin;
    private final Queue<EffectLoop> startPendingSync = new ConcurrentLinkedQueue<>();
    private final List<EffectLoop> syncRunning = new ArrayList<>();
    private final AsyncWorker asyncWorker = new AsyncWorker(1);

    public EffectLoopPlayerController(TrainCarts plugin) {
        this.plugin = plugin;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return plugin;
    }

    /**
     * Creates a new EffectLoop player instance, with the number of concurrently playing
     * effect loops limited by TrainCarts configuration.
     *
     * @return EffectLoop Player
     */
    public EffectLoop.Player createPlayer() {
        return new EffectLoopPlayer();
    }

    /**
     * Creates a new EffectLoop player instance with a certain limit of concurrently playing
     * effect loops.
     *
     * @param limit Maximum number of concurrently playing effect loops. The TrainCarts configured
     *              limit is also in effect.
     * @return EffectLoop Player
     */
    public EffectLoop.Player createPlayer(int limit) {
        return new EffectLoopPlayer(limit);
    }

    @Override
    public void enable() {
        // Start running effect loops once the server finished starting up
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, asyncWorker::start);
    }

    @Override
    public void disable() {
        asyncWorker.stop();
        syncRunning.clear();
        startPendingSync.clear();
    }

    /**
     * Advances all {@link EffectLoop.RunMode#SYNCHRONOUS} mode started Effect Loops by
     * a single tick.
     */
    public void updateSync() {
        for (EffectLoop loop; (loop = startPendingSync.poll()) != null;) {
            syncRunning.add(loop);
        }
        syncRunning.removeIf(e -> !e.advance(EffectLoop.Time.ONE_TICK, EffectLoop.Time.ZERO, false));
    }

    /**
     * Starts playing the EffectLoop instance specified. Will stop playing when the instance
     * {@link EffectLoop#advance(EffectLoop.Time, EffectLoop.Time, boolean)}
     * returns false. This method can be called asynchronously.
     *
     * @param loop EffectLoop
     */
    private void schedule(EffectLoop loop) {
        if (loop.runMode() == EffectLoop.RunMode.SYNCHRONOUS) {
            startPendingSync.add(loop);
        } else {
            asyncWorker.schedule(loop);
        }
    }

    private static class AsyncWorker {
        private static final long INTERVAL = 50_000_000L;
        private final Queue<EffectLoop> startPendingAsync = new ConcurrentLinkedQueue<>();
        private final Thread effectLoopThread;
        private volatile boolean stopping = false;

        public AsyncWorker(int n) {
            effectLoopThread = new Thread(this::processAsync, "TrainCarts.EffectLoopPlayer" + n);
            effectLoopThread.setDaemon(true);
        }

        public void start() {
            stopping = false;
            effectLoopThread.start();
        }

        public void stop() {
            stopping = true;
            try {
                effectLoopThread.join(1000);
            } catch (InterruptedException e) {}
            startPendingAsync.clear();
        }

        public void schedule(EffectLoop loop) {
            startPendingAsync.add(loop);
        }

        public void processAsync() {
            final EffectLoop.Time zero_duration = EffectLoop.Time.ZERO;
            final List<EffectLoop> asyncRunning = new ArrayList<>();
            long lastTime = System.nanoTime();
            while (!stopping) {
                LockSupport.parkNanos(INTERVAL - (System.nanoTime() - lastTime));

                // Measure time elapsed since previous loop
                long now = System.nanoTime();
                EffectLoop.Time elapsedTime = EffectLoop.Time.nanos(now - lastTime);
                lastTime = now;

                // Run all effect loops
                {
                    for (EffectLoop loop; (loop = startPendingAsync.poll()) != null;) {
                        asyncRunning.add(loop);
                    }
                    asyncRunning.removeIf(e -> !e.advance(elapsedTime, zero_duration, false));
                }
            }
        }
    }

    /**
     * Plays Effect Loops in a safe way. Has functionality to limit the number of simultaneously
     * playing effect loops.
     */
    private class EffectLoopPlayer implements EffectLoop.Player, TrainCarts.Provider {
        private final Semaphore semaphore;

        public EffectLoopPlayer() {
            this.semaphore = new Semaphore(TCConfig.maxConcurrentEffectLoops);
        }

        public EffectLoopPlayer(int limit) {
            if (limit == 0) {
                this.semaphore = new Semaphore(1);
            } else if (limit < 0) {
                this.semaphore = new Semaphore(TCConfig.maxConcurrentEffectLoops);
            } else {
                this.semaphore = new Semaphore(Math.min(limit, TCConfig.maxConcurrentEffectLoops));
            }
        }

        @Override
        public TrainCarts getTrainCarts() {
            return plugin;
        }

        @Override
        public void play(EffectLoop loop) {
            if (semaphore.tryAcquire()) {
                schedule(new EffectLoopWrap(this, loop));
            }
        }

        public void onEffectLoopDone() {
            semaphore.release();
        }
    }

    /**
     * Wraps an EffectLoop to track its completion with a {@link EffectLoopPlayer}
     */
    private static class EffectLoopWrap implements EffectLoop {
        private final EffectLoopPlayer player;
        private final EffectLoop base;

        public EffectLoopWrap(EffectLoopPlayer player, EffectLoop loop) {
            this.player = player;
            this.base = loop;
        }

        @Override
        public RunMode runMode() {
            return base.runMode();
        }

        @Override
        public boolean advance(Time dt, Time duration, boolean loop) {
            try {
                if (base.advance(dt, duration, loop)) {
                    return true;
                }
            } catch (Throwable t) {
                player.getTrainCarts().getLogger().log(Level.SEVERE, "An error occurred inside an effect loop", t);
            }
            player.onEffectLoopDone();
            return false;
        }
    }
}
