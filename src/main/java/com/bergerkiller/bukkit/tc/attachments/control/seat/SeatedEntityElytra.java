package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
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
        {
            DataWatcher meta = new DataWatcher();
            getMetadataFunction(true).accept(meta);
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(this._fakeEntityId, meta, true);
            for (Player viewer : seat.getViewers()) {
                if (this.entity != viewer || isMadeVisibleInFirstPerson()) {
                    PacketUtil.sendPacket(viewer, packet);
                }
            }
        }

        // Show new entity
        {
            DataWatcher meta = new DataWatcher();
            getMetadataFunction(false).accept(meta);
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(this._fakeEntityIdFlipped, meta, true);
            for (Player viewer : seat.getViewers()) {
                if (this.entity != viewer || isMadeVisibleInFirstPerson()) {
                    PacketUtil.sendPacket(viewer, packet);
                }
            }
        }

        // Flip id's
        {
            int old = this._fakeEntityId;
            this._fakeEntityId = this._fakeEntityIdFlipped;
            this._fakeEntityIdFlipped = old;
        }
    }

    public void makeFakePlayerVisible(VehicleMountController vmc, Player viewer) {
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

        // Spawn and mount a fake elytra-pose player into an invisible vehicle
        Consumer<DataWatcher> metaFunction = getMetadataFunction(false);
        FakePlayerSpawner.NO_NAMETAG_SECONDARY.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, false, this.orientation, metaFunction);
        vmc.mount(this.fakeVehicle.getEntityId(), this._fakeEntityId);

        // Also spawn a player entity with pitch flipped for elytra mode to switch between 0 / 180 degrees
        // Mount this fake player too
        Consumer<DataWatcher> metaFunctionFlipped = getMetadataFunction(true);
        FakePlayerSpawner.NO_NAMETAG.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityIdFlipped, true, this.orientation, metaFunctionFlipped);
        vmc.mount(this.fakeVehicle.getEntityId(), this._fakeEntityIdFlipped);

        // Sync initial rotations of these entities, if locked
        if (this.orientation.isLocked()) {
            this.orientation.sendLockedRotations(viewer, this._fakeEntityId);
        }
    }

    public void makeFakePlayerHidden(VehicleMountController vmc, Player viewer) {
        if (this._fakeEntityId != -1 && isPlayer()) {
            // Destroy old fake player entity
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityId));
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityIdFlipped));
            this.fakeVehicle.destroy(viewer);
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
    public void makeVisible(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
        if (this.isPlayer()) {
            // Show the fake player, and if not first-person, hide the real player
            if (this.entity != viewer) {
                hideRealPlayer(viewer);
            }
            makeFakePlayerVisible(vmc, viewer);
        } else if (!this.isEmpty()) {
            // Default behavior for non-player entities is just to mount them
            vmc.mount(this.spawnVehicleMount(viewer), this.entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
        if (this.isPlayer()) {
            // Hide the fake player, and if not first-person, re-show the real player too
            makeFakePlayerHidden(vmc, viewer);
            if (this.entity != viewer) {
                showRealPlayer(viewer);
            }
        } else if (!this.isEmpty()) {
            // Unmount for generic entities
            vmc.unmount(this.parentMountId, this.entity.getEntityId());
            despawnVehicleMount(viewer);
        }
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = this.seat.firstPerson.getMode();
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        if (this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        // No other mode is supported here
        if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
            new_firstPersonMode = FirstPersonViewMode.THIRD_P;
        }

        // If unchanged, do nothing
        if (new_smoothCoasters == seat.firstPerson.useSmoothCoasters() &&
            new_firstPersonMode == seat.firstPerson.getLiveMode())
        {
            return;
        }

        // Sometimes a full reset of the FPV controller is required. Avoid when silent.
        if (!silent &&
            seat.firstPerson.doesViewModeChangeRequireReset(new_firstPersonMode) &&
            seat.getViewersSynced().contains(this.getEntity()))
        {
            // Hide, change, and make visible again, just for the first-player-view player
            Player viewer = (Player) this.getEntity();
            seat.makeHidden(viewer);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            seat.makeVisibleImpl(viewer);
            return;
        }

        // Silent update
        seat.firstPerson.setLiveMode(new_firstPersonMode);
        seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        if (!isEmpty()) {
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
}
