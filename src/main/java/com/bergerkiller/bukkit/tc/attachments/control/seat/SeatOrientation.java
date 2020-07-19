package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
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
    private float _mountYaw = 0;
    private int _entityRotationCtr = 0;
    private final Vector _mountOffset = new Vector(0.0, VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET, 0.0);

    public float getPassengerYaw() {
        return this._entityLastYaw;
    }

    public float getPassengerPitch() {
        return this._entityLastPitch;
    }

    public float getPassengerHeadYaw() {
        return this._entityLastHeadYaw;
    }

    public float getMountYaw() {
        return this._mountYaw;
    }

    public Vector getMountOffset() {
        return this._mountOffset;
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

    private void synchronizeElytra(CartAttachmentSeat seat, Matrix4x4 transform, SeatedEntityElytra seated) {
        Vector pyr = transform.getYawPitchRoll();

        // Compute the actual position of the butt using yaw/pitch
        // Subtract this offset from the mount offset to correct for it
        // This makes sure the player is seated where the seat is
        {
            double yaw_sin = Math.sin(Math.toRadians(pyr.getY()));
            double yaw_cos = Math.cos(Math.toRadians(pyr.getY()));
            double pitch_sin = Math.sin(Math.toRadians(pyr.getX()));
            double pitch_cos = Math.cos(Math.toRadians(pyr.getX()));

            final double l = 0.6;
            final double m = 0.1;

            double rx = (l * pitch_sin) + (m * pitch_cos - m);
            double ry = (l * pitch_cos - l) - (m * pitch_sin);

            double off_x = -yaw_sin * rx;
            double off_y = ry;
            double off_z = yaw_cos * rx;

            // Subtracts butt position to correct the mount offset
            _mountOffset.setX(-off_x);
            _mountOffset.setY(VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET - off_y);
            _mountOffset.setZ(-off_z);

            // Shows a green particle where the player's butt is expected to be
            // Shows a red particle where the seat is supposed to be
            // Make sure to uncomment the mount offset when testing!
            /*
            Vector wow = transform.toVector().add(new Vector(off_x, off_y, off_z));
            for (Player viewer : seat.getViewers()) {
                PlayerUtil.spawnDustParticles(viewer, transform.toVector(), org.bukkit.Color.RED);
                PlayerUtil.spawnDustParticles(viewer, wow, org.bukkit.Color.GREEN);
            }
            */
        }

        // Yaw of vehicle player is on = yaw, player will rotate along
        _mountYaw = (float) pyr.getY();

        // Limit head rotation within range of yaw
        float pitch = (float) (pyr.getX() - 90.0);
        float headRot = EntityHandle.fromBukkit(seated.getEntity()).getHeadRotation();
        final float HEAD_ROT_LIM = 30.0f;
        if (MathUtil.getAngleDifference(headRot, _mountYaw) > HEAD_ROT_LIM) {
            if (MathUtil.getAngleDifference(headRot, _mountYaw + HEAD_ROT_LIM) <
                MathUtil.getAngleDifference(headRot, _mountYaw - HEAD_ROT_LIM)) {
                headRot = _mountYaw + HEAD_ROT_LIM;
            } else {
                headRot = _mountYaw - HEAD_ROT_LIM;
            }
        }

        // When pitch glitches out, swap the entities to avoid a glitch 180 turn
        if (Util.isProtocolRotationGlitched(pitch, this._entityLastPitch)) {
            seated.flipFakes(seat);
            this._entityRotationCtr = 0; // Force resent of yaw/pitch
        }

        // Entity id is that of a fake entity if used, otherwise uses entity id
        // If in first-person the fake entity is not used, then we're sending packets
        // about an entity that does not exist. Is this bad?
        int entityId = seated.getId();

        // Refresh head rotation
        if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(headRot, this._entityLastHeadYaw)) {
            PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, headRot);
            PacketPlayOutEntityHeadRotationHandle headPacketFlipped = null;
            if (seated.getFlippedId() != -1) {
                headPacketFlipped = PacketPlayOutEntityHeadRotationHandle.createNew(seated.getFlippedId(), headRot);
            }

            this._entityLastHeadYaw = headPacket.getHeadYaw();
            for (Player viewer : seat.getViewers()) {
                if (!seated.isInvisibleTo(viewer)) {
                    PacketUtil.sendPacket(viewer, headPacket);
                    if (headPacketFlipped != null) {
                        PacketUtil.sendPacket(viewer, headPacketFlipped);
                    }
                }
            }
        }

        // Refresh body yaw and head pitch
        // Repeat this packet every 15 ticks to make sure the entity's orientation stays correct
        // The client will automatically rotate the body towards the head after a short delay
        // Sending look packets regularly prevents that from happening
        if (this._entityRotationCtr == 0 || 
            EntityTrackerEntryStateHandle.hasProtocolRotationChanged(_mountYaw, this._entityLastYaw) ||
            EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pitch, this._entityLastPitch))
        {
            this._entityRotationCtr = 10;

            PacketPlayOutEntityLookHandle lookPacket = PacketPlayOutEntityLookHandle.createNew(entityId, _mountYaw, pitch, false);
            this._entityLastYaw = lookPacket.getYaw();
            this._entityLastPitch = lookPacket.getPitch();
            for (Player viewer : seat.getViewers()) {
                if (!seated.isInvisibleTo(viewer)) {
                    PacketUtil.sendPacket(viewer, lookPacket);
                }
            }

            // Also prep the flipped entity yaw and right flipped pitch, if used
            int flippedId = seated.getFlippedId();
            if (flippedId != -1) {
                float k = DebugUtil.getFloatValue("a", 180.0);
                float f = DebugUtil.getFloatValue("b", 10.0);
                float flippedPitch = (this._entityLastPitch >= k) ? k+f : k-f;
                PacketPlayOutEntityLookHandle flipLookPacket = PacketPlayOutEntityLookHandle.createNew(flippedId, this._entityLastYaw, flippedPitch, false);
                for (Player viewer : seat.getViewers()) {
                    if (!seated.isInvisibleTo(viewer)) {
                        PacketUtil.sendPacket(viewer, flipLookPacket);
                    }
                }
            }
        } else {
            this._entityRotationCtr--;
        }
    }

    private void synchronizeNormal(CartAttachmentSeat seat, Matrix4x4 transform, SeatedEntityNormal seated) {
        if (seated.isUpsideDown()) {
            _mountOffset.setY(VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET - 0.65);
        } else {
            _mountOffset.setY(VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET);
        }

        if (this._locked) {
            EntityHandle realPlayer = EntityHandle.fromBukkit(seated.getEntity());

            float yaw = this._mountYaw = getMountYaw(transform);
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
            // Mount yaw is always updated
            this._mountYaw = getMountYaw(transform);

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

    public void synchronize(CartAttachmentSeat seat, Matrix4x4 transform, SeatedEntity seated) {
        if (seated.isEmpty()) {
            return;
        }

        if (seated instanceof SeatedEntityElytra) {
            synchronizeElytra(seat, transform, (SeatedEntityElytra) seated);
        } else if (seated instanceof SeatedEntityNormal) {
            synchronizeNormal(seat, transform, (SeatedEntityNormal) seated);
        }

        // Apply rotation to fake mount, if needed
        if (seated.fakeMount != null) {
            seated.fakeMount.setRelativeOffset(this.getMountOffset());
            seated.fakeMount.updatePosition(transform, new Vector(0.0, (double) this.getMountYaw(), 0.0));
        }
    }

    private static float getMountYaw(Matrix4x4 transform) {
        Vector f = transform.getRotation().forwardVector();
        return MathUtil.getLookAtYaw(-f.getZ(), f.getX());
    }
}
