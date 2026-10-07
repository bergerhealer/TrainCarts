package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.controller.player.ViewRotationTracker;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerPositionPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.LivingEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.ArmorStandHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonSpectatedEntity;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Makes the player spectate an Entity and then moves that Entity to
 * move the camera around.
 */
public class FirstPersonViewSpectator extends FirstPersonView {
    /** The real (invisible) player is floating this high above the entity being spectated */
    private static final double GHOST_Y_OFFSET = 64;
    // Vehicle entity id, -1 if not used
    private int vehicleEntityId = -1;
    // Controls all the spectating logic itself, depending on the type of view mode used
    private FirstPersonSpectatedEntity _spectatedEntity = null;
    // Holds the player nearby, off-screen, while spectating. Out of the way of the
    // spectated entity to prevent self-interaction-caused player d/c.
    private VirtualEntity _playerMount = null;
    // Tracks player input while inside this FPV mode
    private final SpectatorInput _input = new SpectatorInput();
    // Tracks incoming view rotations and applies spectator pitch correction while active
    private ViewRotationTracker _viewRotationTracker = null;

    public FirstPersonViewSpectator(CartAttachmentSeat seat, AttachmentViewer player) {
        super(seat, player);
    }

    /**
     * If not already spawned, spawns a fake vehicle, or otherwise returns the Entity ID
     * to which passengers can be mounted directly into the vehicle.
     *
     * @return
     */
    public int prepareVehicleEntityId() {
        if (vehicleEntityId == -1) {
            vehicleEntityId = seat.seated.spawnVehicleMount(player);
        }
        return vehicleEntityId;
    }

    @Override
    public boolean doesViewModeChangeRequireReset(FirstPersonViewMode newViewMode) {
        // Respawns the seated entity in third-person, so a reset is needed
        return newViewMode == FirstPersonViewMode.THIRD_P ||
               this.getLiveMode() == FirstPersonViewMode.THIRD_P;
    }

    @Override
    protected Matrix4x4 getEyeTransform() {
        Matrix4x4 base = super.getEyeTransform();
        _input.applyTo(base);
        return base;
    }

    /**
     * Gets the current exact head rotation of the Player inside this seat.
     * This differs from the player entity's rotation, because of the relative
     * transformation that is applied.
     *
     * @param transform Current body (butt) transformation of this seated entity
     * @return current head rotation
     */
    protected Quaternion getCurrentHeadRotation(Matrix4x4 transform) {
        transform = transform.clone();

        // Adjust for eye position changes, if set
        if (!this._eyePosition.isDefault()) {
            transform.multiply(this._eyePosition.transform);
        }

        // Adjust for the relative rotation input by the player
        _input.applyTo(transform);

        return transform.getRotation();
    }

    @Override
    public void makeVisible(AttachmentViewer viewer, boolean isReload) {
        // Make the player invisible - we don't want it to get in view
        setPlayerVisible(viewer, false);
        vehicleEntityId = -1;

        // Start tracking player input
        if (this.getLockMode() == FirstPersonViewLockMode.SPECTATOR_FREE) {
            _input.start(viewer, seat.isRotationLocked() ? BODY_LOCK_FOV_LIMIT : 360.0f);
        } else {
            _input.startLocked();
        }

        // Position used to compute where the eye/camera view is at
        Matrix4x4 eyeTransform = this.getEyeTransform();

        // Start spectator mode
        this._spectatedEntity = FirstPersonSpectatedEntity.create(seat, this, viewer);
        this._spectatedEntity.start(eyeTransform);

        // Track incoming player look packets and correct spectator pitch where needed
        this._viewRotationTracker = viewer.startViewRotationTracking();
        this._viewRotationTracker.setPositionYCorrection(GHOST_Y_OFFSET);
        this._viewRotationTracker.setUsePitchAdjustment(true);

        // Mount the player itself off-screen on a mount somewhere
        // We want it to stay out of clickable range to prevent player d/c
        if (this._playerMount == null) {
            this._playerMount = new VirtualEntity(seat.getManager());
            this._playerMount.setEntityType(EntityType.ARMOR_STAND);
            this._playerMount.setSyncMode(SyncMode.SEAT);

            // Put the Player somewhere high up there in the sky
            this._playerMount.setRelativeOffset(0.0, GHOST_Y_OFFSET, 0.0);
            this._playerMount.updatePosition(eyeTransform);
            this._playerMount.syncPosition(true);
            this._playerMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this._playerMount.getMetaData().set(LivingEntityHandle.DATA_HEALTH, 10.0F);
            this._playerMount.getMetaData().set(ArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    ArmorStandHandle.DATA_FLAG_SET_MARKER |
                            ArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                            ArmorStandHandle.DATA_FLAG_IS_SMALL));
            this._playerMount.spawn(viewer, new Vector());

            // Sync the player to be high up in the sky, and then begin intercepting player inputs
            // This also gets rid of the GHOST_Y_OFFSET that the server receives from the player
            Vector pos = this._playerMount.getSyncPos();
            viewer.getClientSynchronizer().synchronize(teleportId -> ClientboundPlayerPositionPacketHandle.createNew(
                    pos.getX(), pos.getY(), pos.getZ(),
                    this._playerMount.getSyncYaw(), this._playerMount.getSyncPitch(),
                    0.0, 0.0, 0.0,
                    RelativeFlags.ABSOLUTE_POSITION,
                    teleportId), p -> _viewRotationTracker.enable());

            // Mount the player. Happens after the position sync.
            viewer.getVehicleMountController().mount(this._playerMount.getEntityId(), viewer.getEntityId());
        }

        // If third-person mode is used, also spawn the real seated entity for this viewer
        if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            seat.seated.makeVisibleFirstPerson(viewer);
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer, boolean isReload) {
        // If third-person mode is used, also despawn the real seated entity for this viewer
        if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            seat.seated.makeHiddenFirstPerson(viewer);
        }

        if (_viewRotationTracker != null) {
            _viewRotationTracker.stop();
            _viewRotationTracker = null;
        }

        // Remove player from the temporary mount
        if (_playerMount != null) {
            VehicleMountController vmc = viewer.getVehicleMountController();
            vmc.unmount(this._playerMount.getEntityId(), viewer.getEntityId());

            _playerMount.destroy(viewer);
            _playerMount = null;

            // The player will be really high up there after ejecting, put them back at a sane position
            if (_spectatedEntity != null) {
                VirtualEntity entity = _spectatedEntity.getCurrentEntity();
                Vector pos = entity.getSyncPos();

                // Ensure that the player position is sync with where it should be
                // We already do this with the player position modifier, but to be safe...
                ServerPlayerHandle playerHandle = ServerPlayerHandle.fromBukkit(viewer.getPlayer());
                playerHandle.setPositionRotation(pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch());
                playerHandle.setFallDistance(0.0f);

                viewer.send(ClientboundPlayerPositionPacketHandle.createAbsolute(pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch()));
            }
        }

        // Release camera of spectated entity & destroy it
        if (_spectatedEntity != null) {
            _spectatedEntity.stop();
            _spectatedEntity = null;
        }

        // Hide any fake mount previously used
        if (vehicleEntityId != -1) {
            seat.seated.despawnVehicleMount(viewer);
            vehicleEntityId = -1;
        }

        // Make viewer visible to himself again (restore)
        if (this.getLiveMode() != FirstPersonViewMode.THIRD_P) {
            setPlayerVisible(viewer, true);
        }

        // Cleanup, moves player view so it faces where it looked in spectator mode
        _input.stop(this.getEyeTransform());
    }

    @Override
    public void onTick() {
        if (_viewRotationTracker != null) {
            ViewRotationTracker.Rotation rotation = _viewRotationTracker.readAndResetRotationChange();
            if (rotation != ViewRotationTracker.Rotation.ZERO) {
                _input.addInputRotation(new SpectatorInput.YawPitch(rotation.yaw, rotation.pitch));
            }
        }

        // Update spectated entity
        if (_spectatedEntity != null) {
            Matrix4x4 baseTransform = getEyeTransform();
            _playerMount.updatePosition(baseTransform);
            _spectatedEntity.updatePosition(baseTransform);
        }

        // Update input
        _input.update();
    }

    @Override
    public void onMove(boolean absolute) {
        // Move the spectated entity
        if (_spectatedEntity != null) {
            _playerMount.syncPosition(absolute);
            _spectatedEntity.syncPosition(absolute);
        }
    }
}
