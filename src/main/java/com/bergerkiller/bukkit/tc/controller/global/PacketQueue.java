package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueue;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueue.EmptyQueueException;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueueStampedRW;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutCustomPayloadHandle;

/**
 * Efficiently queues up packets and sends them on a dedicated thread.
 * Includes a sync method to wait until sending has completed.
 * Queue is per player.
 */
public class PacketQueue implements me.m56738.smoothcoasters.api.NetworkInterface {
    private final TrainCarts plugin;
    private final Player player;
    private final CircularFIFOQueue<CommonPacket> queue;
    private volatile Thread thread;

    /**
     * Creates a new functional packet queue for a player
     *
     * @param plugin Main TrainCarts plugin instance
     * @param player The Player
     * @return Packet queue
     */
    public static PacketQueue create(TrainCarts plugin, Player player, PlayerGameInfo playerGameInfo) {
        CircularFIFOQueue<CommonPacket> fifoQueue = new CircularFIFOQueueStampedRW<>();

        // Since Minecraft 1.19.4 we can send Bundle packets so that all packets arrive in the same tick
        // If supported by the server AND the player, use these.
        if (CommonCapabilities.HAS_BUNDLE_PACKET && playerGameInfo.evaluateVersion(">=", "1.19.4")) {
            return new BundlerPacketQueue(plugin, player, playerGameInfo, fifoQueue);
        }

        return new PacketQueue(plugin, player, playerGameInfo, fifoQueue);
    }

    /**
     * Creates a non-functional (No-Op) packet queue. Is used for players that
     * have gone offline. Simply calls the sendPacket methods directly when
     * send is called.
     *
     * @param plugin Main TrainCarts plugin instance
     * @param player The Player
     * @return No-Op packet queue
     */
    public static PacketQueue createNoOp(TrainCarts plugin, Player player) {
        return new PacketQueue(plugin, player);
    }

    // No-Op
    private PacketQueue(TrainCarts plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.queue = CircularFIFOQueue.forward(this::processPacket);
        this.thread = null;
    }

    protected PacketQueue(TrainCarts plugin, Player player, PlayerGameInfo playerGameInfo, CircularFIFOQueue<CommonPacket> queue) {
        this.plugin = plugin;
        this.player = player;
        this.queue = queue;
        this.queue.setWakeCallback(this::startProcessingPackets);
        this.thread = null;
    }

    public void send(PacketHandle packet) {
        queue.put(packet.toCommonPacket());
    }

    public void send(CommonPacket packet) {
        queue.put(packet);
    }

    public void sendSilent(CommonPacket packet) {
        queue.put(new PacketQueue.SilentCommonPacket(packet.getHandle(), packet.getType()));
    }

    public void sendSilent(PacketHandle packet) {
        queue.put(new PacketQueue.SilentCommonPacket(packet.getRaw(), packet.getPacketType()));
    }

    /// -------------- Smoothcoasters support integration -----------------
    @Override
    public void sendMessage(Player player, String channel, byte[] message) {
        if (player != this.player) {
            throw new IllegalArgumentException("Wrong network interface used, interface is of " +
                    this.player.getName() + " but updated " + player.getName());
        }
        if (plugin.getSmoothCoastersAPI().getVersion(player) < 5) {
            // Cannot use bundle packets with V4 because of a race condition
            queue.put(PacketPlayOutCustomPayloadHandle.createNew(channel, message).toCommonPacket());
        } else {
            send(PacketPlayOutCustomPayloadHandle.createNew(channel, message));
        }
    }
    /// --------------------------------------------------------------------

    public void abort() {
        queue.abort();
    }

    /**
     * Called before a large amount of packets are going to be sent to a Player.
     * Waits until all packets have been processed. Might do more stuff in preparation.
     */
    public void syncBegin() {
        while (!this.queue.isEmpty()) {
            Thread.yield(); // Eh.
        }
    }

    /**
     * Performs any operations needed after a large amount of packets have been sent to this player
     */
    public void syncEnd() {
    }

    private void startProcessingPackets() {
        if (thread == null && !queue.isAborted()) {
            Thread newThread = new Thread(this::processPacketsThread, "TC-PacketWriterThread-" + player.getEntityId());
            newThread.setDaemon(true);
            this.thread = newThread;
            newThread.start();
        }
    }

    private void processPacketsThread() {
        final CircularFIFOQueue<CommonPacket> queue = this.queue;
        while (true) {
            try {
                processPacket(queue.take(60000));
            } catch (EmptyQueueException e) {
                if (queue.runIfEmpty(() -> thread = null)) {
                    break;
                }
            }
        }
    }

    private void processPacket(CommonPacket packet) {
        PacketUtil.sendPacket(player, packet, !(packet instanceof SilentCommonPacket));
    }

    @Override
    public String toString() {
        return "PacketQueue{player=" + player.getName() + "}";
    }

    /**
     * Marker class for a packet that should not go through packet listeners
     */
    public static final class SilentCommonPacket extends CommonPacket {

        public SilentCommonPacket(Object packetHandle, PacketType packetType) {
            super(packetHandle, packetType);
        }
    }
}
