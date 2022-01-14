package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import java.util.function.Consumer;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Spawns a duplicate of the player and spectates that player. The player is seated into
 * the seat like any other player is as viewed in third-person.
 */
class FirstPersonSpectatedEntityPlayer extends FirstPersonSpectatedEntity {
    // A fake invisible mount that positions the player correctly and moves it around
    private VirtualEntity fakeMount;
    // The entity ID to which the fake player is mounted
    private int mountedVehicleId = -1;
    // Fake player entity currently displayed and riding the cart, being spectated
    private FakeVirtualPlayer fakePlayer;
    // On standby in case it goes beyond 180 pitch
    private FakeVirtualPlayer fakePlayerAlt;
    // Right after the fake player spawns, the head is in the process of rotating from 0.0
    // To prevent a glitch when stepping in we spectate a different entity when starting,
    // and after this rotating has cleaned up spectate the actual player instead.
    private BlindRespawn blindRespawn = null;

    public FirstPersonSpectatedEntityPlayer(CartAttachmentSeat seat, FirstPersonViewSpectator view, Player player) {
        super(seat, view, player);
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        // Spawn the fake player using the FakePlayerSpawner. Initially the player is made invisible,
        // because the head is still rotating awkwardly. We spectate a different entity during this time,
        // and we don't want this player obscuring the view.
        fakePlayer = new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG);
        fakePlayer.updatePosition(eyeTransform);
        fakePlayer.syncPosition(true);
        fakePlayer.spawn(player, new Vector());

        // Also spawn an invisible fake alt player which has the head rotated exactly at pitch 180
        // When the player head pitches beyond 180 we swap the two fake players so that the movement is smooth
        fakePlayerAlt = new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG_SECONDARY);
        fakePlayerAlt.updatePosition(fakePlayer.getPos(), new Vector(
                computeAltPitch(fakePlayer.getYawPitchRoll().getX(), 179.0f),
                fakePlayer.getYawPitchRoll().getY(),
                0.0));
        fakePlayerAlt.syncPosition(true);
        fakePlayerAlt.spawn(player, new Vector());

        // Spawn an invisible holder entity inside which the fake player sits
        // Or, depending on configuration, just mount it in the vehicle directly
        if (!seat.firstPerson.getEyePosition().isDefault()) {
            // Player must be put into the seat so the eye position is at the baseTransform
            prepareFakeMount(eyeTransform, mount -> {
                double y_offset = VirtualEntity.ARMORSTAND_BUTT_OFFSET + VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
                mount.setRelativeOffset(0.0, -y_offset, 0.0);
            });
        } else {
            // Player is put into a vehicle, we don't really care
            mountInVehicle();
        }

        // Initialize, spawn and spectate a temporary entity while the fake player head spins
        blindRespawn = new BlindRespawn();
        blindRespawn.spawn(eyeTransform);
    }

    private void mountInVehicle() {
        this.mountedVehicleId = view.prepareVehicleEntityId();
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
        vmh.mount(this.mountedVehicleId, this.fakePlayer.getEntityId());
    }

    private void prepareFakeMount(Matrix4x4 baseTransform, Consumer<VirtualEntity> manipulator) {
        this.fakeMount = new VirtualEntity(seat.getManager());
        this.fakeMount.setEntityType(EntityType.ARMOR_STAND);
        this.fakeMount.setSyncMode(SyncMode.SEAT);
        this.fakeMount.setUseMinecartInterpolation(seat.isMinecartInterpolation());

        // Put the entity on a fake mount that we move around at an offset
        manipulator.accept(this.fakeMount);
        this.fakeMount.updatePosition(baseTransform);
        this.fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        this.fakeMount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
        this.fakeMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        this.fakeMount.syncPosition(true);
        this.fakeMount.spawn(player, seat.calcMotion());

        // Hide health bar
        PacketUtil.sendPacket(player, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this.fakeMount.getEntityId()));

        // Put the fake player into the invisible entity that moves it around
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
        vmh.mount(this.fakeMount.getEntityId(), this.fakePlayer.getEntityId());

        // Now uses this
        this.mountedVehicleId = this.fakeMount.getEntityId();
    }

    @Override
    public void stop() {
        spectate(-1);

        // If initial blind period, despawn that one too
        if (blindRespawn != null) {
            blindRespawn.despawn();
            blindRespawn = null;
        }

        if (this.mountedVehicleId != -1) {
            VehicleMountController vmc = PlayerUtil.getVehicleMountController(player);
            vmc.unmount(this.mountedVehicleId, this.fakePlayer.getEntityId());
            this.mountedVehicleId = -1;
        }
        if (this.fakeMount != null) {
            this.fakeMount.destroy(player);
        }

        // Despawn both
        this.fakePlayer.destroy(player);
        this.fakePlayerAlt.destroy(player);
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        // While blind, also move the fake spectated entity around
        if (blindRespawn != null) {
            if (System.currentTimeMillis() > blindRespawn.timeout) {
                // Spectate the actual player, despawn the fake blind entity
                // Make the player visible again

                spectate(fakePlayer.getEntityId());
                fakePlayer.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);

                blindRespawn.despawn();
                blindRespawn = null;
            } else {
                blindRespawn.updatePosition(eyeTransform);
            }
        }

        // Move/update the fake player
        this.fakePlayer.updatePosition(eyeTransform);

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(this.fakePlayer.getSyncPitch(), this.fakePlayer.getLivePitch())) {
            // Remount and spectate the new player
            VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
            vmh.unmount(this.mountedVehicleId, this.fakePlayer.getEntityId());
            vmh.mount(this.mountedVehicleId, this.fakePlayerAlt.getEntityId());
            spectate(this.fakePlayerAlt.getEntityId());

            // Make previous player invisible, make new player visible
            fakePlayer.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            fakePlayer.syncMetadata(); // do early
            fakePlayerAlt.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
            fakePlayerAlt.syncMetadata(); // do early

            // Swap them out, continue working with alt
            {
                FakeVirtualPlayer tmp = this.fakePlayer;
                this.fakePlayer = this.fakePlayerAlt;
                this.fakePlayerAlt = tmp;
            }

            // Give the fake player full sync pitch
            this.fakePlayer.updatePosition(eyeTransform);
        }

        // Calculate what new alt-pitch should be used. This swaps over at the 180-degree mark
        {
            float newAltPitch = computeAltPitch(this.fakePlayer.getYawPitchRoll().getX(),
                                                this.fakePlayerAlt.getLivePitch());
            boolean isAltPitchDifferent = (newAltPitch != this.fakePlayerAlt.getLivePitch());

            // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
            this.fakePlayerAlt.updatePosition(fakePlayer.getPos(), new Vector(
                    newAltPitch, this.fakePlayer.getYawPitchRoll().getY(), 0.0));

            if (isAltPitchDifferent) {
                // We cannot safely rotate between these two - it requires a respawn to do this quickly
                this.fakePlayerAlt.destroy(player);
                this.fakePlayerAlt.syncPosition(true);
                this.fakePlayerAlt.spawn(player, new Vector());
            }
        }

        // Move the vehicle itself, which moves the fake player around
        if (this.fakeMount != null) {
            this.fakeMount.updatePosition(eyeTransform);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (this.fakeMount != null) {
            this.fakeMount.syncPosition(absolute);
        }

        this.fakePlayer.syncPosition(absolute);
        this.fakePlayerAlt.syncPosition(absolute);

        if (blindRespawn != null) {
            blindRespawn.syncPosition(absolute);
        }
    }

    /**
     * Short-lived entities used for spectating while the player head is rotating
     * into position.
     */
    private class BlindRespawn {
        public final VirtualEntity spectated;
        public final long timeout;

        public BlindRespawn() {
            // We spectate an invisible armorstand that has MARKER set
            // This causes the spectator to view from 0/0/0, avoiding having to do any extra offsets
            spectated = new VirtualEntity(seat.getManager());
            spectated.setEntityType(EntityType.VILLAGER);
            spectated.setSyncMode(SyncMode.NORMAL);
            spectated.setUseMinecartInterpolation(seat.isMinecartInterpolation());
            spectated.setRelativeOffset(0.0, -1.62, 0.0);
            spectated.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            spectated.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            timeout = System.currentTimeMillis() + (6 * 50); // 6 (client) ticks
        }

        public void spawn(Matrix4x4 eyeTransform) {
            spectated.updatePosition(eyeTransform);
            spectated.syncPosition(true);
            spectated.spawn(player, seat.calcMotion());
            spectate(spectated.getEntityId());
        }

        public void despawn() {
            spectated.destroy(player);
        }

        public void updatePosition(Matrix4x4 eyeTransform) {
            spectated.updatePosition(eyeTransform);
        }

        public void syncPosition(boolean absolute) {
            spectated.syncPosition(absolute);
        }
    }

    /**
     * VirtualEntity spawned using {@link FakePlayerSpawner}
     */
    private static class FakeVirtualPlayer extends VirtualEntity {
        public final FakePlayerSpawner fakePlayer;

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakeplayer) {
            super(manager);
            this.fakePlayer = fakeplayer;
            this.setEntityType(EntityType.PLAYER);
            this.setSyncMode(SyncMode.NORMAL);
        }

        @Override
        protected void sendSpawnPackets(Player viewer, Vector motion) {
            FakePlayerSpawner.FakePlayerPosition orientation = FakePlayerSpawner.FakePlayerPosition.create(
                    this.getPosX(), this.getPosY(), this.getPosZ(),
                    (float) this.getYawPitchRoll().getY(),
                    this.getLivePitch(),
                    (float) this.getYawPitchRoll().getY());

            addViewerWithoutSpawning(viewer);
            fakePlayer.spawnPlayer(viewer, viewer, this.getEntityId(), orientation, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                FakeVirtualPlayer.this.metaData = meta;
            });
        }
    }
}
