package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/**
 * Plays {@link EffectLoop} instances that have been scheduled to start. Runs two
 * operating modes: synchronous and asynchronous.
 */
public class EffectLoopPlayer implements LibraryComponent  {
    private final TrainCarts plugin;
    private final Queue<EffectLoop> startPendingSync = new ConcurrentLinkedQueue<>();
    private final List<EffectLoop> syncRunning = new ArrayList<>();
    private final AsyncWorker asyncWorker = new AsyncWorker(1);

    public EffectLoopPlayer(TrainCarts plugin) {
        this.plugin = plugin;
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
        syncRunning.removeIf(e -> !e.advance(0.05));
    }

    /**
     * Starts playing the EffectLoop instance specified. Will stop playing when
     * the instance {@link EffectLoop#advance(double)} returns false.
     * This method can be called asynchronously.
     *
     * @param loop EffectLoop
     */
    public void start(EffectLoop loop) {
        loop = new EffectLoopErrorGuard(plugin, loop);
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
            final List<EffectLoop> asyncRunning = new ArrayList<>();
            long lastTime = System.nanoTime();
            while (!stopping) {
                LockSupport.parkNanos(INTERVAL - (System.nanoTime() - lastTime));

                // Measure time elapsed since previous loop
                long now = System.nanoTime();
                double elapsedTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                // Run all effect loops
                {
                    for (EffectLoop loop; (loop = startPendingAsync.poll()) != null;) {
                        asyncRunning.add(loop);
                    }
                    asyncRunning.removeIf(e -> !e.advance(elapsedTime));
                }
            }
        }
    }

    private static class EffectLoopErrorGuard implements EffectLoop {
        private final TrainCarts plugin;
        private final EffectLoop base;

        public EffectLoopErrorGuard(TrainCarts plugin, EffectLoop loop) {
            this.plugin = plugin;
            this.base = loop;
        }

        @Override
        public RunMode runMode() {
            return base.runMode();
        }

        @Override
        public boolean advance(double dt) {
            try {
                return base.advance(dt);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "An error occurred inside an effect loop", t);
                return false;
            }
        }
    }
}
