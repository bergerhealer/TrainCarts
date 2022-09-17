package com.bergerkiller.bukkit.tc.attachments.api;

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

/**
 * Represents a Player that views train attachments. Can be used as a
 * key in a hashmap / hashset, where multiple viewer instances of the
 * same Player are considered equal.<br>
 * <br>
 * Includes optimized methods of sending (many) packets to the viewer.
 */
public interface AttachmentViewer {

    /**
     * Gets the Player this viewer is
     *
     * @return player
     */
    Player getPlayer();

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
}
