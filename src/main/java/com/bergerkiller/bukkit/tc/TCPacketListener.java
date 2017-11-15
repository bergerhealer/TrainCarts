package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
public class TCPacketListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Thread.dumpStack();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        if (event.getType() == PacketType.IN_STEER_VEHICLE && packet.read(PacketType.IN_STEER_VEHICLE.unmount)) {
            // Handle vehicle exit cancelling
            Player player = event.getPlayer();
            if (!TrainCarts.handlePlayerVehicleChange(player, null)) {
                packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
            }
        } else if (event.getType() == PacketType.IN_USE_ENTITY) {
            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet.read(PacketType.IN_USE_ENTITY.clickedEntityId);
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null) {
                return; // Is a valid Entity. Ignore it.
            }

            // Find all Minecart entities that are nearby the player
            for (Entity nearby : WorldUtil.getNearbyEntities(event.getPlayer(), 10.0, 10.0, 10.0)) {
                MinecartMember<?> member = MinecartMemberStore.getFromEntity(nearby);
                if (member != null) {
                    EntityNetworkController<?> enc = member.getEntity().getNetworkController();
                    if (enc instanceof MinecartMemberNetwork && ((MinecartMemberNetwork) enc).handleInteraction(entityId)) {
                        // Found our entity! Set entity Id field of the packet to the real entity.
                        packet.write(PacketType.IN_USE_ENTITY.clickedEntityId, nearby.getEntityId());
                        break;
                    }
                }
            }
        }
    }
}
