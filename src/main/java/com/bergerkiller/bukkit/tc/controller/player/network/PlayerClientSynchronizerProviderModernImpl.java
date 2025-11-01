package com.bergerkiller.bukkit.tc.controller.player.network;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundBundlePacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Used on Minecraft 1.9 and later, where the position packet supports the teleport
 * id flag properly, which means we can rely on that.
 */
final class PlayerClientSynchronizerProviderModernImpl implements PlayerClientSynchronizer.Provider, PacketListener {
    private final TrainCarts traincarts;
    private final Task cleanupTask;
    private final Map<Player, SyncQueue> queues = new IdentityHashMap<>();
    private Map<Player, SyncQueue> queuesVisible = Collections.emptyMap();
    private boolean disabled = false;

    public PlayerClientSynchronizerProviderModernImpl(TrainCarts traincarts) {
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
        traincarts.register(this, PacketType.IN_TELEPORT_ACCEPT);
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
            int id = event.getPacket().read(PacketType.IN_TELEPORT_ACCEPT.teleportId);
            PendingAcknowledgement ack = queue.acknowledge(id);
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

        public synchronized PendingAcknowledgement acknowledge(int teleportId) {
            PendingAcknowledgement first = pending.pollFirst();
            if (first != null) {
                if (first.getTeleportId() == teleportId) {
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

            int teleportId = getSafeAwaitTeleportId();
            PacketPlayOutPositionHandle packet = positionPacketMaker.apply(teleportId);
            PendingAcknowledgement pendingAck = new PendingAcknowledgement(packet, callback);
            synchronized (this) {
                if (hasQuit) {
                    return;
                }
                this.pending.addLast(pendingAck);
                viewer.send(packet);
            }
        }

        @Override
        public void synchronizeBundle(List<? extends PacketHandle> packets, Runnable startCallback, Runnable endCallback) {
            if (hasQuit) {
                return;
            }

            if (!CommonCapabilities.HAS_BUNDLE_PACKET) {
                synchronized (this) {
                    if (hasQuit) {
                        return;
                    }

                    synchronize(startCallback);
                    for (PacketHandle packet : packets) {
                        viewer.send(packet);
                    }
                    synchronize(endCallback);
                    return;
                }
            }

            // Just use the same id twice, no big deal, we control it.
            int teleportId = getSafeAwaitTeleportId();
            PacketPlayOutPositionHandle startSyncPacket = NO_CHANGE_ACK_PACKET.apply(teleportId);
            PacketPlayOutPositionHandle endSyncPacket = NO_CHANGE_ACK_PACKET.apply(teleportId);

            List<Object> rawPackets = new ArrayList<>(packets.size() + 2);
            rawPackets.add(startSyncPacket.getRaw());
            for (PacketHandle packet : packets) {
                rawPackets.add(packet.getRaw());
            }
            rawPackets.add(endSyncPacket.getRaw());

            ClientboundBundlePacketHandle bundlePacket = ClientboundBundlePacketHandle.createNew(rawPackets);

            PendingAcknowledgement pendingStartAck = new PendingAcknowledgement(startSyncPacket, (np) -> startCallback.run());
            PendingAcknowledgement pendingEndAck = new PendingAcknowledgement(endSyncPacket, (np) -> endCallback.run());
            synchronized (this) {
                if (hasQuit) {
                    return;
                }
                this.pending.addLast(pendingStartAck);
                this.pending.addLast(pendingEndAck);
                viewer.send(bundlePacket);
            }
        }

        @Override
        public void synchronize(Runnable callback) {
            synchronize(NO_CHANGE_ACK_PACKET, p -> callback.run());
        }

        private int getSafeAwaitTeleportId() {
            EntityPlayerHandle handle = EntityPlayerHandle.fromBukkit(player);
            int id = handle.getPlayerConnection().getAwaitingTeleportId();

            // Take some ID's prior so that we are not in the way of whatever the server is doing
            id -= 20;
            if (id < 0) {
                id = Integer.MAX_VALUE + id;
            }

            return id;
        }
    }

    private static class PendingAcknowledgement {
        /** Teleport packet with unique id to wait for */
        private final PacketPlayOutPositionHandle position;
        /** Callback to run once acknowledged */
        private final Consumer<PacketPlayOutPositionHandle> callback;

        public PendingAcknowledgement(PacketPlayOutPositionHandle position, Consumer<PacketPlayOutPositionHandle> callback) {
            this.position = position;
            this.callback = callback;
        }

        public int getTeleportId() {
            return position.getTeleportWaitTimer();
        }

        public void call() {
            callback.accept(position);
        }
    }
}
