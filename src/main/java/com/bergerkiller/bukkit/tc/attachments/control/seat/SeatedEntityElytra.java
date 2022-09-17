package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Seated entity that is using flying (elytra) mode of display.
 * To deal with a smooth rotation transition at 180-degree pitch,
 * there are two fake entities used to represent the player, switched between.
 */
class SeatedEntityElytra extends SeatedEntity {
    private int _fakeEntityId = -1;
    private int _fakeEntityIdFlipped = -1; // Fix for the 180 pitch rotation bug, swaps entities instead
    private VirtualEntity fakeVehicle = null; // Vehicle in which the two fake entities sit
    private Vector fakeVehicleInitialOffset = new Vector(); // Initial relative offset of the fake vehicle

    public SeatedEntityElytra(CartAttachmentSeat seat) {
        super(seat);
    }

    protected int getFakePlayerId() {
        return this._fakeEntityId;
    }

    /**
     * Gets the entity id of the invisible pitch-flipped entity (only used in elytra mode to fix glitch)
     * 
     * @return pitch-flipped entity id
     */
    protected int getFlippedFakePlayerId() {
        return this._fakeEntityIdFlipped;
    }

    /**
     * Flips the fakes around (180 degree pitch glitch)
     */
    public void flipFakes(CartAttachmentSeat seat) {
        // Ignore if borked
        if (this._fakeEntityId == -1 || this._fakeEntityIdFlipped == -1) {
            return;
        }

        // Hide old entity
        sendUpdatedMetadata(this._fakeEntityId, true);

        // Show new entity
        sendUpdatedMetadata(this._fakeEntityIdFlipped, false);

        // Flip id's
        {
            int old = this._fakeEntityId;
            this._fakeEntityId = this._fakeEntityIdFlipped;
            this._fakeEntityIdFlipped = old;
        }
    }

    private void sendUpdatedMetadata(int entityId, boolean invisible) {
        DataWatcher meta = new DataWatcher();
        getMetadataFunction(invisible).accept(meta);
        PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(entityId, meta, true);
        for (AttachmentViewer viewer : seat.getAttachmentViewers()) {
            if (this.entity != viewer.getPlayer() || isDummyPlayer() || isMadeVisibleInFirstPerson()) {
                viewer.send(packet);
            }
        }
    }

    public void makeFakePlayerVisible(AttachmentViewer viewer) {
        // Spawn a vehicle for the two elytra-mode fake player entities
        if (this.fakeVehicle == null) {
            this.fakeVehicle = this.createPassengerVehicle();
            MathUtil.setVector(this.fakeVehicleInitialOffset, this.fakeVehicle.getRelativeOffset());
            this.fakeVehicle.addRelativeOffset(orientation.computeElytraRelativeOffset(seat.getTransform().getYawPitchRoll()));
            this.fakeVehicle.updatePosition(seat.getTransform());
            this.fakeVehicle.syncPosition(true);
        }
        this.fakeVehicle.spawn(viewer, seat.calcMotion());

        // Initialize entity id's the first time
        if (this._fakeEntityId == -1) {
            this._fakeEntityId = EntityUtil.getUniqueEntityId();
        }
        if (this._fakeEntityIdFlipped == -1) {
            this._fakeEntityIdFlipped = EntityUtil.getUniqueEntityId();
        }

        // Compute the fake player position and orientation
        FakePlayerSpawner.FakePlayerPosition fpp = FakePlayerSpawner.FakePlayerPosition.create(
                this.fakeVehicle.getPosX(), this.fakeVehicle.getPosY(), this.fakeVehicle.getPosZ(),
                this.orientation.getPassengerYaw(), this.orientation.getPassengerPitch(),
                this.orientation.getPassengerHeadYaw());

        // Spawn and mount a fake elytra-pose player into an invisible vehicle
        // For dummy players entity is null, so spawns a dummy.
        VehicleMountController vmc = viewer.getVehicleMountController();
        Consumer<DataWatcher> metaFunction = getMetadataFunction(false);
        FakePlayerSpawner.NO_NAMETAG_SECONDARY.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, fpp, metaFunction);
        vmc.mount(this.fakeVehicle.getEntityId(), this._fakeEntityId);

        // Also spawn a player entity with pitch flipped for elytra mode to switch between 0 / 180 degrees
        // Mount this fake player too
        // For dummy players entity is null, so spawns a dummy.
        Consumer<DataWatcher> metaFunctionFlipped = getMetadataFunction(true);
        FakePlayerSpawner.NO_NAMETAG.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityIdFlipped, fpp.atOppositePitchBoundary(), metaFunctionFlipped);
        vmc.mount(this.fakeVehicle.getEntityId(), this._fakeEntityIdFlipped);

        // Sync initial rotations of these entities, if locked
        if (seat.isRotationLocked()) {
            this.orientation.sendLockedRotations(viewer, this._fakeEntityId);
        }
    }

    public void makeFakePlayerHidden(AttachmentViewer viewer) {
        if (this._fakeEntityId != -1 && (isPlayer() || isDummyPlayer())) {
            // Destroy old fake player entity
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityId));
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityIdFlipped));
            this.fakeVehicle.destroy(viewer);
            VehicleMountController vmc = viewer.getVehicleMountController();
            vmc.remove(this._fakeEntityId);
            vmc.remove(this._fakeEntityIdFlipped);
            vmc.remove(this.fakeVehicle.getEntityId());

            // Remove vehicle if no more viewers for it exist
            if (!this.fakeVehicle.hasViewers()) {
                this.fakeVehicle = null;
            }
        }
    }

    private Consumer<DataWatcher> getMetadataFunction(final boolean invisible) {
        return metadata -> {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING, true);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, invisible);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, !invisible && seat.isFocused());
        };
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 1.4, 0.0);
    }

    @Override
    public Vector getFirstPersonCameraOffset() {
        return new Vector(0.0, VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT, 0.0);
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        if (isPlayer()) {
            // Show the fake player, and if not first-person, hide the real player
            if (this.entity != viewer.getPlayer() && !this.isDummyPlayer()) {
                hideRealPlayer(viewer);
            }
            makeFakePlayerVisible(viewer);
        } else if (!this.isEmpty()) {
            // Default behavior for non-player entities is just to mount them
            viewer.getVehicleMountController().mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        } else if (isDummyPlayer()) {
            // Show the dummy
            makeFakePlayerVisible(viewer);
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (isPlayer()) {
            // Hide the fake player, and if not first-person, re-show the real player too
            makeFakePlayerHidden(viewer);
            if (this.entity != viewer.getPlayer()) {
                showRealPlayer(viewer);
            }
        } else if (!isEmpty()) {
            // Unmount for generic entities
            viewer.getVehicleMountController().unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);
        } else if (isDummyPlayer()) {
            // Hide the dummy
            makeFakePlayerHidden(viewer);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (entityId == _fakeEntityId) {
            return true; // Don't care about flipped - is invisible
        }
        return false;
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        if (isDisplayed()) {
            Vector pyr = transform.getYawPitchRoll();
            orientation.synchronizeElytra(seat, transform, pyr, this);
            if (this.fakeVehicle != null) {
                this.fakeVehicle.setRelativeOffset(this.fakeVehicleInitialOffset);
                this.fakeVehicle.addRelativeOffset(orientation.computeElytraRelativeOffset(pyr));
                this.fakeVehicle.updatePosition(transform, new Vector(0.0, this.orientation.getMountYaw(), 0.0));
            }
        }
        updateVehicleMountPosition(transform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (this.fakeVehicle != null) {
            this.fakeVehicle.syncPosition(absolute);
        }
        syncVehicleMountPosition(absolute);
    }

    @Override
    public void updateFocus(boolean focused) {
        if (this._fakeEntityId != -1 && this._fakeEntityIdFlipped != -1) {
            sendUpdatedMetadata(this._fakeEntityId, false);
            sendUpdatedMetadata(this._fakeEntityIdFlipped, true);
        }
    }
}
