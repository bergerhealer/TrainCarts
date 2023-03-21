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
            int numPackets = bufferIndex.getAndSet(Integer.MIN_VALUE);
            if (numPackets > 0) {
                Object[] bundlePackets = Arrays.copyOf(buffer, numPackets);
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

                // Throw into a Bundle packet and send it
                // Leave locked while we do this as otherwise packets can be sent out of order
                super.send(ClientboundBundlePacketHandle.createNew(Arrays.asList(bundlePackets)));
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
    public void send(PacketHandle packet) {
        long readLock = lock.readLock();
        int index = bufferIndex.getAndIncrement();
        if (index < 0) {
            try {
                super.send(packet);
            } finally {
                lock.unlockRead(readLock);
            }
        } else {
            addToQueue(readLock, index, packet.getRaw());
        }
    }

    @Override
    public void send(CommonPacket packet) {
        long readLock = lock.readLock();
        int index = bufferIndex.getAndIncrement();
        if (index < 0) {
            try {
                super.send(packet);
            } finally {
                lock.unlockRead(readLock);
            }
        } else {
            addToQueue(readLock, index, packet.getHandle());
        }
    }

    @Override
    public void sendSilent(CommonPacket packet) {
        long readLock = lock.readLock();
        int index = bufferIndex.getAndIncrement();
        if (index < 0) {
            try {
                super.sendSilent(packet);
            } finally {
                lock.unlockRead(readLock);
            }
        } else {
            addToQueue(readLock, index, packet.getHandle());
        }
    }

    @Override
    public void sendSilent(PacketHandle packet) {
        long readLock = lock.readLock();
        int index = bufferIndex.getAndIncrement();
        if (index < 0) {
            try {
                super.sendSilent(packet);
            } finally {
                lock.unlockRead(readLock);
            }
        } else {
            addToQueue(readLock, index, packet.getRaw());
        }
    }

    private void addToQueue(long readLock, int index, Object rawPacket) {
        // Try to add to the buffer
        Object[] buffer = this.buffer;
        if (index < buffer.length) {
            buffer[index] = rawPacket;
        } else {
            // Buffer is too small! Add to the fallback list. Next sync we resize the buffer
            // so it fits the right amount of items.
            // This is slower, but we only suck for a single run.
            synchronized (fallbackBuffer) {
                fallbackBuffer.add(rawPacket);
            }
        }
        lock.unlockRead(readLock);
    }
}
