package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

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
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Spawns a duplicate of the player and spectates that player. The player is seated into
 * the seat like any other player is as viewed in third-person.
 */
public class FirstPersonSpectatedEntityPlayer extends FirstPersonSpectatedEntity {
    private VirtualEntity fakeMount;
    private VirtualEntity fakePlayer;
    private VirtualEntity fakePlayerAlt; // On standby in case it goes beyond 180 pitch
    // Right after the fake player spawns, the head is in the process of rotating from 0.0
    // To prevent a glitch when stepping in we spectate a different entity when starting,
    // and after this rotating has cleaned up spectate the actual player instead.
    private BlindRespawn blindRespawn = null;

    public FirstPersonSpectatedEntityPlayer(CartAttachmentSeat seat, Player player) {
        super(seat, player);
    }

    @Override
    public void start() {
        // Prepare a Player virtual entity to get position information, do not yet spawn
        this.fakePlayer = new VirtualEntity(seat.getManager());
        this.fakePlayer.setEntityType(EntityType.PLAYER);
        this.fakePlayer.setSyncMode(SyncMode.NORMAL);
        this.fakePlayer.updatePosition(seat.getTransform());
        this.fakePlayer.syncPosition(true);

        // Spawn the fake player using the FakePlayerSpawner. Initially the player is made invisible,
        // because the head is still rotating awkwardly. We spectate a different entity during this time,
        // and we don't want this player obscuring the view.
        FakePlayerSpawner.FakePlayerOrientation orientation = FakePlayerSpawner.FakePlayerOrientation.create(
                (float) this.fakePlayer.getYawPitchRoll().getY(),
                (float) this.fakePlayer.getYawPitchRoll().getX(),
                (float) this.fakePlayer.getYawPitchRoll().getY());
        {
            this.fakePlayer.addViewerWithoutSpawning(player);
            FakePlayerSpawner.NO_NAMETAG.spawnPlayer(player, player, this.fakePlayer.getEntityId(), orientation, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                fakePlayer.useMetadata(meta);
            });
        }

        // Also spawn an invisible fake alt player which has the head rotated exactly at pitch 180
        // When the player head pitches beyond 180 we spam the two fake players so that the movement is smooth
        {
            FakePlayerSpawner.FakePlayerOrientation orientationBeyond = FakePlayerSpawner.FakePlayerOrientation.create(
                    orientation.getYaw(), 179.0f, orientation.getHeadYaw());

            this.fakePlayerAlt = new VirtualEntity(seat.getManager());
            this.fakePlayerAlt.setEntityType(EntityType.PLAYER);
            this.fakePlayerAlt.setSyncMode(SyncMode.NORMAL);
            this.fakePlayerAlt.updatePosition(seat.getTransform(), new Vector(orientationBeyond.getPitch(), orientationBeyond.getHeadYaw(), 0.0));
            this.fakePlayerAlt.syncPosition(true);

            this.fakePlayerAlt.addViewerWithoutSpawning(player);
            FakePlayerSpawner.NO_NAMETAG_SECONDARY.spawnPlayer(player, player, this.fakePlayerAlt.getEntityId(), orientationBeyond, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                fakePlayerAlt.useMetadata(meta);
            });
        }

        // Spawn an invisible holder entity inside which the fake player sits
        {
            this.fakeMount = new VirtualEntity(seat.getManager());
            this.fakeMount.setEntityType(EntityType.ARMOR_STAND);
            this.fakeMount.setSyncMode(SyncMode.SEAT);
            this.fakeMount.setRelativeOffset(0.0, -VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET, 0.0);

            // Put the entity on a fake mount that we move around at an offset
            this.fakeMount.updatePosition(seat.getTransform(), new Vector(0.0, (double) orientation.getYaw(), 0.0));
            this.fakeMount.syncPosition(true);
            this.fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this.fakeMount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            this.fakeMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
            this.fakeMount.spawn(player, seat.calcMotion());

            // Hide health bar
            PacketUtil.sendPacket(player, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this.fakeMount.getEntityId()));
        }

        // Put the player into the invisible entity that moves it around
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
        vmh.mount(this.fakeMount.getEntityId(), this.fakePlayer.getEntityId());

        // Initialize, spawn and spectate a temporary entity while the fake player head spins
        blindRespawn = new BlindRespawn();
        blindRespawn.spawn();        
    }

    @Override
    public void stop() {
        spectate(-1);

        // If initial blind period, despawn that one too
        if (blindRespawn != null) {
            blindRespawn.despawn();
            blindRespawn = null;
        }

        // Unmount fake player from mount then despawn both
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
        vmh.unmount(this.fakeMount.getEntityId(), this.fakePlayer.getEntityId());
        this.fakePlayer.destroy(player);
        this.fakePlayerAlt.destroy(player);
        this.fakeMount.destroy(player);
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        // While blind, also move the fake spectated entity around
        if (blindRespawn != null) {
            if (--blindRespawn.timer == 0) {
                // Spectate the actual player, despawn the fake blind entity
                // Make the player visible again

                spectate(fakePlayer.getEntityId());
                fakePlayer.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);

                blindRespawn.despawn();
                blindRespawn = null;
            } else {
                blindRespawn.updatePosition(transform);
            }
        }

        // Move/update the fake player
        this.fakePlayer.updatePosition(transform);

        // If pitch went from < 180 to > 180 or other way around, we must swap fake and alt
        if (Util.isProtocolRotationGlitched(this.fakePlayer.getSyncPitch(), this.fakePlayer.getLivePitch())) {
            System.out.println("SWAPPED WAS " + fakePlayer.getSyncPitch() + " IS NOW " + fakePlayer.getLivePitch());
            
            System.out.println("ALT IS PREPPED FOR " + fakePlayerAlt.getLivePitch());
            
            // Remount and spectate the new player
            VehicleMountController vmh = PlayerUtil.getVehicleMountController(player);
            vmh.unmount(this.fakeMount.getEntityId(), this.fakePlayer.getEntityId());
            vmh.mount(this.fakeMount.getEntityId(), this.fakePlayerAlt.getEntityId());
            
            /*
            if (blindRespawn == null) {
                blindRespawn = new BlindRespawn();
                blindRespawn.spawn();
            }
            */
            
            spectate(this.fakePlayerAlt.getEntityId());

            // Make previous player invisible, make new player visible
            fakePlayer.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            fakePlayer.syncMetadata(); // do early
            fakePlayerAlt.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
            fakePlayerAlt.syncMetadata(); // do early

            // Swap them out, continue working with alt
            {
                VirtualEntity tmp = this.fakePlayer;
                this.fakePlayer = this.fakePlayerAlt;
                this.fakePlayerAlt = tmp;
            }

            // Give the fake player full sync pitch
            this.fakePlayer.updatePosition(transform);
        }

        // Keep the alt nearby ready to be used. Keep head yaw in check so no weird spazzing out happens there
        this.fakePlayerAlt.updatePosition(new Vector(fakePlayer.getPosX(), fakePlayer.getPosY(), fakePlayer.getPosZ()), new Vector(
                179.0, this.fakePlayer.getYawPitchRoll().getY(), 0.0));

        // Move the vehicle itself, which moves the fake player around
        this.fakeMount.updatePosition(transform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        this.fakeMount.syncPosition(absolute);
        
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
        //public final VirtualEntity mount;
        public final VirtualEntity spectated;
        public int timer = 6; //TODO: Millis

        public BlindRespawn() {
            /*
            mount = new VirtualEntity(seat.getManager());
            mount.setEntityType(EntityType.ARMOR_STAND);
            mount.setSyncMode(SyncMode.SEAT);
            mount.setRelativeOffset(0.0, VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET + DebugUtil.getDoubleValue("c", 1.0), 0.0);

            // Put the entity on a fake mount that we move around at an offset
            mount.updatePosition(seat.getTransform(), new Vector(0.0, (double) orientation.getYaw(), 0.0));
            mount.syncPosition(true);
            mount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            mount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            mount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            mount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    //EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    //EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
            */

            // We spectate an invisible armorstand that has MARKER set
            // This causes the spectator to view from 0/0/0, avoiding having to do any extra offsets
            spectated = new VirtualEntity(seat.getManager());
            spectated.setEntityType(EntityType.VILLAGER);
            spectated.setSyncMode(SyncMode.NORMAL);
            spectated.setRelativeOffset(0.0, -0.62, 0.0);
            spectated.updatePosition(seat.getTransform());
            spectated.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            spectated.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            spectated.syncPosition(true);
        }

        public void spawn() {
            //mount.spawn(player, seat.calcMotion());
            spectated.spawn(player, seat.calcMotion());
            //PlayerUtil.getVehicleMountController(player).mount(mount.getEntityId(), spectated.getEntityId());

            spectate(spectated.getEntityId());
        }

        public void despawn() {
            //PlayerUtil.getVehicleMountController(player).unmount(mount.getEntityId(), spectated.getEntityId());
            //mount.destroy(player);
            spectated.destroy(player);
        }

        public void updatePosition(Matrix4x4 transform) {
            if (timer == 15) {
                //spectate(spectated.getEntityId());
            }
            
            spectated.updatePosition(transform);
        }

        public void syncPosition(boolean absolute) {
            spectated.syncPosition(absolute);
        }
    }
}
