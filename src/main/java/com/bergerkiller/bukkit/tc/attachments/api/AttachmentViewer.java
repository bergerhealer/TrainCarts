package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeamHandle;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
     * Gets whether this viewer is online
     *
     * @return True if online
     */
    default boolean isOnline() {
        return getPlayer().isOnline();
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
     * Obtains a fallback AttachmentViewer implementation, to be used when only
     * a Player input is provided.
     *
     * @param player
     * @return AttachmentViewer
     */
    public static AttachmentViewer fallback(final Player player) {
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
