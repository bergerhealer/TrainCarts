package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.Collection;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

/**
 * Seated entity that is using flying (elytra) mode of display.
 * To deal with a smooth rotation transition at 180-degree pitch,
 * there are two fake entities used to represent the player, switched between.
 */
public class SeatedEntityElytra extends SeatedEntity {
    private int _fakeEntityId = -1;
    private int _fakeEntityIdFlipped = -1; // Fix for the 180 pitch rotation bug, swaps entities instead

    public SeatedEntityElytra(CartAttachmentSeat seat) {
        super(seat);
    }

    @Override
    public int getId() {
        return this._fakeEntityId;
    }

    /**
     * Gets the entity id of the invisible pitch-flipped entity (only used in elytra mode to fix glitch)
     * 
     * @return pitch-flipped entity id
     */
    public int getFlippedId() {
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
                if (!this.isInvisibleTo(viewer)) {
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
                if (!this.isInvisibleTo(viewer)) {
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

    @Override
    public boolean isInvisibleTo(Player viewer) {
        return super.isInvisibleTo(viewer) ||
                (this._entity == viewer && this.seat.firstPerson.getLiveMode() == FirstPersonViewMode.DEFAULT);
    }

    public void makeFakePlayerVisible(Player viewer) {
        if (isInvisibleTo(viewer)) {
            return;
        }

        // Initialize entity id's the first time
        if (this._fakeEntityId == -1) {
            this._fakeEntityId = EntityUtil.getUniqueEntityId();
        }
        if (this._fakeEntityIdFlipped == -1) {
            this._fakeEntityIdFlipped = EntityUtil.getUniqueEntityId();
        }

        Consumer<DataWatcher> metaFunction = getMetadataFunction(false);
        ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, false, this.orientation, metaFunction);

        // Unmount from the original vehicle and mount the new fake entity instead
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
        vmh.unmount(this.parentMountId, this._entity.getEntityId());
        vmh.mount(this.parentMountId, this._fakeEntityId);

        // Also spawn a player entity with pitch flipped for elytra mode to switch between 0 / 180 degrees
        // Mount this fake player too
        Consumer<DataWatcher> metaFunctionFlipped = getMetadataFunction(true);
        ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityIdFlipped, true, this.orientation, metaFunctionFlipped);
        vmh.mount(this.parentMountId, this._fakeEntityIdFlipped);
    }

    public void makeFakePlayerHidden(Player viewer) {
        if (isInvisibleTo(viewer)) {
            return;
        }

        if (this._fakeEntityId != -1 && isPlayer()) {
            // Destroy old fake player entity
            VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId, this._fakeEntityIdFlipped}));
            vmc.remove(this._fakeEntityId);
            vmc.remove(this._fakeEntityIdFlipped);

            // Respawn the actual player or clean up the list
            // Only needed when the player is not the viewer
            if (viewer == this._entity) {
                // Can not respawn yourself! Only undo listing.
                ProfileNameModifier.NORMAL.sendListInfo(viewer, (Player) this._entity);
            } else {
                // Respawns the player as a normal player
                vmc.respawn((Player) this._entity, (theViewer, thePlayer) -> {
                    ProfileNameModifier.NORMAL.spawnPlayer(theViewer, thePlayer, thePlayer.getEntityId(), false, null, meta -> {});
                });
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
    public void makeVisible(Player viewer, boolean fake) {
        super.makeVisible(viewer, fake);

        if (fake) {
            // Despawn/hide original player entity
            hideRealPlayer(viewer);

            // Respawn an upside-down player in its place
            makeFakePlayerVisible(viewer);
        } else {
            // Send metadata
            refreshMetadata(viewer);

            // Mount entity in vehicle, unless a camera is used
            if (this._entity != viewer || !seat.firstPerson.isFakeCameraUsed()) {
                VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
                vmh.mount(this.parentMountId, this._entity.getEntityId());
            }
        }
    }

    @Override
    public void makeHidden(Player viewer, boolean fake) {
        if (fake) {
            makeFakePlayerHidden(viewer);
        }
        super.makeHidden(viewer, fake);
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = this.seat.firstPerson.getMode();
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        if (seat.isRotationLocked() && this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        // No other mode is supported here
        if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
            new_firstPersonMode = FirstPersonViewMode.THIRD_P;
        }

        // When we change whether a fake entity is displayed, hide for everyone and make visible again
        if (silent) {
            // Explicitly requested we do not send any packets
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            return;
        }

        if (new_firstPersonMode != seat.firstPerson.getLiveMode()) {
            // Only first-person view useVirtualCamera changed
            Collection<Player> viewers = seat.getViewersSynced();
            if (viewers.contains(this.getEntity())) {
                // Hide, change, and make visible again, just for the first-player-view player
                Player viewer = (Player) this.getEntity();
                seat.makeHidden(viewer);
                seat.firstPerson.setLiveMode(new_firstPersonMode);
                seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
                seat.makeVisibleImpl(viewer);
            } else {
                // Silent
                seat.firstPerson.setLiveMode(new_firstPersonMode);
                seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            }
        }
    }
}
