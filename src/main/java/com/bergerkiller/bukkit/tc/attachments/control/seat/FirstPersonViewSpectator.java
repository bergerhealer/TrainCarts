package com.bergerkiller.bukkit.tc.attachments.control.seat;

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
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Makes the player spectate an Entity and then moves that Entity to
 * move the camera around.
 */
public class FirstPersonViewSpectator extends FirstPersonView {
    // Vehicle entity id, -1 if not used
    private int vehicleEntityId = -1;
    // Controls all the spectating logic itself, depending on the type of view mode used
    private FirstPersonSpectatedEntity _spectatedEntity = null;
    // Holds the player nearby, off-screen, while spectating. Out of the way of the
    // spectated entity to prevent self-interaction-caused player d/c.
    private VirtualEntity _playerMount = null;
    // Tracks player input while inside this FPV mode
    private final SpectatorInput _input = new SpectatorInput();

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
        }

        // Position used to compute where the eye/camera view is at
        Matrix4x4 eyeTransform = this.getEyeTransform();

        // Start spectator mode
        this._spectatedEntity = FirstPersonSpectatedEntity.create(seat, this, viewer);
        this._spectatedEntity.start(eyeTransform);

        // Mount the player itself off-screen on a mount somewhere
        // We want it to stay out of clickable range to prevent player d/c
        if (this._playerMount == null) {
            this._playerMount = new VirtualEntity(seat.getManager());
            this._playerMount.setEntityType(EntityType.ARMOR_STAND);
            this._playerMount.setSyncMode(SyncMode.SEAT);

            // Put the Player somewhere high up there in the sky
            this._playerMount.setRelativeOffset(0.0, 64.0, 0.0);
            this._playerMount.updatePosition(eyeTransform);
            this._playerMount.syncPosition(true);
            this._playerMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this._playerMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            this._playerMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
            this._playerMount.spawn(viewer, new Vector());

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

        // Remove player from the temporary mount
        if (_playerMount != null) {
            VehicleMountController vmc = viewer.getVehicleMountController();
            vmc.unmount(this._playerMount.getEntityId(), viewer.getEntityId());

            _playerMount.destroy(viewer);
            _playerMount = null;
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
