package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerClientSynchronizer;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;

import me.m56738.smoothcoasters.api.NetworkInterface;

import java.util.Iterator;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a Player that views train attachments. Can be used as a
 * key in a hashmap / hashset, where multiple viewer instances of the
 * same Player are considered equal.<br>
 * <br>
 * Includes optimized methods of sending (many) packets to the viewer.
 */
public interface AttachmentViewer extends TrainCarts.Provider {

    /**
     * Gets the Player this viewer is
     *
     * @return player
     */
    Player getPlayer();

    /**
     * Gets the name of this Player viewer
     *
     * @return Player name
     */
    default String getName() {
        return getPlayer().getName();
    }

    @Override
    TrainCarts getTrainCarts();

    /**
     * Gets the Vehicle Mount Controller of this viewer. This is used to
     * mount and unmount entities into one another for a player.
     *
     * @return vehicle mount controller
     */
    VehicleMountController getVehicleMountController();

    /**
     * Gets the smooth coasters network interface. This can be passed to
     * smoothcoasters API method calls to use this viewer's send methods.
     * May return null if the default network interface should be used.<br>
     * <br>
     * This network interface should <b>only</b> be used for this viewer!
     *
     * @return smooth coasters network interface
     */
    me.m56738.smoothcoasters.api.NetworkInterface getSmoothCoastersNetwork();

    /**
     * Sends a packet to this viewer
     *
     * @param packet
     */
    void send(CommonPacket packet);

    /**
     * Sends a packet to this viewer
     *
     * @param packet
     */
    void send(PacketHandle packet);

    /**
     * Sends a packet to this viewer, bypassing packet listeners like ProtocolLib
     *
     * @param packet
     */
    void sendSilent(CommonPacket packet);

    /**
     * Sends a packet to this viewer, bypassing packet listeners like ProtocolLib
     *
     * @param packet
     */
    void sendSilent(PacketHandle packet);

    /**
     * Sends an EntityLiving spawn packet with entity metadata
     *
     * @param packet
     * @param metadata
     */
    @SuppressWarnings("deprecation")
    default void sendEntityLivingSpawnPacket(PacketPlayOutSpawnEntityLivingHandle packet, DataWatcher metadata) {
        if (packet.hasDataWatcherSupport()) {
            packet.setDataWatcher(metadata);
            send(packet);
        } else {
            send(packet);
            send(PacketPlayOutEntityMetadataHandle.createNew(packet.getEntityId(), metadata, true));
        }
    }

    /**
     * Sends the spawn packet for a named entity. On MC 1.15 and later the metadata for the entity is sent separate
     * from the spawn packet of the named entity.
     *
     * @param packet
     * @param metadata
     */
    @SuppressWarnings("deprecation")
    default void sendNamedEntitySpawnPacket(PacketPlayOutNamedEntitySpawnHandle packet, DataWatcher metadata) {
        if (packet.hasDataWatcherSupport()) {
            packet.setDataWatcher(metadata);
            send(packet);
        } else {
            send(packet);
            send(PacketPlayOutEntityMetadataHandle.createNew(packet.getEntityId(), metadata, true));
        }
    }

    /**
     * Sends a Team packet to this player that disables collision caused by an Entity
     *
     * @param entityUUID UUID of the Entity
     */
    default void sendDisableCollision(UUID entityUUID) {
        getTrainCarts().getTeamProvider().noCollisionTeam().join(this, entityUUID);
    }

    /**
     * Sends a Team packet to this player that disables collision caused by
     * a number of entities.
     *
     * @param entityUUIDs UUID of the Entities to disable collision for
     */
    default void sendDisableCollision(Iterable<UUID> entityUUIDs) {
        getTrainCarts().getTeamProvider().noCollisionTeam().join(this, entityUUIDs);
    }

    /**
     * Gets whether the Player is logged on and has a living Entity on a World.
     * If the player is respawning, this returns False. If it's important to know
     * whether the player logged off, use {@link #isConnected()} instead.
     *
     * @return True if the Player is alive and connected
     */
    default boolean isValid() {
        return getPlayer().isValid();
    }

    /**
     * Gets whether the Player is still connected to the server, and has joined it
     * (past the login phase). This also returns True if the player is still connected
     * but is dead and on the respawn screen.
     *
     * @return True if the Player is still connected to the server and has joined it
     */
    default boolean isConnected() {
        Player player = getPlayer();
        return player.isValid() || Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    /**
     * Gets the PlayerClientSynchronizer instance for a Player. This is used to send packets
     * to the client, and wait for the client to acknowledge them. Then a callback is called
     * on the server end.<br>
     * <br>
     * This is primarily useful to send position updates to the client and know when those
     * have been applied. This accounts for (large) client latency.
     *
     * @return PlayerClientSynchronizer
     */
    default PlayerClientSynchronizer getClientSynchronizer() {
        return getTrainCarts().getPlayerClientSynchronizerProvider().forPlayer(getPlayer());
    }

    /**
     * Creates a packet listener, for this Player viewer only.
     * Must call {@link PlayerPacketListener#enable()} before it is active.
     * Can call disable to temporarily stop it, and terminate to shut down the packet
     * listener forever. Is automatically stopped when this player quits the server.
     *
     * @param packetListener PacketListener
     * @param packetTypes PacketTypes to listen for (receive OR send)
     * @return PlayerPacketListener to enable/disable the listener. Also grants access
     *         to the original packet listener implementation passed in.
     * @param <L> PacketListener implementation type
     */
    default <L extends PacketListener> PlayerPacketListener<L> createPacketListener(L packetListener, PacketType... packetTypes) {
        TrainCarts trainCarts = getTrainCarts();
        return trainCarts.getPlayerPacketListenerProvider().create(
                        getPlayer(), packetListener, packetTypes);
    }

    /**
     * Gets the Entity ID of the Player
     *
     * @return Player entity ID
     */
    default int getEntityId() {
        return getPlayer().getEntityId();
    }

    /**
     * Evaluates a logical expression against the game version supported by this viewer.
     * Will make use of API's such as ViaVersion to detect the actual game version of the player.
     *
     * @param operand to evaluate (>, >=, ==, etc.)
     * @param rightSide value on the right side of the operand
     * @return True if the evaluation succeeds, False if not
     */
    default boolean evaluateGameVersion(String operand, String rightSide) {
        return PlayerUtil.evaluateGameVersion(getPlayer(), operand, rightSide);
    }

    /**
     * Gets whether this viewer, and the server itself, can support the use of display entities.
     * If true, a display entity can be spawned for this player. If false, armorstands should be used.
     *
     * @return True if the display entity is supported by this viewer
     */
    default boolean supportsDisplayEntities() {
        return CommonCapabilities.HAS_DISPLAY_ENTITY && evaluateGameVersion(">=", "1.19.4");
    }

    /**
     * Gets whether this viewer, and the server itself, can support the use of display entities
     * and teleporting them smoothly with interpolation. This is since 1.20.2.
     *
     * @return True if display entity location interpolation is supported for this client
     */
    default boolean supportsDisplayEntityLocationInterpolation() {
        return CommonCapabilities.HAS_DISPLAY_ENTITY_LOCATION_INTERPOLATION && evaluateGameVersion(">=", "1.20.2");
    }

    /**
     * Gets whether this viewer, and the server itself, is capable of sending a relative camera rotation
     * update to this player. Minecraft 1.21.2 - 1.21.8 do not support this.
     *
     * @return True if relative camera rotation updates are supported
     */
    default boolean supportRelativeRotationUpdate() {
        return (Common.evaluateMCVersion("<", "1.21.2") || Common.evaluateMCVersion(">=", "1.21.9")) &&
                (evaluateGameVersion("<", "1.21.2") || evaluateGameVersion(">=", "1.21.9"));
    }

    /**
     * Gets the offset at which a player sits on a surface. Mounts must be adjusted to take this
     * into account.
     *
     * @return ArmorStand butt offset. Game version-dependent.
     */
    default double getArmorStandButtOffset() {
        return evaluateGameVersion(">=", "1.20.2") ? 0.0 : 0.27;
    }

    /**
     * Resets the glow color of an entity
     *
     * @param entityUUID Entity UUID
     */
    default void resetGlowColor(UUID entityUUID) {
        getTrainCarts().getGlowColorTeamProvider().reset(this, entityUUID);
    }

    /**
     * Sets a glow color for an entity
     *
     * @param entityUUID Entity UUID
     * @param color Desired color. Null to reset.
     */
    default void updateGlowColor(UUID entityUUID, ChatColor color) {
        getTrainCarts().getGlowColorTeamProvider().update(this, entityUUID, color);
    }

    /**
     * Sets a glow color for an entity
     *
     * @param entityUUIDs Entity UUIDs
     * @param color Desired color. Null to reset.
     */
    default void updateGlowColor(Iterable<UUID> entityUUIDs, ChatColor color) {
        getTrainCarts().getGlowColorTeamProvider().update(this, entityUUIDs, color);
    }

    /**
     * Obtains the AttachmentViewer implementation best suited for a Player.
     * If TrainCarts is enabled and the player is still online, uses the TrainCarts
     * implementation with optimized APIs. Otherwise uses the
     * {@link #fallback(Player)} implementation.
     *
     * @param player Player
     * @return AttachmentViewer
     */
    static AttachmentViewer forPlayer(final Player player) {
        TrainCarts trainCarts = TrainCarts.plugin;
        if (trainCarts != null && trainCarts.isEnabled()) {
            return trainCarts.getAttachmentViewer(player);
        } else {
            return fallback(player);
        }
    }

    /**
     * Obtains a fallback AttachmentViewer implementation, to be used when only
     * a Player input is provided.
     *
     * @param player
     * @return AttachmentViewer
     */
    static AttachmentViewer fallback(final Player player) {
        return new AttachmentViewer() {
            @Override
            public TrainCarts getTrainCarts() {
                return TrainCarts.plugin;
            }

            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public VehicleMountController getVehicleMountController() {
                return PlayerUtil.getVehicleMountController(player);
            }

            @Override
            public NetworkInterface getSmoothCoastersNetwork() {
                return null; // Let the API decide
            }

            @Override
            public void send(CommonPacket packet) {
                PacketUtil.sendPacket(player, packet);
            }

            @Override
            public void send(PacketHandle packet) {
                PacketUtil.sendPacket(player, packet);
            }

            @Override
            public void sendSilent(CommonPacket packet) {
                PacketUtil.sendPacket(player, packet, false);
            }

            @Override
            public void sendSilent(PacketHandle packet) {
                PacketUtil.sendPacket(player, packet, false);
            }

            @Override
            public int hashCode() {
                return player.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                } else if (o instanceof AttachmentViewer) {
                    return ((AttachmentViewer) o).getPlayer() == player;
                } else {
                    return false;
                }
            }
        };
    }

    /**
     * Adapts a Player Iterable and calls {@link #fallback(Player)} on every element.
     *
     * @param players Player Iterable
     * @return Iterable of Attachment Viewers
     */
    public static Iterable<AttachmentViewer> fallbackIterable(Iterable<Player> players) {
        return () -> new Iterator<AttachmentViewer>() {
            private final Iterator<Player> baseIter = players.iterator();

            @Override
            public boolean hasNext() {
                return baseIter.hasNext();
            }

            @Override
            public AttachmentViewer next() {
                return fallback(baseIter.next());
            }

            @Override
            public void remove() {
                baseIter.remove();
            }

            @Override
            public void forEachRemaining(Consumer<? super AttachmentViewer> action) {
                baseIter.forEachRemaining(p -> action.accept(fallback(p)));
            }
        };
    }
}
