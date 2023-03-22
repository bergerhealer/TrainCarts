package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueue;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundBundlePacketHandle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/**
 * PacketQueue used on Minecraft 1.19.4+ which collects all packets sent into
 * a temporary queue, sending them out in Bundle packets. This is only done
 * between {@link #startBundling()} and {@link #stopBundling()}. Outside of it,
 * it uses the ordinary base asynchronous FIFO queue.
 */
class BundlerPacketQueue extends PacketQueue {
    private static final int MAX_PACKETS_PER_BUNDLE = 4095;
    private final StampedLock lock = new StampedLock();
    private final AtomicInteger bufferIndex = new AtomicInteger(Integer.MIN_VALUE);
    private final ArrayList<Object> fallbackBuffer = new ArrayList<>();
    private Object[] buffer;

    protected BundlerPacketQueue(TrainCarts plugin, Player player, PlayerGameInfo playerGameInfo, CircularFIFOQueue<CommonPacket> queue) {
        super(plugin, player, playerGameInfo, queue);
        this.buffer = new Object[256];
    }

    /**
     * Sets it so that all packets sent to this queue from now on are queued in a temporary
     * buffer. Once {@link #stopBundling()} is called, all these packets are sent
     * to the player in a single Bundle packet.
     */
    public void startBundling() {
        long writeLock = lock.writeLock();
        if (bufferIndex.get() < 0) {
            bufferIndex.set(0);
        }
        lock.unlockWrite(writeLock);
    }

    /**
     * Collects all packets sent so far since {@link #startBundling()} was called and sends
     * them in a Bundle packet to the Player. Then stops bundling any more packets, so that
     * future packets sent are sent to the asynchronous queue instead of the temporary
     * buffer.
     */
    public void stopBundling() {
        long writeLock = lock.writeLock();
        try {
            int numBufferPackets = Math.min(buffer.length, bufferIndex.getAndSet(Integer.MIN_VALUE));
            if (numBufferPackets > 0) {
                int numPackets = numBufferPackets + fallbackBuffer.size();

                // Copy all packets over into a new array
                // Do so in reverse, and while an element is unset, yield()
                // until the other thread finished writing to it. Because we
                // iterate in reverse we reduce the number of yield()s required.
                Object[] bundlePackets = new Object[numPackets];
                for (int i = numBufferPackets - 1; i >= 0; --i) {
                    Object rawPacket;
                    while ((rawPacket = buffer[i]) == null) {
                        Thread.yield();
                    }
                    bundlePackets[i] = rawPacket;
                }

                if (fallbackBuffer.isEmpty()) {
                    // Only copied the buffer, so fill it with nulls again
                    Arrays.fill(buffer, 0, numPackets, null);
                } else {
                    // Buffer was too small. Copy the extra packets over, then resize
                    // the buffer to be bigger.
                    for (int i = buffer.length, j = 0; i < numPackets; i++, j++) {
                        bundlePackets[i] = fallbackBuffer.get(j);
                    }
                    fallbackBuffer.clear();
                    fallbackBuffer.trimToSize();
                    buffer = new Object[numPackets * 2];
                }

                if (numPackets > MAX_PACKETS_PER_BUNDLE) {
                    // If more than 4095 packets split it up into multiple bundles, as clients
                    // otherwise error out
                    int i = 0;
                    do {
                        int endIndex = Math.min(i + MAX_PACKETS_PER_BUNDLE, numPackets);
                        Object[] singleBundlePackets = Arrays.copyOfRange(bundlePackets, i, endIndex);
                        super.send(ClientboundBundlePacketHandle.createNew(Arrays.asList(singleBundlePackets)));
                        i = endIndex;
                    } while (i < numPackets);
                } else {
                    // Send all of them at once in a single bundle packet (ideal)
                    super.send(ClientboundBundlePacketHandle.createNew(Arrays.asList(bundlePackets)));
                }
            }
        } finally {
            lock.unlockWrite(writeLock);
        }
    }

    @Override
    public void syncBegin() {
        super.syncBegin();
        startBundling();
    }

    @Override
    public void syncEnd() {
        super.syncEnd();
        stopBundling();
    }

    @Override
    public boolean supportsDisplayEntities() {
        return true; // Optimization. We know this is true as this only works on 1.19.4+
    }

    @Override
    public void send(CommonPacket packet) {
        handleSend(packet.getHandle(), () -> super.send(packet));
    }

    @Override
    public void send(PacketHandle packet) {
        handleSend(packet.getRaw(), () -> super.send(packet));
    }

    @Override
    public void sendSilent(CommonPacket packet) {
        handleSend(packet.getHandle(), () -> super.sendSilent(packet));
    }

    @Override
    public void sendSilent(PacketHandle packet) {
        handleSend(packet.getRaw(), () -> super.sendSilent(packet));
    }

    private void handleSend(Object rawPacket, Runnable fallbackAction) {
        // Most common case: try to put it in the buffer without any locks
        // If this index is outside the range of the buffer or is negative, then
        // we need to use a read lock to properly guarantee order.
        int index = bufferIndex.getAndIncrement();
        if (index >= 0) {
            Object[] buffer = this.buffer;
            if (index < buffer.length) {
                buffer[index] = rawPacket;
                return;
            }
        }

        // Got to synchronize. Open a read lock so we aren't sending packets out of order by accident
        long readLock = lock.readLock();
        try {
            index = bufferIndex.getAndIncrement();

            // Try to put in the buffer again. If buffer is full, put it into the slower fallback list
            if (index >= 0) {
                Object[] buffer = this.buffer;
                if (index < buffer.length) {
                    buffer[index] = rawPacket;
                } else {
                    synchronized (fallbackBuffer) {
                        fallbackBuffer.add(rawPacket);
                    }
                }
                return;
            }

            // Queue is not actually buffering for the bundle packet. Send to the fallback.
            bufferIndex.set(Integer.MIN_VALUE); // Don't drift
            fallbackAction.run();
        } finally {
            lock.unlockRead(readLock);
        }
    }
}
