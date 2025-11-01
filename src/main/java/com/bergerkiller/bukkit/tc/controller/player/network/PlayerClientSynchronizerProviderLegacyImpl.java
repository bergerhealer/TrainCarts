package com.bergerkiller.bukkit.tc.controller.player.network;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.common.ClientboundKeepAlivePacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Used on Minecraft 1.8 - 1.8.8, where we must send some other type of marker packet
 * to detect. The position packet does not support a teleport id here.
 * But we can get similar mechanics working using the keep-alive packet. The server
 * sends a <code>System.nanoTime() / 1000000L</code> periodically, and so long we
 * stay far away from this value, we can safely inject our own packets.
 */
final class PlayerClientSynchronizerProviderLegacyImpl implements PlayerClientSynchronizer.Provider, PacketListener {
    private final TrainCarts traincarts;
    private final Task cleanupTask;
    private final Map<Player, SyncQueue> queues = new IdentityHashMap<>();
    private Map<Player, SyncQueue> queuesVisible = Collections.emptyMap();
    private boolean disabled = false;

    public PlayerClientSynchronizerProviderLegacyImpl(TrainCarts traincarts) {
        this.traincarts = traincarts;
        this.cleanupTask = new Task(traincarts) {
            @Override
            public void run() {
                cleanupQuitPlayerQueues();
            }
        };
    }

    @Override
    public PlayerClientSynchronizer forViewer(AttachmentViewer viewer) {
        PlayerClientSynchronizer queue = queuesVisible.get(viewer.getPlayer());
        if (queue == null) {
            synchronized (this) {
                if (disabled || !viewer.isConnected()) {
                    return createNoOp(viewer.getPlayer());
                }

                queue = queues.computeIfAbsent(viewer.getPlayer(), p -> new SyncQueue(viewer));
                updateVisibleQueueMap();
            }
        }

        return queue;
    }

    @Override
    public void enable() {
        traincarts.register(this, PacketType.IN_KEEP_ALIVE);
        traincarts.register(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerQuit(PlayerQuitEvent event) {
                SyncQueue queue = queuesVisible.get(event.getPlayer());
                if (queue != null) {
                    queue.setHasQuit();

                    // Note: we clean this up some ticks later.
                    // This avoids weird issues that a new queue is created after the
                    // player has already quit.
                }
            }
        });

        cleanupTask.start(20, 20);
        disabled = false;
    }

    @Override
    public synchronized void disable() {
        cleanupTask.stop();
        queues.values().forEach(SyncQueue::setHasQuit);
        queues.clear();
        queuesVisible = Collections.emptyMap();
        disabled = true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Only called with the accept teleport packets
        SyncQueue queue = queuesVisible.get(event.getPlayer());
        if (queue != null && queue.hasPendingAcknowledgements()) {
            long keepAliveId = event.getPacket().read(PacketType.IN_KEEP_ALIVE.key);
            PendingAcknowledgement ack = queue.acknowledge(keepAliveId);
            if (ack != null) {
                // Call the callback
                ack.call();

                // Don't let the server see this acknowledgement
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    private synchronized void cleanupQuitPlayerQueues() {
        if (queues.values().removeIf(SyncQueue::hasQuitSomeTimeAgo)) {
            updateVisibleQueueMap();
        }
    }

    private void updateVisibleQueueMap() {
        if (queues.isEmpty()) {
            queuesVisible = Collections.emptyMap();
        } else {
            queuesVisible = new IdentityHashMap<>(queues);
        }
    }

    private static class SyncQueue implements PlayerClientSynchronizer {
        private static final RelativeFlags NO_CHANGE_RELATIVE_FLAGS = RelativeFlags.RELATIVE_POSITION_ROTATION
                .withRelativeDelta()
                .withRelativeDeltaRotation();
        private static final IntFunction<PacketPlayOutPositionHandle> NO_CHANGE_ACK_PACKET = teleportId ->
                PacketPlayOutPositionHandle.createNew(
                        0.0, 0.0, 0.0,
                        0.0f, 0.0f,
                        0.0, 0.0, 0.0,
                        NO_CHANGE_RELATIVE_FLAGS, teleportId);

        private final AttachmentViewer viewer;
        private final Player player;
        private boolean hasQuit = false;
        private int quitTickTime;
        private final Deque<PendingAcknowledgement> pending = new LinkedList<>();

        public SyncQueue(AttachmentViewer viewer) {
            this.viewer = viewer;
            this.player = viewer.getPlayer();
        }

        public synchronized boolean hasQuitSomeTimeAgo() {
            return hasQuit && (CommonUtil.getServerTicks() - quitTickTime) > 2;
        }

        public synchronized void setHasQuit() {
            hasQuit = true;
            quitTickTime = CommonUtil.getServerTicks();
            pending.clear();
        }

        public synchronized boolean hasPendingAcknowledgements() {
            return !pending.isEmpty();
        }

        public synchronized PendingAcknowledgement acknowledge(long keepAliveId) {
            PendingAcknowledgement first = pending.pollFirst();
            if (first != null) {
                if (first.getKeepAliveId() == keepAliveId) {
                    return first;
                } else {
                    pending.addFirst(first);
                }
            }

            return null;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void synchronize(IntFunction<PacketPlayOutPositionHandle> positionPacketMaker, Consumer<PacketPlayOutPositionHandle> callback) {
            if (hasQuit) {
                return;
            }

            long keepAliveId = getSafeAwaitKeepAliveId();
            PacketPlayOutPositionHandle packet = positionPacketMaker.apply(0);
            ClientboundKeepAlivePacketHandle keepAlivePacket = ClientboundKeepAlivePacketHandle.createNew(keepAliveId);
            PendingAcknowledgement pendingAck = new PendingAcknowledgement(keepAliveId, packet, callback);
            synchronized (this) {
                if (hasQuit) {
                    return;
                }
                this.pending.addLast(pendingAck);
                viewer.send(packet);
                viewer.send(keepAlivePacket);
            }
        }

        @Override
        public void synchronizeBundle(List<? extends PacketHandle> packets, Runnable startCallback, Runnable endCallback) {
            if (hasQuit) {
                return;
            }

            synchronized (this) {
                if (hasQuit) {
                    return;
                }

                synchronize(startCallback);
                for (PacketHandle packet : packets) {
                    viewer.send(packet);
                }
                synchronize(endCallback);
            }
        }

        @Override
        public void synchronize(Runnable callback) {
            if (hasQuit) {
                return;
            }

            long keepAliveId = getSafeAwaitKeepAliveId();
            ClientboundKeepAlivePacketHandle keepAlivePacket = ClientboundKeepAlivePacketHandle.createNew(keepAliveId);
            PendingAcknowledgement pendingAck = new PendingAcknowledgement(keepAliveId, null, (np) -> callback.run());
            synchronized (this) {
                if (hasQuit) {
                    return;
                }
                this.pending.addLast(pendingAck);
                viewer.send(keepAlivePacket);
            }
        }

        private static long getSafeAwaitKeepAliveId() {
            long timeStampMillis = (System.nanoTime() / 1000000L);

            // Take some ID's prior so that we are not in the way of whatever the server is doing
            // We want to stay clash-free for about 30 seconds, so that is 30000.
            timeStampMillis -= 30000L;

            return timeStampMillis;
        }
    }

    private static class PendingAcknowledgement {
        /** Id assigned to the keep-alive packet we are looking for */
        private final long keepAliveId;
        /** Position packet sent along. Null if not used (runnable callback) */
        private final PacketPlayOutPositionHandle position;
        /** Callback to run once acknowledged */
        private final Consumer<PacketPlayOutPositionHandle> callback;

        public PendingAcknowledgement(long keepAliveId, PacketPlayOutPositionHandle position, Consumer<PacketPlayOutPositionHandle> callback) {
            this.keepAliveId = keepAliveId;
            this.position = position;
            this.callback = callback;
        }

        public long getKeepAliveId() {
            return keepAliveId;
        }

        public void call() {
            callback.accept(position);
        }
    }
}
