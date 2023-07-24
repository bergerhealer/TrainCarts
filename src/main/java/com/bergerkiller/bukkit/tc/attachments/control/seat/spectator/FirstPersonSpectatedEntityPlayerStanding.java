package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import com.bergerkiller.bukkit.tc.Util;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Spawns a duplicate of the player and spectates that player. The player is left standing
 * up and is moved around without a vehicle mount.
 */
class FirstPersonSpectatedEntityPlayerStanding extends FirstPersonSpectatedEntity {
    // Fake player entity currently displayed and riding the cart, being spectated
    private PitchSwappedEntity<FakeVirtualPlayer> fakePlayer;
    // Right after the fake player spawns, the head is in the process of rotating from 0.0
    // To prevent a glitch when stepping in we spectate a different entity when starting,
    // and after this rotating has cleaned up spectate the actual player instead.
    private BlindRespawn blindRespawn = null;
    // Used in HEAD mode for the skull item
    private final ItemStack skullItem;

    public FirstPersonSpectatedEntityPlayerStanding(CartAttachmentSeat seat, FirstPersonViewSpectator view, AttachmentViewer player) {
        super(seat, view, player);

        if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
            skullItem = SeatedEntityHead.createSkullItem(player.getPlayer());
        } else {
            skullItem = null;
        }
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        // Spawn the fake player using the FakePlayerSpawner. Initially the player is made invisible,
        // because the head is still rotating awkwardly. We spectate a different entity during this time,
        // and we don't want this player obscuring the view.
        fakePlayer = PitchSwappedEntity.create(player,
                new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG),
                new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG_SECONDARY),
                new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG_TERTIARY));
        fakePlayer.beforeSwap(swapped -> {
            // Ensure mounted the new entity is mounted, a previous destroy may have broken it
            //vmc.mount(fakePlayer.entityAlt.mountedVehicleId, fakePlayer.entityAlt.getEntityId());

            // If not still in the blind respawn mode, swap visibility too
            if (blindRespawn == null) {
                if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
                    // Swap which entity holds the player skull
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, null));
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            swapped.getEntityId(), EquipmentSlot.HEAD, skullItem));
                } else {
                    // Swap visibility
                    fakePlayer.swapVisibility(swapped);
                }
            }
        });
        fakePlayer.spawn(eyeTransform, seat.calcMotion());

        // Initialize, spawn and spectate a temporary entity while the fake player head spins
        blindRespawn = new BlindRespawn();
        blindRespawn.spawn(eyeTransform);
    }

    @Override
    public void stop() {
        // If initial blind period, despawn that one too
        if (blindRespawn != null) {
            blindRespawn.despawn();
            blindRespawn = null;
        }

        // Despawn fake player
        this.fakePlayer.destroy();
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        // While blind, also move the fake spectated entity around
        if (blindRespawn != null) {
            if (System.currentTimeMillis() > blindRespawn.timeout) {
                // Spectate the actual player, despawn the fake blind entity
                // Make the player visible again
                fakePlayer.spectateFrom(blindRespawn.spectated.getEntityId());
                if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
                    // Make only the head visible
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, skullItem));
                } else {
                    // Make entire body visible
                    fakePlayer.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
                }

                blindRespawn.despawn();
                blindRespawn = null;
            } else {
                blindRespawn.updatePosition(eyeTransform);
            }
        }

        // Move/update the fake player
        this.fakePlayer.updatePosition(eyeTransform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        this.fakePlayer.syncPosition(absolute);

        if (blindRespawn != null) {
            blindRespawn.syncPosition(absolute);
        }
    }

    @Override
    public VirtualEntity getCurrentEntity() {
        return fakePlayer.entity;
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
            //
            // Ignore the above. Armorstands use a different FOV effect.
            // So we use a villager instead, which uses the same FOV. Needs an offset.
            spectated = new VirtualEntity(seat.getManager());
            spectated.setEntityType(EntityType.VILLAGER);
            spectated.setSyncMode(SyncMode.NORMAL);
            spectated.setUseMinecartInterpolation(seat.isMinecartInterpolation());
            spectated.setRelativeOffset(0.0, -VirtualEntity.PLAYER_STANDING_EYE_HEIGHT, 0.0);
            spectated.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            spectated.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            timeout = System.currentTimeMillis() + (6 * 50); // 6 (client) ticks
        }

        public void spawn(Matrix4x4 eyeTransform) {
            spectated.updatePosition(eyeTransform);
            spectated.syncPosition(true);
            spectated.spawn(player, seat.calcMotion());
            spectated.forceSyncRotation();
            player.getVehicleMountController().startSpectating(spectated.getEntityId());
        }

        public void despawn() {
            player.getVehicleMountController().stopSpectating(spectated.getEntityId());
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
        // Logic for spawning a new fake player entity
        public final FakePlayerSpawner fakePlayer;

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakeplayer) {
            super(manager);
            this.fakePlayer = fakeplayer;
            this.setEntityType(EntityType.PLAYER);
            this.setSyncMode(SyncMode.NORMAL);
            this.setRelativeOffset(0.0, -VirtualEntity.PLAYER_STANDING_EYE_HEIGHT, 0.0);
        }

        @Override
        protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
            FakePlayerSpawner.FakePlayerPosition orientation = FakePlayerSpawner.FakePlayerPosition.create(
                    this.getPosX(), this.getPosY(), this.getPosZ(),
                    (float) this.getYawPitchRoll().getY(),
                    this.getLivePitch(),
                    (float) this.getYawPitchRoll().getY());

            addViewerWithoutSpawning(viewer);
            fakePlayer.spawnPlayer(viewer, viewer.getPlayer(), this.getEntityId(), orientation, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                FakeVirtualPlayer.this.metaData = meta;
            });
        }
    }
}
