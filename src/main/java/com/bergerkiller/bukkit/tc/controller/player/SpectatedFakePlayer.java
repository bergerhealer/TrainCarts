package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.PitchSwappedEntity;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Controls spawning a fake player, spectating it and synchronizing first-person
 * appearance and look direction.
 */
public class SpectatedFakePlayer {
    private static final int[] NO_MOUNTS = new int[0];

    private final AttachmentViewer player;
    private final AttachmentManager manager;
    private PitchSwappedEntity<FakeVirtualPlayer> fakePlayer = null;
    private BlindRespawn blindRespawn = null;
    private int[] mountedEntityIds = NO_MOUNTS;
    private boolean useMinecartInterpolation = false;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;
    private ItemStack skullItem = null;

    public SpectatedFakePlayer(AttachmentViewer player) {
        this(player, null);
    }

    public SpectatedFakePlayer(AttachmentViewer player, AttachmentManager manager) {
        this.player = player;
        this.manager = manager;
    }

    public void setUseMinecartInterpolation(boolean useMinecartInterpolation) {
        this.useMinecartInterpolation = useMinecartInterpolation;
    }

    public void setPlayerOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public void start(Matrix4x4 transform, Vector motion) {
        if (fakePlayer != null) {
            stop();
        }
        if (manager == null) {
            throw new IllegalStateException("No AttachmentManager configured");
        }
        Matrix4x4 syncTransform = transformForSync(transform);

        this.skullItem = isHeadOnly() ? SeatedEntityHead.createSkullItem(player.getPlayer()) : null;
        this.fakePlayer = PitchSwappedEntity.create(player,
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG),
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG_SECONDARY),
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG_TERTIARY));
        this.fakePlayer.beforeSwap(swapped -> {
            if (blindRespawn == null) {
                if (isHeadOnly()) {
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, null));
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            swapped.getEntityId(), EquipmentSlot.HEAD, skullItem));
                } else {
                    fakePlayer.swapVisibility(swapped);
                }
            }
        });
        this.fakePlayer.spawn(syncTransform, motion);

        this.blindRespawn = new BlindRespawn(manager);
        this.blindRespawn.spawn(syncTransform, motion);
    }

    public void stop() {
        if (blindRespawn != null) {
            blindRespawn.despawn();
            blindRespawn = null;
        }
        unmount();
        if (fakePlayer != null) {
            fakePlayer.destroy();
            fakePlayer = null;
        }
        mountedEntityIds = NO_MOUNTS;
        skullItem = null;
    }

    public void mountSingle(int mountEntityId) {
        checkStarted();

        VehicleMountController vmc = player.getVehicleMountController();
        fakePlayer.entity.mount(vmc, mountEntityId);
        fakePlayer.entityAlt.mount(vmc, mountEntityId);
        fakePlayer.entityAltFlip.mount(vmc, mountEntityId);
        mountedEntityIds = new int[] { mountEntityId, mountEntityId, mountEntityId };
    }

    public void mountMultiple(int mountEntityIdA, int mountEntityIdB, int mountEntityIdC) {
        checkStarted();

        VehicleMountController vmc = player.getVehicleMountController();
        fakePlayer.entity.mount(vmc, mountEntityIdA);
        fakePlayer.entityAlt.mount(vmc, mountEntityIdB);
        fakePlayer.entityAltFlip.mount(vmc, mountEntityIdC);
        mountedEntityIds = new int[] { mountEntityIdA, mountEntityIdB, mountEntityIdC };
    }

    public void unmount() {
        if (fakePlayer == null || mountedEntityIds.length != 3) {
            return;
        }

        VehicleMountController vmc = player.getVehicleMountController();
        fakePlayer.entity.unmount(vmc);
        fakePlayer.entityAlt.unmount(vmc);
        fakePlayer.entityAltFlip.unmount(vmc);
        mountedEntityIds = NO_MOUNTS;
    }

    public void updatePosition(Matrix4x4 transform) {
        checkStarted();
        Matrix4x4 syncTransform = transformForSync(transform);

        if (blindRespawn != null) {
            if (System.currentTimeMillis() > blindRespawn.timeout) {
                fakePlayer.spectateFrom(blindRespawn.spectated.getEntityId());
                if (isHeadOnly()) {
                    player.sendSilent(Util.createPlayerEquipmentPacket(
                            fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, skullItem));
                } else {
                    fakePlayer.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, false);
                }
                blindRespawn.despawn();
                blindRespawn = null;
            } else {
                blindRespawn.updatePosition(syncTransform);
            }
        }

        fakePlayer.updatePosition(syncTransform);
    }

    public void syncPosition(boolean absolute) {
        checkStarted();

        fakePlayer.syncPosition(absolute);
        if (blindRespawn != null) {
            blindRespawn.syncPosition(absolute);
        }
    }

    public VirtualEntity getCurrentEntity() {
        checkStarted();
        return fakePlayer.entity;
    }

    private void checkStarted() {
        if (fakePlayer == null) {
            throw new IllegalStateException("Spectated fake player is not started");
        }
    }

    protected Matrix4x4 transformForSync(Matrix4x4 transform) {
        return transform;
    }

    protected boolean isHeadOnly() {
        return false;
    }

    private class BlindRespawn {
        public final VirtualEntity spectated;
        public final long timeout;

        public BlindRespawn(AttachmentManager manager) {
            this.spectated = new VirtualEntity(manager);
            this.spectated.setEntityType(EntityType.VILLAGER);
            this.spectated.setSyncMode(SyncMode.NORMAL);
            this.spectated.setUseMinecartInterpolation(useMinecartInterpolation);
            this.spectated.setRelativeOffset(0.0, -VirtualEntity.PLAYER_STANDING_EYE_HEIGHT, 0.0);
            this.spectated.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this.spectated.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.timeout = System.currentTimeMillis() + (6 * 50);
        }

        public void spawn(Matrix4x4 eyeTransform, Vector motion) {
            spectated.updatePosition(eyeTransform);
            spectated.syncPosition(true);
            spectated.spawn(player, motion);
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

    private class FakeVirtualPlayer extends VirtualEntity {
        private final FakePlayerSpawner fakePlayerSpawner;
        private int mountedVehicleId = -1;

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakePlayerSpawner) {
            super(manager);
            this.fakePlayerSpawner = fakePlayerSpawner;
            this.setEntityType(EntityType.PLAYER);
            this.setSyncMode(SyncMode.NORMAL);
            this.setUseMinecartInterpolation(useMinecartInterpolation);
            this.setRelativeOffset(offsetX, offsetY, offsetZ);
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
        protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
            FakePlayerSpawner.FakePlayerPosition orientation = FakePlayerSpawner.FakePlayerPosition.create(
                    this.getPosX(), this.getPosY(), this.getPosZ(),
                    (float) this.getYawPitchRoll().getY(),
                    this.getLivePitch(),
                    (float) this.getYawPitchRoll().getY());

            addViewerWithoutSpawning(viewer);
            fakePlayerSpawner.spawnPlayer(viewer, viewer.getPlayer(), this.getEntityId(), orientation, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                FakeVirtualPlayer.this.metaData = meta;
            });
        }
    }
}
