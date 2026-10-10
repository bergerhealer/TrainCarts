package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualOffScreenMount;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntityHead;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.PitchSwappedEntity;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerPositionPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundSetCameraPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Controls spawning a fake player, spectating it and synchronizing first-person
 * appearance and look direction.
 */
public class SpectatedFakePlayer {
    private static final int[] NO_MOUNTS = new int[0];

    private final TrainCartsAttachmentViewer player;
    private final AttachmentManager manager;
    private PitchSwappedEntity<FakeVirtualPlayer> fakePlayer = null;
    private BlindRespawn blindRespawn = null;
    private VirtualEntity playerMount = null;
    private int[] mountedEntityIds = NO_MOUNTS;
    private boolean useMinecartInterpolation = false;
    private boolean forceAbsoluteSync = false;
    /** If true, then when the fake player is spawned head is already aligned the right way (since 1.20.2) */
    private final boolean canSpawnWithoutHeadAnimation;
    private Runnable onRealPlayerPositionSynchronized = null;
    private final EnumMap<EquipmentSlot, ItemStack> equipmentState = new EnumMap<>(EquipmentSlot.class);
    private final Task equipmentUpdateTask;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;
    private ItemStack skullItem = null;

    public SpectatedFakePlayer(TrainCartsAttachmentViewer player) {
        this(player, null);
    }

    public SpectatedFakePlayer(TrainCartsAttachmentViewer player, AttachmentManager manager) {
        this.player = player;
        this.manager = manager;
        this.equipmentUpdateTask = new Task(player.getTrainCarts()) {
            @Override
            public void run() {
                updateMirroredEquipment();
            }
        };
        this.canSpawnWithoutHeadAnimation = Common.hasCapability("Common:PlayerYawAPIFixes")
                && Common.evaluateMCVersion(">=", "1.20.2")
                && player.evaluateGameVersion(">=", "1.20.2");
        this.player.onSpectatedFakePlayerCreated(this);
    }

    public void setUseMinecartInterpolation(boolean useMinecartInterpolation) {
        this.useMinecartInterpolation = useMinecartInterpolation;
    }

    public void setPlayerOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public void setForceAbsoluteSync(boolean forceAbsoluteSync) {
        this.forceAbsoluteSync = forceAbsoluteSync;
    }

    /**
     * Sets whether the real player is mounted onto an invisible off-screen seat while spectating
     * this fake player, so incoming look packets can be tracked reliably. Disabled by default.
     * Sets a callback run after the real player has been synchronized to the off-screen seat
     * position. Used to enable look packet tracking after the client has acknowledged teleport.
     *
     * @param callback Callback to run, null to disable
     */
    public void setOnRealPlayerPositionSynchronized(Runnable callback) {
        this.onRealPlayerPositionSynchronized = callback;
    }

    public void start(Matrix4x4 transform, Vector motion) {
        if (fakePlayer != null) {
            stop();
        }
        Matrix4x4 syncTransform = transformForSync(transform);

        this.skullItem = isHeadOnly() ? SeatedEntityHead.createSkullItem(player.getPlayer()) : null;
        this.fakePlayer = PitchSwappedEntity.create(player,
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG, !isHeadOnly() && canSpawnWithoutHeadAnimation),
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG_SECONDARY, false),
                new FakeVirtualPlayer(manager, FakePlayerSpawner.NO_NAMETAG_TERTIARY, false));
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

        if (canSpawnWithoutHeadAnimation) {
            // No head animation occurs in newer versions, spawn visible and spectate immediately
            if (isHeadOnly()) {
                player.sendSilent(Util.createPlayerEquipmentPacket(
                        fakePlayer.entity.getEntityId(), EquipmentSlot.HEAD, skullItem));
            }
            fakePlayer.spectate();
        } else {
            // Use BlindRespawn workaround to avoid head animation glitch
            this.blindRespawn = new BlindRespawn(manager, isHeadOnly());
            this.blindRespawn.spawn(syncTransform, motion);
        }

        this.startRealPlayerMount(syncTransform);
        if (!isHeadOnly()) {
            initializeMirroredEquipmentState();
            equipmentUpdateTask.start(1, 1);
        }
        this.player.onSpectatedFakePlayerActive(this, true);
    }

    public void stop() {
        equipmentUpdateTask.stop();
        stopRealPlayerMount();
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
        equipmentState.clear();
        this.player.onSpectatedFakePlayerActive(this, false);
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
        if (playerMount != null) {
            playerMount.updatePosition(syncTransform);
        }
    }

    public void syncPosition(boolean absolute) {
        checkStarted();
        if (forceAbsoluteSync) {
            absolute = true;
        }

        fakePlayer.syncPosition(absolute);
        if (blindRespawn != null) {
            blindRespawn.syncPosition(absolute);
        }
        if (playerMount != null) {
            playerMount.syncPosition(absolute);
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

    private void startRealPlayerMount(Matrix4x4 syncTransform) {
        if (playerMount == null) {
            playerMount = new VirtualOffScreenMount(manager, player.getPlayer().getWorld());
        }

        playerMount.updatePosition(syncTransform);
        playerMount.syncPosition(true);
        playerMount.spawn(player, new Vector());

        final Runnable callback = onRealPlayerPositionSynchronized;
        final Vector pos = playerMount.getSyncPos();
        player.getClientSynchronizer().synchronize(teleportId -> ClientboundPlayerPositionPacketHandle.createNew(
                pos.getX(), pos.getY(), pos.getZ(),
                playerMount.getSyncYaw(), playerMount.getSyncPitch(),
                0.0, 0.0, 0.0,
                RelativeFlags.ABSOLUTE_POSITION,
                teleportId), p -> {
            if (callback != null) {
                callback.run();
            }
        });
        player.getVehicleMountController().mount(playerMount.getEntityId(), player.getEntityId());
    }

    private void stopRealPlayerMount() {
        if (playerMount != null) {
            player.getVehicleMountController().unmount(playerMount.getEntityId(), player.getEntityId());
            playerMount.destroy(player);
            playerMount = null;

            // After unmounting, make sure the real player is restored to where the spectated
            // fake player was, instead of being left up at the off-screen mount altitude.
            VirtualEntity entity = (fakePlayer != null) ? fakePlayer.entity : null;
            if (entity != null) {
                Vector pos = entity.getSyncPos();
                ServerPlayerHandle playerHandle = ServerPlayerHandle.fromBukkit(player.getPlayer());
                playerHandle.setPositionRotation(pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch());
                playerHandle.setFallDistance(0.0f);

                player.send(ClientboundPlayerPositionPacketHandle.createAbsolute(
                        pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch()));
            }
        }
    }

    private void initializeMirroredEquipmentState() {
        if (fakePlayer == null) {
            return;
        }

        int entityId = fakePlayer.entity.getEntityId();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = PlayerUtil.getEquipment(player.getPlayer(), slot);
            equipmentState.put(slot, item);
            if (ItemUtil.isEmpty(item)) {
                continue;
            }
            player.sendSilent(Util.createPlayerEquipmentPacket(entityId, slot, item));
        }
    }

    private void updateMirroredEquipment() {
        if (isHeadOnly() || fakePlayer == null || fakePlayer.entity == null || !player.isConnected()) {
            return;
        }
        final int entityId = fakePlayer.entity.getEntityId();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            sendEquipmentIfChanged(entityId, slot, PlayerUtil.getEquipment(player.getPlayer(), slot));
        }
    }

    private void sendEquipmentIfChanged(int entityId, EquipmentSlot slot, ItemStack item) {
        ItemStack oldItem = equipmentState.get(slot);
        if (Objects.equals(oldItem, item)) {
            return;
        }

        equipmentState.put(slot, item);
        player.sendSilent(Util.createPlayerEquipmentPacket(entityId, slot, item));
    }

    private class BlindRespawn {
        public final VirtualEntity spectated;
        public final long timeout;

        public BlindRespawn(AttachmentManager manager, boolean isHead) {
            this.spectated = new VirtualEntity(manager);
            this.spectated.setEntityType(EntityType.VILLAGER);
            this.spectated.setSyncMode(SyncMode.NORMAL);
            this.spectated.setUseMinecartInterpolation(useMinecartInterpolation);
            this.spectated.setRelativeOffset(0.0, isHead ? -VirtualEntity.PLAYER_STANDING_EYE_HEIGHT : 0.0, 0.0);
            this.spectated.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this.spectated.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.timeout = System.currentTimeMillis() + (6 * 50);
        }

        public void spawn(Matrix4x4 eyeTransform, Vector motion) {
            spectated.updatePosition(eyeTransform);
            spectated.syncPosition(true);
            spectated.spawn(player, motion);
            spectated.forceSyncRotation();
            // Send camera packet through AttachmentViewer to ensure it's bundled properly
            player.send(ClientboundSetCameraPacketHandle.createNew(spectated.getEntityId()));
        }

        public void despawn() {
            // Send camera packet to stop spectating by having the player spectate themselves
            player.send(ClientboundSetCameraPacketHandle.createNew(player.getEntityId()));
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
        private final boolean initiallyVisible;
        private int mountedVehicleId = -1;

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakePlayerSpawner) {
            this(manager, fakePlayerSpawner, false);
        }

        public FakeVirtualPlayer(AttachmentManager manager, FakePlayerSpawner fakePlayerSpawner, boolean initiallyVisible) {
            super(manager);
            this.fakePlayerSpawner = fakePlayerSpawner;
            this.initiallyVisible = initiallyVisible;
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
        protected void sendSpawnPackets(com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer viewer, Vector motion) {
            FakePlayerSpawner.FakePlayerPosition orientation = FakePlayerSpawner.FakePlayerPosition.create(
                    this.getPosX(), this.getPosY(), this.getPosZ(),
                    (float) this.getYawPitchRoll().getY(),
                    this.getLivePitch(),
                    (float) this.getYawPitchRoll().getY());

            addViewerWithoutSpawning(viewer);
            fakePlayerSpawner.spawnPlayer(viewer, viewer.getPlayer(), this.getEntityId(), orientation, meta -> {
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, !initiallyVisible);
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                FakeVirtualPlayer.this.metaData = meta;
            });
        }
    }
}
