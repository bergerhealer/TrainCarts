package com.bergerkiller.bukkit.tc.controller.player.network;

import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.entity.Player;

/**
 * A temporary packet listener that receives events for a single Player only.
 * Can be atomically switched on or off as well, which is useful to pair it with
 * the {@link PlayerClientSynchronizer} to start listening once the client is in
 * a known state.
 *
 * @param <L> PacketListener implementation type
 */
public interface PlayerPacketListener<L extends PacketListener> {

    /**
     * Creates a no-operation player packet listener. No packets are listened,
     * {@link #isEnabled()} always returns false and the enable/disable/terminate
     * methods do nothing. This is used when requesting a packet listener for a
     * Player that has quit the server.
     *
     * @param player Player
     * @param packetListener PacketListener implementation
     * @return No-Op PlayerPacketListener
     * @param <L> PacketListener implementation type
     */
    static <L extends PacketListener> PlayerPacketListener<L> createNoOp(final Player player, final L packetListener) {
        return new PlayerPacketListener<L>() {
            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public L getListener() {
                return packetListener;
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public PlayerPacketListener<L> enable() {
                return this;
            }

            @Override
            public PlayerPacketListener<L> disable() {
                return this;
            }

            @Override
            public void terminate() {
            }
        };
    }

    /**
     * Gets the Player whose packets are being listened
     *
     * @return Player
     */
    Player getPlayer();

    /**
     * Gets the packet listener instance that receives all of the packet updates
     *
     * @return Packet Listener
     */
    L getListener();

    /**
     * Gets whether this player packet listener is presently enabled. If disabled, the
     * listener is still installed but will not receive packet updates (silent).<br>
     * <br>
     * States:
     * <ul>
     *     <li>false: When the listener is first created</li>
     *     <li>true: After {@link #enable()} is called once</li>
     *     <li>false: After {@link #disable()} is called once. Can be enabled again.</li>
     *     <li>false: After {@link #terminate()} is called or player quits the server.</li>
     * </ul>
     *
     * @return True if this player packet listener is currently enabled
     */
    boolean isEnabled();

    /**
     * Enables this player packet listener. When first created the listener is not enabled,
     * so this method must be called to enable it. This method is multithread-safe.
     *
     * @return this (for chaining/assignment)
     */
    PlayerPacketListener<L> enable();

    /**
     * Disables this player packet listener. This does keep the packet listener "warm", ready
     * to enable again in the future. This method is multithread-safe.<br>
     * <br>
     * To actually stop it permanently and clean up all resources, call {@link #terminate()}
     * instead. This is automatically called when the player quits the server as well.
     *
     * @return this (for chaining/assignment)
     * @see #terminate()
     */
    PlayerPacketListener<L> disable();

    /**
     * Permanently stops this packet listener. It will never receive packet updates again,
     * and {@link #enable()} can not turn it back on. This is automatically
     * called when players quit the server. After it is terminated, {@link #isEnabled()}
     * will return false.
     */
    void terminate();

    /**
     * Provides PlayerPacketListener instances for players and listeners
     */
    interface Provider extends LibraryComponent {
        /**
         * Creates a new PlayerPacketListener provider
         *
         * @param traincarts Main plugin instance
         * @return New Provider
         */
        static Provider create(TrainCarts traincarts) {
            return new PlayerPacketListenerProviderImpl(traincarts);
        }

        /**
         * Starts listening for packets. The returned listener is initially disabled, and must be enabled
         * first.
         *
         * @param player Player to listen packets for/from
         * @param packetListener PacketListener that receives the changes
         * @param packetTypes Array of packet types to listen for
         * @return PlayerPacketListener
         * @param <L> PacketListener implementation type
         */
        <L extends PacketListener> PlayerPacketListener<L> create(Player player, L packetListener, PacketType... packetTypes);

        void enable();
        void disable();
    }
}
