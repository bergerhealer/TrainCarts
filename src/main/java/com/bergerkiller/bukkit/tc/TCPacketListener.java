package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
public class TCPacketListener implements PacketListener {

	@Override
	public void onPacketSend(PacketSendEvent event) {
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
		}
	}
}
