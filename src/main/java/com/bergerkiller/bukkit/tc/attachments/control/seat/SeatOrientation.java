package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryStateHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHeadRotationHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutEntityLookHandle;

/**
 * Tracks and updates the orientation of a seated entity
 */
public class SeatOrientation {
    private boolean _locked = false;
    private float _entityLastYaw = 0;
    private float _entityLastPitch = 0;
    private float _entityLastHeadYaw = 0;
    private int _entityRotationCtr = 0;

    public float getPassengerYaw() {
        return this._entityLastYaw;
    }

    public float getPassengerPitch() {
        return this._entityLastPitch;
    }

    public float getPassengerHeadYaw() {
        return this._entityLastHeadYaw;
    }

    public boolean isLocked() {
        return this._locked;
    }

    public void setLocked(boolean locked) {
        this._locked = locked;
    }

    public void makeVisible(Player viewer, SeatedEntity seated) {
        if (this.isLocked() && !seated.isEmpty()) {
            int entityId = seated.getId();

            // Do not send viewer to self - bad things happen
            if (entityId != viewer.getEntityId()) {
                PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, getPassengerHeadYaw());
                PacketUtil.sendPacket(viewer, headPacket);

                PacketPlayOutEntityLookHandle lookPacket = PacketPlayOutEntityLookHandle.createNew(
                        entityId, getPassengerYaw(), getPassengerPitch(), false);
                PacketUtil.sendPacket(viewer, lookPacket);
            }
        }
    }

    public void synchronize(CartAttachmentSeat seat, SeatedEntity seated) {
        if (seated.isEmpty()) {
            return;
        }
        if (this._locked) {
            EntityHandle realPlayer = EntityHandle.fromBukkit(seated.getEntity());
            float yaw = (float) seat.getMountPYR().getY();

            float pitch = realPlayer.getPitch();
            float headRot = realPlayer.getHeadRotation();

            // Reverse the values and correct head yaw, because the player is upside-down
            if (seated.isUpsideDown()) {
                pitch = -pitch;
                headRot = -headRot + 2.0f * yaw;
            }

            // Limit head rotation within range of yaw
            final float HEAD_ROT_LIM = 30.0f;
            if (MathUtil.getAngleDifference(headRot, yaw) > HEAD_ROT_LIM) {
                if (MathUtil.getAngleDifference(headRot, yaw + HEAD_ROT_LIM) <
                    MathUtil.getAngleDifference(headRot, yaw - HEAD_ROT_LIM)) {
                    headRot = yaw + HEAD_ROT_LIM;
                } else {
                    headRot = yaw - HEAD_ROT_LIM;
                }
            }

            // Entity id is that of a fake entity if used, otherwise uses entity id
            // If in first-person the fake entity is not used, then we're sending packets
            // about an entity that does not exist. Is this bad?
            int entityId = seated.getId();

            // Refresh head rotation
            if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(headRot, this._entityLastHeadYaw)) {
                PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, headRot);
                this._entityLastHeadYaw = headPacket.getHeadYaw();
                for (Player viewer : seat.getViewers()) {
                    if (viewer.getEntityId() != entityId) {
                        PacketUtil.sendPacket(viewer, headPacket);
                    }
                }
            }

            // Refresh body yaw and head pitch
            // Repeat this packet every 15 ticks to make sure the entity's orientation stays correct
            // The client will automatically rotate the body towards the head after a short delay
            // Sending look packets regularly prevents that from happening
            if (this._entityRotationCtr == 0 || 
                    EntityTrackerEntryStateHandle.hasProtocolRotationChanged(yaw, this._entityLastYaw) ||
                EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pitch, this._entityLastPitch))
            {
                this._entityRotationCtr = 10;

                PacketPlayOutEntityLookHandle lookPacket = PacketPlayOutEntityLookHandle.createNew(entityId, yaw, pitch, false);
                this._entityLastYaw = lookPacket.getYaw();
                this._entityLastPitch = lookPacket.getPitch();
                for (Player viewer : seat.getViewers()) {
                    if (viewer.getEntityId() != entityId) {
                        PacketUtil.sendPacket(viewer, lookPacket);
                    }
                }
            } else {
                this._entityRotationCtr--;
            }
        } else {
            // Refresh head rotation and body yaw/pitch for a fake player entity
            if (seated.isPlayer() && seated.isFake()) {
                EntityHandle realPlayer = EntityHandle.fromBukkit(seated.getEntity());
                float yaw = realPlayer.getYaw();
                float pitch = realPlayer.getPitch();
                float headRot = realPlayer.getHeadRotation();

                // Reverse the values and correct head yaw, because the player is upside-down
                if (seated.isUpsideDown()) {
                    pitch = -pitch;
                    headRot = -headRot + 2.0f * yaw;
                }

                if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(yaw, this._entityLastYaw) ||
                    EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pitch, this._entityLastPitch))
                {
                    PacketPlayOutEntityLookHandle lookPacket = PacketPlayOutEntityLookHandle.createNew(seated.getId(), yaw, pitch, false);
                    this._entityLastYaw = lookPacket.getYaw();
                    this._entityLastPitch = lookPacket.getPitch();
                    for (Player viewer : seat.getViewers()) {
                        PacketUtil.sendPacket(viewer, lookPacket);
                    }
                }

                if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(headRot, this._entityLastHeadYaw)) {
                    PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(seated.getId(), headRot);
                    this._entityLastHeadYaw = headPacket.getHeadYaw();
                    for (Player viewer : seat.getViewers()) {
                        PacketUtil.sendPacket(viewer, headPacket);
                    }
                }
            }
        }
    }
}
