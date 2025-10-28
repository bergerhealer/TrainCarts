package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.collections.FastIdentityHashMap;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.global.PacketQueue;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stores all of the {@link TrainCartsAttachmentViewer} instances for all of the
 * online Players on the server.
 */
public class TrainCartsAttachmentViewerMap {
    private final TrainCarts plugin;
    private final FastIdentityHashMap<Player, TrainCartsAttachmentViewer> viewers = new FastIdentityHashMap<>();
    private final List<PacketQueue> queuesList = new ArrayList<>();

    public TrainCartsAttachmentViewerMap(TrainCarts plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the AttachmentViewer to be used for sending packets to a Player and other general
     * attachment things. This object is cached if the player is a valid and online.
     *
     * @param player Player
     * @return TrainCartsAttachmentViewer. Internal use only, please use the official
     *         API instead: {@link TrainCarts#getAttachmentViewer(Player)}
     * @see TrainCarts#getAttachmentViewer(Player)
     */
    public synchronized TrainCartsAttachmentViewer getViewer(Player player) {
        TrainCartsAttachmentViewer viewer = viewers.get(player);
        if (viewer == null) {
            final PlayerGameInfo playerGameInfo = PlayerGameInfo.of(player);
            if (player.isValid()) {
                final PacketQueue packetQueue = PacketQueue.create(plugin, player, playerGameInfo);
                viewer = new TrainCartsAttachmentViewer(plugin, player, playerGameInfo, packetQueue);
                viewers.put(player, viewer);
                queuesList.add(packetQueue);
            } else {
                final PacketQueue packetQueue = PacketQueue.createNoOp(plugin, player);
                viewer = new TrainCartsAttachmentViewer(plugin, player, playerGameInfo, packetQueue);
            }
        }
        return viewer;
    }

    /**
     * De-registers the AttachmentViewer of a Player and shuts down its Packet Queue.
     *
     * @param player Player to remove it work
     */
    public synchronized void remove(Player player) {
        TrainCartsAttachmentViewer viewer = viewers.remove(player);
        if (viewer != null) {
            final PacketQueue packetQueue = viewer.getPacketQueue();
            queuesList.remove(packetQueue);
            packetQueue.abort();
        }
    }

    /**
     * Runs an action on all currently existing player packet queues
     *
     * @param operation Operation to run
     */
    public synchronized void forAllPacketQueues(Consumer<PacketQueue> operation) {
        queuesList.forEach(operation);
    }
}
