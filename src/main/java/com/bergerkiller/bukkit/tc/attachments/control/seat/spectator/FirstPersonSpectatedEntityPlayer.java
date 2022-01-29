package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity.DisplayMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Spawns a duplicate of the player and spectates that player. The player is seated into
 * the seat like any other player is as viewed in third-person.
 */
class FirstPersonSpectatedEntityPlayer extends FirstPersonSpectatedEntity {
    private static final VirtualEntity[] NO_FAKE_MOUNTS = new VirtualEntity[0];

    // A fake invisible mount that positions the player correctly and moves it around
    // One mount on newer versions of Minecraft that supports multiple passengers per mount
    // Two mounts for older versions.
    // 0 mounts if mounted directly in the parent vehicle.
    private VirtualEntity[] fakeMounts = NO_FAKE_MOUNTS;
    // Fake player entity currently displayed and riding the cart, being spectated
    private PitchSwappedEntity<FakeVirtualPlayer> fakePlayer;
    // Right after the fake player spawns, the head is in the process of rotating from 0.0
    // To prevent a glitch when stepping in we spectate a different entity when starting,
    // and after this rotating has cleaned up spectate the actual player instead.
    private BlindRespawn blindRespawn = null;
    // Used in HEAD mode for the skull item
    private final ItemStack skullItem;

    public FirstPersonSpectatedEntityPlayer(CartAttachmentSeat seat, FirstPersonViewSpectator view, VehicleMountController vmc) {
        super(seat, view, vmc);

        if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
            skullItem = SeatedEntityHead.createSkullItem(vmc.getPlayer());
        } else {
            skullItem = null;
        }
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        // Spawn the fake player using the FakePlayerSpawner. Initially the player is made invisible,
        // because the head is still rotating awkwardly. We spectate a different entity during this time,
        // and we don't want this player obscuring the view.
        fakePlayer = PitchSwappedEntity.create(vmc,
                new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG),
                new FakeVirtualPlayer(seat.getManager(), FakePlayerSpawner.NO_NAMETAG_SECONDARY));
        fakePlayer.beforeSwap(() -> {
            // Ensure mounted the new entity is mounted, a previous destroy may have broken it
            //vmc.mount(fakePlayer.entityAlt.mountedVehicleId, fakePlayer.entityAlt.getEntityId());

            // If not still in the blind respawn mode, swap visibility too
            if (blindRespawn == null) {
                if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
                    // Swap which entity holds the player skull
                    PacketUtil.sendPacket(player, PacketPlayOutEntityEquipmentHandle.createNew(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, null), false);
                    PacketUtil.sendPacket(player, PacketPlayOutEntityEquipmentHandle.createNew(
                            fakePlayer.entityAlt.getEntityId(), EquipmentSlot.HEAD, skullItem), false);
                } else {
                    // Swap visibility
                    fakePlayer.swapVisibility();
                }
            }
        });
        fakePlayer.spawn(eyeTransform, seat.calcMotion());

        // Spawn an invisible holder entity inside which the fake player sits
        // Or, depending on configuration, just mount it in the vehicle directly
        // Or, on older versions of Minecraft, where multiple passengers per mount doesn't work
        if (!seat.firstPerson.getEyePosition().isDefault() ||
            seat.seated.getDisplayMode() == DisplayMode.HEAD ||
            seat.seated.getDisplayMode() == DisplayMode.INVISIBLE ||
            !PacketPlayOutMountHandle.T.isAvailable()
        ) {
            // Player must be put into the seat so the eye position is at the baseTransform
            prepareFakeMounts(eyeTransform);
        } else {
            // Player is put into a vehicle, we don't really care
            mountInVehicle();
        }

        // Initialize, spawn and spectate a temporary entity while the fake player head spins
        blindRespawn = new BlindRespawn();
        blindRespawn.spawn(eyeTransform);
    }

    private void mountInVehicle() {
        int vehicleId = view.prepareVehicleEntityId();
        this.fakePlayer.entity.mount(vmc, vehicleId);
        this.fakePlayer.entityAlt.mount(vmc, vehicleId);
    }

    private void prepareFakeMounts(Matrix4x4 baseTransform) {
        if (PacketPlayOutMountHandle.T.isAvailable()) {
            // Only one fake mount needed
            VirtualEntity fakeMount = createFakeMount(baseTransform);

            // Mount both players inside
            this.fakePlayer.entity.mount(vmc, fakeMount.getEntityId());
            this.fakePlayer.entityAlt.mount(vmc, fakeMount.getEntityId());

            this.fakeMounts = new VirtualEntity[] { fakeMount };
        } else {
            // Spawn two mounts, put players in each
            VirtualEntity[] fakeMounts = new VirtualEntity[] {
                    createFakeMount(baseTransform),  createFakeMount(baseTransform)
            };

            // Mount both players inside
            this.fakePlayer.entity.mount(vmc, fakeMounts[0].getEntityId());
            this.fakePlayer.entityAlt.mount(vmc, fakeMounts[1].getEntityId());

            this.fakeMounts = fakeMounts;
        }
    }

    private VirtualEntity createFakeMount(Matrix4x4 baseTransform) {
        VirtualEntity fakeMount = new VirtualEntity(seat.getManager());
        fakeMount.setEntityType(EntityType.ARMOR_STAND);
        fakeMount.setSyncMode(SyncMode.SEAT);
        fakeMount.setUseMinecartInterpolation(seat.isMinecartInterpolation());

        // Put the entity on a fake mount that we move around at an offset
        double y_offset = VirtualEntity.ARMORSTAND_BUTT_OFFSET + VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
        fakeMount.setRelativeOffset(0.0, -y_offset, 0.0);
        fakeMount.updatePosition(baseTransform);
        fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        fakeMount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
        fakeMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        fakeMount.syncPosition(true);
        fakeMount.spawn(player, seat.calcMotion());

        // Hide health bar
        PacketUtil.sendPacket(player, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(fakeMount.getEntityId()));

        return fakeMount;
    }

    @Override
    public void stop() {
        // If initial blind period, despawn that one too
        if (blindRespawn != null) {
            blindRespawn.despawn();
            blindRespawn = null;
        }

        this.fakePlayer.entity.unmount(vmc);
        this.fakePlayer.entityAlt.unmount(vmc);

        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.destroy(player);
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
                    PacketUtil.sendPacket(player, PacketPlayOutEntityEquipmentHandle.createNew(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, skullItem), false);
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

        // Move the vehicle itself, which moves the fake player around
        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.updatePosition(eyeTransform);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.syncPosition(absolute);
        }

        this.fakePlayer.syncPosition(absolute);

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
            spectated.forceSyncRotation();
            Util.startSpectating(vmc, spectated.getEntityId());
        }

        public void despawn() {
            Util.stopSpectating(vmc, spectated.getEntityId());
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

        // The entity ID to which the fake player is mounted
        public int mountedVehicleId = -1;

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakeplayer) {
            super(manager);
            this.fakePlayer = fakeplayer;
            this.setEntityType(EntityType.PLAYER);
            this.setSyncMode(SyncMode.NORMAL);
            this.mountedVehicleId = -1;
        }

        public void mount(VehicleMountController vmc, int mountedVehicleId) {
            this.mountedVehicleId = mountedVehicleId;
            vmc.mount(mountedVehicleId, this.getEntityId());
        }

        public void unmount(VehicleMountController vmc) {
            if (this.mountedVehicleId != -1) {
                vmc.unmount(this.mountedVehicleId, this.getEntityId());
                this.mountedVehicleId = -1;
            }
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
