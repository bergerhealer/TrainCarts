package com.bergerkiller.bukkit.tc.controller.global;

import java.util.Collection;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.helper.AttachmentUpdateTransformHelper;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Handles everything to do with updating trains:
 * <ul>
 * <li>Ensures all trains update, even when entity ticks are skipped</li>
 * <li>Handles the logic of setting a custom tick divider, or disabling
 * train updates entirely (debug)</li>
 * <li>Tracks the server tick rate for the train realtime physics behavior</li>
 * </ul>
 */
public class TrainUpdateController {
    private final TrainCarts plugin;
    private int tickUpdateDivider = 1; // allows slowing down of minecart physics globally (debugging!)
    private int tickUpdateNow = 0; // forces update ticks
    private boolean ticking = true; // whether train tick updates are enabled
    private double realtimeFactor = 1.0; // Factor to correct for server lag this current tick
    private TrainUpdateTask updateTask = null;
    private TrainNetworkSyncTask networkSyncTask = null;
    private AttachmentUpdateTransformHelper updateTransformHelper;

    public TrainUpdateController(TrainCarts plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets whether trains should perform per-tick physics this current
     * server tick. If a divider is set, then some ticks might be skipped.
     *
     * @return True if ticking, False if not
     */
    public boolean isTicking() {
        return this.ticking;
    }

    /**
     * Gets a factor multiplier to use when performing movement physics,
     * that accounts for current tick server lag. If the server is running
     * more slowly, a value &gt; 1 is returned to account for this. If it is
     * running faster, a value &lt; 1 is returned. On a stable server, the value
     * should stay around 1.0.
     *
     * @return realtime physics movement factor
     */
    public double getRealtimeFactor() {
        return realtimeFactor;
    }

    public int getTickDivider() {
        return this.tickUpdateDivider;
    }

    public void setTickDivider(int divider) {
        this.tickUpdateDivider = divider;
    }

    public void step(int number) {
        this.tickUpdateNow += number;
    }

    public void preEnable() {
        this.updateTask = new TrainUpdateTask(this.plugin);
        this.updateTask.start(1, 1);

        this.networkSyncTask = new TrainNetworkSyncTask();
        this.networkSyncTask.start(1, 1);

        this.updateTransformHelper = AttachmentUpdateTransformHelper.create(1);

        // Note: just for testing, is normally disabled
        //new DebugArtificialLag(this.plugin).start(1, 1);
    }

    public void postEnable() {
        this.updateTransformHelper = AttachmentUpdateTransformHelper.create(TCConfig.attachmentTransformParallelism);
    }

    public void disable() {
        Task.stop(this.updateTask);
        this.updateTask = null;
        Task.stop(this.networkSyncTask);
        this.networkSyncTask = null;
    }

    public void computeAttachmentTransform(Attachment attachment, Matrix4x4 initialTransform) {
        updateTransformHelper.startAndFinish(attachment, initialTransform);
    }

    /**
     * Updates the attachment positions, without performing any relative movement updates or updating
     * attachment onTick().
     *
     * @param groups Groups to sync positions of
     */
    public void syncPositions(Collection<MinecartGroup> groups) {
        syncPositions(groups, true);
    }

    /**
     * Updates the attachment positions, without performing any relative movement updates or updating
     * attachment onTick().
     *
     * @param member MinecartMember to sync positions of
     */
    public void syncPositions(MinecartMember<?> member) {
        /* Timings: updatePositions  (Network) */
        {
            // First do a pre-movement update
            try {
                member.getAttachments().syncPrePositionUpdate(updateTransformHelper);
            } catch (Throwable ex) {
                syncFail(member.getGroup(), ex);
            }

            // Sync
            updateTransformHelper.finish();
        }

        // Post-updates
        try {
            /* Timings: performMovement  (Network) */
            {
                member.getAttachments().syncMovement(true);
            }
        } catch (Throwable t) {
            syncFail(member.getGroup(), t);
        }
    }

    /**
     * Updates the attachments of all the groups specified
     *
     * @param groups Collection of groups to update
     * @param positionSync Whether to only synchronize position of the attachments. This will not
     *                     update attachment onTick() and will force attachment positions to be sync'd.
     */
    private void syncPositions(Collection<MinecartGroup> groups, boolean positionSync) {
        /* Timings: updatePositions  (Network) */
        {
            // First do a pre-movement update for all trains
            for (MinecartGroup group : groups) {
                try {
                    group.getAttachments().syncPrePositionUpdate(updateTransformHelper);
                } catch (Throwable ex) {
                    syncFail(group, ex);
                }
            }

            // Sync
            updateTransformHelper.finish();
        }

        // Post-updates
        for (MinecartGroup group : groups) {
            try {
                if (positionSync) {
                    group.getAttachments().syncPositionAbsolute();
                } else {
                    group.getAttachments().syncPostPositionUpdate();
                }
            } catch (Throwable t) {
                syncFail(group, t);
            }
        }
    }

    private void syncFail(MinecartGroup group, Throwable ex) {
        final TrainProperties p = group.getProperties();
        plugin.log(Level.SEVERE, "Failed to synchronize a network controller of train '" + p.getTrainName() + "' at " + p.getLocation() + ":");
        plugin.handle(ex);
    }

    private class TrainUpdateTask extends Task {
        int ctr = 0;
        long lastTick = Long.MAX_VALUE;

        public TrainUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        public void run() {
            // Track elapsed time since last tick, and use that for adjustment
            long currentTime = System.currentTimeMillis();
            if (lastTick > currentTime) {
                realtimeFactor = 1.0;
            } else {
                // 50 ms is normal
                realtimeFactor = MathUtil.clamp((currentTime - lastTick) / 50.0, 0.05, 5.0);
            }
            lastTick = currentTime;

            // Refresh whether or not trains are allowed to tick
            if (++ctr >= tickUpdateDivider) {
                ctr = 0;
                tickUpdateNow++;
            }
            if (tickUpdateNow > 0) {
                tickUpdateNow--;
                ticking = true;
            } else {
                ticking = false;
            }

            // For all Minecart that were not ticked, tick them ourselves
            MinecartGroupStore.doFixedTick(plugin);
        }
    }

    private class TrainNetworkSyncTask extends Task {

        public TrainNetworkSyncTask() {
            super(plugin);
        }

        @Override
        public void run() {
            // BEFORE we send a lot of packets to players synchronizing the network, process all
            // packets queued up so far. By doing this before the actual sending, we give the server
            // a full tick time to process everything.
            // This also activates bundler mode for 1.19.4+ clients.
            PacketQueueMap packetQueues = plugin.getPacketQueueMap();
            packetQueues.forAllQueues(PacketQueue::syncBegin);

            // Actual sending of network updates
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                // Update train positions
                syncPositions(groups, false);
                // Update playback of synchronous effects (sound & particle packets)
                plugin.getEffectLoopPlayer().updateSync();
            } finally {
                // Send the bundler packets / cleanup
                packetQueues.forAllQueues(PacketQueue::syncEnd);
            }
        }
    }

    /**
     * Introduces artificial lag into the server to test
     * realtime physics behavior.
     */
    @SuppressWarnings("unused")
    private static class DebugArtificialLag extends Task {
        private final Random r = new Random();

        public DebugArtificialLag(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            int lag = DebugUtil.getIntValue("lag", 0) + r.nextInt(DebugUtil.getIntValue("jitter", 0) + 1);
            if (lag > 0) {
                try {
                    Thread.sleep(lag);
                } catch (InterruptedException e) {}
            }
        }
        
    }
}
