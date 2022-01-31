package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.Collection;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Information for a seated entity in a 'normal' way. The entity can only be
 * upright or upside-down. This is the classic behavior seats have in Traincarts.
 */
class SeatedEntityNormal extends SeatedEntity {
    private boolean _upsideDown = false;
    private int _fakeEntityId = -1;
    private boolean _fake = false;

    // Is initialized when the player needs to be displayed upside-down
    private VirtualEntity _upsideDownVehicle = null;

    public SeatedEntityNormal(CartAttachmentSeat seat) {
        super(seat);
    }

    public boolean isUpsideDown() {
        return this._upsideDown;
    }

    public void setUpsideDown(boolean upsideDown) {
        this._upsideDown = upsideDown;
    }

    public boolean isFake() {
        return this._fake;
    }

    public void setFake(boolean fake) {
        this._fake = fake;
    }

    /**
     * Sends the correct current metadata information the seated entity should have.
     * 
     * @param viewer
     */
    public void refreshUpsideDownMetadata(Player viewer, boolean upsideDown) {
        // We don't use the dinnerbone tag for players at all
        if (isEmpty() || isPlayer() || isDummyPlayer()) {
            return;
        }

        if (upsideDown) {
            // Apply metadata 'Dinnerbone' with nametag invisible
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME, FakePlayerSpawner.UPSIDEDOWN.getPlayerName());
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entity.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            // Send the default metadata of this entity
            DataWatcher metaTmp = EntityHandle.fromBukkit(this.entity).getDataWatcher();
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entity.getEntityId(), metaTmp, true));
        }
    }

    private void makeFakePlayerVisible(VehicleMountController vmc, Player viewer) {
        // Generate an entity id if needed for the first time
        if (this._fakeEntityId == -1) {
            this._fakeEntityId = EntityUtil.getUniqueEntityId();
        }

        // Position of the fake player
        Vector fpp_pos = seat.getTransform().toVector();
        FakePlayerSpawner.FakePlayerPosition fpp = FakePlayerSpawner.FakePlayerPosition.create(
                fpp_pos.getX(), fpp_pos.getY(), fpp_pos.getZ(),
                this.orientation.getPassengerYaw(),
                this.orientation.getPassengerPitch(),
                this.orientation.getPassengerHeadYaw());

        if (this._upsideDown) {
            // Player must be mounted inside the upside-down vehicle, which has a special offset so that the
            // upside-down butt is where the transform is at.
            if (this._upsideDownVehicle == null) {
                this._upsideDownVehicle = createPassengerVehicle();
                this._upsideDownVehicle.addRelativeOffset(0.0, -0.65, 0.0);
                this._upsideDownVehicle.updatePosition(seat.getTransform(),
                        new Vector(0.0, (double) this.orientation.getMountYaw(), 0.0));
                this._upsideDownVehicle.syncPosition(true);
            }
            this._upsideDownVehicle.spawn(viewer, seat.calcMotion());

            // Spawn player as upside-down. For dummy players, entity is null, so spawns a dummy.
            FakePlayerSpawner.UPSIDEDOWN.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, fpp, this::applyFakePlayerMetadata);

            // Mount player inside the upside-down carrier vehicle
            vmc.mount(this._upsideDownVehicle.getEntityId(), this._fakeEntityId);
        } else {
            // Spawn a normal no-nametag player. For dummy players, entity is null, so spawns a dummy.
            FakePlayerSpawner.NO_NAMETAG.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, fpp, this::applyFakePlayerMetadata);

            // Upright fake players can be put into the vehicle mount
            vmc.mount(this.parentMountId, this._fakeEntityId);
        }

        // Sync initial rotations of these entities, if locked
        if (this.orientation.isLocked()) {
            this.orientation.sendLockedRotations(viewer, this._fakeEntityId);
        }
    }

    private void applyFakePlayerMetadata(DataWatcher metadata) {
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING, false);
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING,
                this.isDummyPlayer() && seat.isFocused());
    }

    private void makeFakePlayerInvisible(VehicleMountController vmc, Player viewer) {
        // De-spawn the fake player itself
        PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityId));
        vmc.remove(this._fakeEntityId);

        // If used, de-spawn the upside-down vehicle too
        if (this._upsideDown && this._upsideDownVehicle != null) {
            this._upsideDownVehicle.destroy(viewer);
            vmc.remove(this._upsideDownVehicle.getEntityId());
        }
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 1.6, 0.0);
    }

    @Override
    public Vector getFirstPersonCameraOffset() {
        return new Vector(0.0, VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);

        spawnVehicleMount(viewer); // Makes parentMountId valid

        if (isDummyPlayer() && isEmpty()) {
            // For dummy players, spawn a fake version of the Player and seat it
            // The original player is also displayed, so that might be weird. Oh well.
            makeFakePlayerVisible(vmc, viewer);
        } else if (this.entity == viewer) {
            // In first-person, show the fake player, but do not mount the viewer in anything
            // That is up to the first-person controller to deal with.
            makeFakePlayerVisible(vmc, viewer);
        } else if (this._fake && isPlayer()) {
            // Despawn/hide original player entity
            vmc.despawn(this.entity.getEntityId());

            // Respawn an upside-down player in its place, mounted into the vehicle
            makeFakePlayerVisible(vmc, viewer);
        } else if (!this.isEmpty()) {
            // Send metadata
            if (this._upsideDown) {
                refreshUpsideDownMetadata(viewer, true);
            }

            // Mount entity in vehicle
            vmc.mount(this.parentMountId, this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);

        if (isDummyPlayer() && isEmpty()) {
            // Hide fake player again
            makeFakePlayerInvisible(vmc, viewer);
        } else if (this.entity == viewer) {
            // De-spawn a fake player
            makeFakePlayerInvisible(vmc, viewer);
        } else if (this._fake && this.isPlayer()) {
            // De-spawn a fake player, if any
            makeFakePlayerInvisible(vmc, viewer);

            // Respawn original player
            showRealPlayer(viewer);
        } else if (!this.isEmpty()) {
            // For non-player passengers, resend correct metadata to remove dinnerbone tags
            if (this._upsideDown) {
                refreshUpsideDownMetadata(viewer, false);
            }

            // Unmount original entity from the mount
            vmc.unmount(this.parentMountId, this.entity.getEntityId());
        }

        // De-spawn the fake mount being used, if any
        despawnVehicleMount(viewer);
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = FirstPersonViewMode.DEFAULT;

        // Whether a fake entity is used to represent this seated entity
        boolean new_isFake;

        // Whether the (fake) entity is displayed upside-down
        boolean new_isUpsideDown;

        if (!this.isDisplayed()) {
            new_isFake = false;
            new_isUpsideDown = false;
        } else {
            Quaternion rotation = seat.getTransform().getRotation();
            double selfPitch = rotation.getPitch();

            // Compute new upside-down state
            new_isUpsideDown = this.isUpsideDown();
            if (MathUtil.getAngleDifference(selfPitch, 180.0) < 89.0) {
                // Beyond the point where the entity should be rendered upside-down
                new_isUpsideDown = true;
            } else if (MathUtil.getAngleDifference(selfPitch, 0.0) < 89.0) {
                // Beyond the point where the entity should be rendered normally again
                new_isUpsideDown = false;
            }

            // Compute new first-person state of whether the player sees himself from third person using a fake camera
            new_firstPersonMode = seat.firstPerson.getMode();
            if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
                if (TCConfig.enableSeatThirdPersonView &&
                    this.isPlayer() &&
                    Math.abs(selfPitch) > 70.0)
                {
                    new_firstPersonMode = FirstPersonViewMode.THIRD_P;
                }
                else if (seat.useSmoothCoasters()) {
                    // Smooth coasters can't deal well switching between mounts
                    // Stay in the virtual camera view mode
                    new_firstPersonMode = FirstPersonViewMode.SMOOTHCOASTERS_FIX;
                } else {
                    new_firstPersonMode = FirstPersonViewMode.DEFAULT;
                }
            }

            // Whether a fake entity is used to represent this seated entity
            boolean noNametag = (this.displayMode == DisplayMode.NO_NAMETAG);
            new_isFake = isDummyPlayer() || (this.isPlayer() && (noNametag || new_isUpsideDown || new_firstPersonMode.hasFakePlayer()));
        }

        // When we change whether a fake entity is displayed, hide for everyone and make visible again
        if (silent) {
            // Explicitly requested we do not send any packets
            this.setFake(new_isFake);
            this.setUpsideDown(new_isUpsideDown);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            return;
        }

        if (new_isFake != this.isFake() || (this.isPlayer() && new_isUpsideDown != this.isUpsideDown())) {
            // Do we refresh the first player view as well?
            boolean refreshFPV = seat.firstPerson.doesViewModeChangeRequireReset(new_firstPersonMode);

            // Fake entity changed, this requires the entity to be respawned for everyone
            Entity entity = this.getEntity();
            Collection<Player> viewers = seat.getViewersSynced();
            for (Player viewer : viewers) {
                if (refreshFPV || viewer != entity) {
                    seat.makeHiddenImpl(viewer);
                }
            }
            this.setFake(new_isFake);
            this.setUpsideDown(new_isUpsideDown);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            for (Player viewer : viewers) {
                if (refreshFPV || viewer != entity) {
                    seat.makeVisibleImpl(viewer);
                }
            }
        } else {
            if (new_isUpsideDown != this.isUpsideDown()) {
                // Upside-down changed, but the seated entity is not a Player
                // All we have to do is refresh the Entity metadata
                this.setUpsideDown(new_isUpsideDown);
                if (!this.isEmpty()) {
                    for (Player viewer : seat.getViewersSynced()) {
                        this.refreshUpsideDownMetadata(viewer, new_isUpsideDown);
                    }
                }
            }
            if (new_firstPersonMode != seat.firstPerson.getLiveMode()) {
                // Only first-person view useVirtualCamera changed
                Collection<Player> viewers = seat.getViewersSynced();
                if (viewers.contains(this.getEntity())) {
                    // Hide, change, and make visible again, just for the first-player-view player
                    Player viewer = (Player) this.getEntity();
                    seat.makeHiddenImpl(viewer);
                    seat.firstPerson.setLiveMode(new_firstPersonMode);
                    seat.makeVisibleImpl(viewer);
                } else {
                    // Silent
                    seat.firstPerson.setLiveMode(new_firstPersonMode);
                }
            }
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (entityId == this._fakeEntityId) {
            return true;
        }
        return false;
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        if (isDisplayed()) {
            // Entity id is that of a fake entity if used, otherwise uses entity id
            // If in first-person the fake entity is not used, then we're sending packets
            // about an entity that does not exist. Is this bad?
            int entityId = this._fake ? this._fakeEntityId : ((this.entity == null) ? -1 : this.entity.getEntityId());
            orientation.synchronizeNormal(seat, transform, this, entityId);
        }
        updateVehicleMountPosition(transform);
        if (this._upsideDownVehicle != null) {
            this._upsideDownVehicle.updatePosition(transform);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        syncVehicleMountPosition(absolute);
        if (this._upsideDownVehicle != null) {
            this._upsideDownVehicle.syncPosition(absolute);
        }
    }

    @Override
    public void updateFocus(boolean focused) {
        // Send a metadata packet with the updated entity flags to set glowing appropriately
        if (this._fakeEntityId != -1 && this.isDisplayed()) {
            DataWatcher metadata;
            if (isPlayer()) {
                metadata = EntityUtil.getDataWatcher(this.entity).clone();
            } else {
                metadata = new DataWatcher();
                metadata.set(EntityHandle.DATA_FLAGS, (byte) 0);
            }

            applyFakePlayerMetadata(metadata);
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(
                    this._fakeEntityId, metadata, true);
            for (Player viewer : seat.getViewers()) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }
    }
}
