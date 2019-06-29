package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.wrappers.IntHashMap;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHeadRotationHandle;

/**
 * Simple map, mapping passenger entity Id's to the seat attachment
 * they are occupying. Is used to intercept and handle rotation packets
 * for the passengers.
 */
public class SeatAttachmentMap implements PacketListener {
    public static PacketType[] LISTENED_TYPES = {
            PacketType.OUT_ENTITY_MOVE_LOOK,
            PacketType.OUT_ENTITY_LOOK,
            PacketType.OUT_ENTITY_HEAD_ROTATION
    };
    private final IntHashMap<CartAttachmentSeat> _map = new IntHashMap<CartAttachmentSeat>();

    public void set(int passengerEntityId, CartAttachmentSeat seat) {
        this._map.put(passengerEntityId, seat);
    }

    public void remove(int passengerEntityId, CartAttachmentSeat seat) {
        CartAttachmentSeat removed = this._map.remove(passengerEntityId);
        if (removed != seat) {
            this._map.put(passengerEntityId, removed); // undo
        }
    }

    public CartAttachmentSeat get(int passengerEntityId) {
        CartAttachmentSeat seat = this._map.get(passengerEntityId);
        if (seat != null && (seat.getEntity() == null || seat.getEntity().getEntityId() != passengerEntityId)) {
            this._map.remove(passengerEntityId);
            seat = null;
        }
        return seat;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getType() == PacketType.OUT_ENTITY_MOVE_LOOK) {
            PacketPlayOutEntityHandle packet = PacketPlayOutEntityHandle.createHandle(event.getPacket().getHandle());
            CartAttachmentSeat seat = get(packet.getEntityId());
            if (seat != null && seat.isRotationLocked()) {
                packet.setYaw(seat.getPassengerYaw());
                packet.setPitch(seat.getPassengerPitch());
            }
        } else if (event.getType() == PacketType.OUT_ENTITY_LOOK) {
            PacketPlayOutEntityHandle packet = PacketPlayOutEntityHandle.createHandle(event.getPacket().getHandle());
            CartAttachmentSeat seat = get(packet.getEntityId());
            if (seat != null && seat.isRotationLocked()) {
                packet.setYaw(seat.getPassengerYaw());
                packet.setPitch(seat.getPassengerPitch());
            }
        } else if (event.getType() == PacketType.OUT_ENTITY_HEAD_ROTATION) {
            PacketPlayOutEntityHeadRotationHandle packet = PacketPlayOutEntityHeadRotationHandle.createHandle(event.getPacket().getHandle());
            CartAttachmentSeat seat = get(packet.getEntityId());
            if (seat != null && seat.isRotationLocked()) {
                packet.setHeadYaw(seat.getPassengerHeadYaw());
            }
        }
    }

}
