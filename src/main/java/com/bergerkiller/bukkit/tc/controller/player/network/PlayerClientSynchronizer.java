package com.bergerkiller.bukkit.tc.controller.player.network;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Sends synchronization messages to the client, to which the client responds and when
 * the server receives it, a callback can be called. Primarily useful to send position
 * synchronizations and to know when those are applied on the client.
 */
public interface PlayerClientSynchronizer {
    /**
     * Gets the Player this synchronizer is for
     *
     * @return Player
     */
    Player getPlayer();

    /**
     * Synchronizes with the client. A callback is provided to create a new
     * PacketPlayOutPositionHandle packet with position/rotation details to sync
     * to the client. The callback is called receiving these again once the client
     * has acknowledged it. Make sure to pass the provided integer as a teleport id
     * to the packet constructor.
     *
     * @param positionPacketMaker Callback to create the position update packet.
     *                            Is called instantly when this method is called.
     * @param callback Callback called asynchronously once this position packet has
     *                 been acknowledged.
     */
    void synchronize(
            IntFunction<PacketPlayOutPositionHandle> positionPacketMaker,
            Consumer<PacketPlayOutPositionHandle> callback);

    /**
     * Sends a packet to the client and synchronizes back when this packet is received.
     * Only works on 1.19.4+, before that the packets will just be sent one after another
     * and this method is less useful.
     *
     * @param packets Packets to include in the bundle packet
     * @param startCallback Callback called asynchronously as soon as the bundle of packets is
     *                      being processed by the client. The next few changes sent by the client
     *                      are due to these packets.
     * @param endCallback called asynchronously once the entire bundle has been
     *                    acknowledged by the client and the normal updates resume.
     */
    void synchronizeBundle(List<? extends PacketHandle> packets, Runnable startCallback, Runnable endCallback);

    /**
     * Synchronizes with the client. The callback provided is called asynchronously
     * once the request has been acknowledged.
     *
     * @param callback Callback called asynchronously once the client acknowledges
     *                 synchronization.
     */
    void synchronize(Runnable callback);

    /**
     * Provides PlayerClientSynchronizer instances for players
     */
    interface Provider extends LibraryComponent {
        /**
         * Gets the PlayerClientSynchronizer used for a particular Player.
         * Returns a no-op synchronization if the player is offline.
         * Makes use of the AttachmentViewer send packet API to ensure
         * synchronization occurs within bundles and in the same flow.
         *
         * @param viewer AttachmentViewer
         * @return PlayerClientSynchronizer
         */
        PlayerClientSynchronizer forViewer(AttachmentViewer viewer);

        /**
         * Gets the PlayerClientSynchronizer used for a particular Player.
         * Returns a no-op synchronization if the player is offline.
         *
         * @param player Player
         * @return PlayerClientSynchronizer
         */
        default PlayerClientSynchronizer forPlayer(Player player) {
            return forViewer(AttachmentViewer.forPlayer(player));
        }

        /**
         * Creates a new no-op PlayerClientSynchronizer. Does nothing
         * when synchronize is called.
         *
         * @param player Player
         * @return PlayerClientSynchronizer
         */
        default PlayerClientSynchronizer createNoOp(final Player player) {
            return new PlayerClientSynchronizer() {
                @Override
                public Player getPlayer() {
                    return player;
                }

                @Override
                public void synchronize(IntFunction<PacketPlayOutPositionHandle> positionPacketMaker, Consumer<PacketPlayOutPositionHandle> callback) {
                }

                @Override
                public void synchronizeBundle(List<? extends PacketHandle> packets, Runnable startCallback, Runnable endCallback) {
                }

                @Override
                public void synchronize(Runnable callback) {
                }
            };
        }

        /**
         * Creates a Provider instance suitable for the server
         *
         * @param traincarts TrainCarts
         * @return New Provider
         */
        static Provider create(TrainCarts traincarts) {
            if (Common.evaluateMCVersion(">=", "1.9")) {
                return new PlayerClientSynchronizerProviderModernImpl(traincarts);
            } else {
                return new PlayerClientSynchronizerProviderLegacyImpl(traincarts);
            }
        }

        void enable();
        void disable();
    }
}
