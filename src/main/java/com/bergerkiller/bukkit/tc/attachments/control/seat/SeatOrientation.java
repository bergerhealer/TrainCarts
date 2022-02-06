package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotationHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle.PacketPlayOutEntityLookHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityTrackerEntryStateHandle;

/**
 * Tracks and updates the orientation of a seated entity
 */
public class SeatOrientation {
    private float _entityLastYaw = 0;
    private float _entityLastPitch = 0;
    private float _entityLastHeadYaw = 0;
    private float _mountYaw = 0;
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

    public float getMountYaw() {
        return this._mountYaw;
    }

    public void sendLockedRotations(Player viewer, int entityId) {
        // Do not send viewer to self - bad things happen
        if (entityId != viewer.getEntityId()) {
            PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, getPassengerHeadYaw());
            PacketUtil.sendPacket(viewer, headPacket);

            PacketPlayOutEntityLookHandle lookPacket = PacketPlayOutEntityLookHandle.createNew(
                    entityId, getPassengerYaw(), getPassengerPitch(), false);
            PacketUtil.sendPacket(viewer, lookPacket);
        }
    }

    // Compute the actual position of the butt using yaw/pitch
    // Subtract this offset from the mount offset to correct for it
    // This makes sure the player is seated where the seat is
    protected Vector computeElytraRelativeOffset(Vector pyr) {
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
        return new Vector(-off_x, -off_y, -off_z);
    }

    protected void synchronizeElytra(CartAttachmentSeat seat, Matrix4x4 transform, Vector pyr, SeatedEntityElytra seated) {
        // Should not send updates if this elytra entity is not visible to the player
        // This is the case when it's about the player itself, and the player has
        // not enabled a third-person perspective.
        Player viewerToIgnore = (seated.isPlayer() && !seated.isMadeVisibleInFirstPerson())
                ? (Player) seated.getEntity() : null;

        // Yaw of vehicle player is on = yaw, player will rotate along
        _mountYaw = (float) pyr.getY();

        // Limit head rotation within range of yaw
        float pitch = (float) (pyr.getX() - 90.0);
        float headRot = seated.isDummyPlayer() ? _mountYaw : EntityHandle.fromBukkit(seated.getEntity()).getHeadRotation();
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
        int entityId = seated.getFakePlayerId();
        int flippedId = seated.getFlippedFakePlayerId();

        // Refresh head rotation
        if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(headRot, this._entityLastHeadYaw)) {
            PacketPlayOutEntityHeadRotationHandle headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, headRot);
            PacketPlayOutEntityHeadRotationHandle headPacketFlipped =  PacketPlayOutEntityHeadRotationHandle.createNew(flippedId, headRot);

            this._entityLastHeadYaw = headPacket.getHeadYaw();
            for (Player viewer : seat.getViewers()) {
                if (viewer != viewerToIgnore) {
                    PacketUtil.sendPacket(viewer, headPacket);
                    PacketUtil.sendPacket(viewer, headPacketFlipped);
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
                if (viewer != viewerToIgnore) {
                    PacketUtil.sendPacket(viewer, lookPacket);
                }
            }

            // Also prep the flipped entity yaw and right flipped pitch, if used
            float k = 180.0f;
            float f = 10.0f;
            float flippedPitch = (this._entityLastPitch >= k) ? k+f : k-f;
            PacketPlayOutEntityLookHandle flipLookPacket = PacketPlayOutEntityLookHandle.createNew(flippedId, this._entityLastYaw, flippedPitch, false);
            for (Player viewer : seat.getViewers()) {
                if (viewer != viewerToIgnore) {
                    PacketUtil.sendPacket(viewer, flipLookPacket);
                }
            }
        } else {
            this._entityRotationCtr--;
        }
    }

    protected void synchronizeNormal(CartAttachmentSeat seat, Matrix4x4 transform, SeatedEntityNormal seated, int entityId) {
        // Should not send updates if this normal/upside-down entity is not visible to the player
        // This is the case when it's about the player itself, and the player has
        // not enabled a third-person perspective.
        Player viewerToIgnore = (seated.isPlayer() && !seated.isMadeVisibleInFirstPerson())
                ? (Player) seated.getEntity() : null;

        SeatedEntity.PassengerPose pose = seated.getCurrentHeadRotation(transform);
        this._mountYaw = pose.bodyYaw; // Mount yaw is always updated

        // Limit head yaw by 30 degrees compared to body yaw
        if (seat.isRotationLocked()) {
            pose = pose.limitHeadYaw(30.0f);
        }

        // For clients on Minecraft 1.17 and before, when upside-down the pose needs to be transformed to fix a bug
        SeatedEntity.PassengerPose poseFixed = seated.isUpsideDown() ? pose.upsideDownFix_Pre_1_17() : pose;

        // These are the types of packets we might be sending
        PacketPlayOutEntityHeadRotationHandle headPacket = null;
        PacketPlayOutEntityLookHandle lookPacket = null;

        // Refresh head rotation
        if (EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pose.headYaw, this._entityLastHeadYaw)) {
            headPacket = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, pose.headYaw);
            this._entityLastHeadYaw = headPacket.getHeadYaw();
        }

        // Refresh body yaw and head pitch
        // Repeat this packet every 15 ticks to make sure the entity's orientation stays correct
        // The client will automatically rotate the body towards the head after a short delay
        // Sending look packets regularly prevents that from happening
        // Only needed when in locked mode.
        if (this._entityRotationCtr == 0 || 
            EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pose.bodyYaw, this._entityLastYaw) ||
            EntityTrackerEntryStateHandle.hasProtocolRotationChanged(pose.headPitch, this._entityLastPitch))
        {
            this._entityRotationCtr = 10;

            lookPacket = PacketPlayOutEntityLookHandle.createNew(entityId, pose.bodyYaw, pose.headPitch, false);
            this._entityLastYaw = lookPacket.getYaw();
            this._entityLastPitch = lookPacket.getPitch();
        } else if (seat.isRotationLocked()) {
            this._entityRotationCtr--; // Repeat after the delay elapses
        }

        // These packets are sent to players on Minecraft version 1.17.1 and before
        // Only used when isUpsideDownTransform is active
        PacketPlayOutEntityHeadRotationHandle headPacket_1_17_fix = null;
        PacketPlayOutEntityLookHandle lookPacket_1_17_fix = null;

        // Send packets to all the players
        if (headPacket != null || lookPacket != null) {
            for (Player viewer : seat.getViewers()) {
                if (viewer == viewerToIgnore) {
                    continue;
                }
                if (seated.isUpsideDown() && PlayerUtil.evaluateGameVersion(viewer, "<=", "1.17.1")) {
                    // Minecraft 1.17.1 and before requires some fixes to be made
                    if (headPacket != null) {
                        if (headPacket_1_17_fix == null) {
                            headPacket_1_17_fix = PacketPlayOutEntityHeadRotationHandle.createNew(entityId, poseFixed.headYaw);
                        }
                        PacketUtil.sendPacket(viewer, headPacket_1_17_fix);
                    }
                    if (lookPacket != null) {
                        if (lookPacket_1_17_fix == null) {
                            lookPacket_1_17_fix = PacketPlayOutEntityLookHandle.createNew(entityId, poseFixed.bodyYaw, poseFixed.headPitch, false);
                        }
                        PacketUtil.sendPacket(viewer, lookPacket_1_17_fix);
                    }
                } else {
                    // Normal sync
                    if (headPacket != null) {
                        PacketUtil.sendPacket(viewer, headPacket);
                    }
                    if (lookPacket != null) {
                        PacketUtil.sendPacket(viewer, lookPacket);
                    }
                }
            }
        }
    }
}
