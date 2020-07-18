package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.Collection;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TCConfig;
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
    private boolean _fake = false;

    public SeatedEntityElytra(CartAttachmentSeat seat) {
        super(seat);
    }

    @Override
    public int getId() {
        return this._fake ? this._fakeEntityId : ((this._entity == null) ? -1 : this._entity.getEntityId());
    }

    /**
     * Gets the entity id of the invisible pitch-flipped entity (only used in elytra mode to fix glitch)
     * 
     * @return pitch-flipped entity id
     */
    public int getFlippedId() {
        return this._fake ? this._fakeEntityIdFlipped : -1;
    }

    public boolean isFake() {
        return this._fake;
    }

    public void setFake(boolean fake) {
        this._fake = fake;
        if (fake && this._fakeEntityId == -1) {
            this._fakeEntityId = EntityUtil.getUniqueEntityId();
        }
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
                if (viewer.getEntityId() != this._fakeEntityId) {
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
                if (viewer.getEntityId() != this._fakeEntityIdFlipped) {
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

    public void makeFakePlayerVisible(Player viewer) {
        Consumer<DataWatcher> metaFunction = getMetadataFunction(false);
        ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, false, this.orientation, metaFunction);

        // Unmount from the original vehicle and mount the new fake entity instead
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
        vmh.unmount(this.parentMountId, this._entity.getEntityId());
        vmh.mount(this.parentMountId, this._fakeEntityId);

        // Also spawn a player entity with pitch flipped for elytra mode to switch between 0 / 180 degrees
        // Mount this fake player too
        if (this._displayMode == DisplayMode.ELYTRA_SIT || this._displayMode == DisplayMode.ELYTRA) {
            if (this._fakeEntityIdFlipped == -1) {
                this._fakeEntityIdFlipped = EntityUtil.getUniqueEntityId();
            }
            Consumer<DataWatcher> metaFunctionFlipped = getMetadataFunction(true);
            ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityIdFlipped, true, this.orientation, metaFunctionFlipped);

            vmh.mount(this.parentMountId, this._fakeEntityIdFlipped);
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

        if (this._fake && fake) {
            // Despawn/hide original player entity
            if (this._entity == viewer) {
                // Sync to self: make the real player invisible using a metadata change
                DataWatcher metaTmp = new DataWatcher();
                metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(viewer.getEntityId(), metaTmp, true);
                PacketUtil.sendPacket(viewer, metaPacket);
            } else {
                // Sync to others: destroy the original player
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._entity.getEntityId()}));
            }

            // Respawn an upside-down player in its place
            makeFakePlayerVisible(viewer);
        } else {
            // Send metadata
            refreshMetadata(viewer);

            // Mount entity in vehicle
            VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
            vmh.mount(this.parentMountId, this._entity.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer, boolean fake) {
        if (fake) {
            makeFakePlayerHidden(viewer);
        }
        super.makeHidden(viewer, fake);
    }

    public void makeFakePlayerHidden(Player viewer) {
        if (this._fake && isPlayer()) {
            // Destroy old fake player entity
            VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
            if (this._fakeEntityIdFlipped == -1) {
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId}));
                vmc.remove(this._fakeEntityId);
            } else {
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId, this._fakeEntityIdFlipped}));
                vmc.remove(this._fakeEntityId);
                vmc.remove(this._fakeEntityIdFlipped);
            }

            // Respawn the actual player or clean up the list
            // Only needed when the player is not the viewer
            if (viewer == this._entity) {
                // Can not respawn yourself! Only undo listing.
                ProfileNameModifier.NORMAL.sendListInfo(viewer, (Player) this._entity);
            } else {
                // Respawns the player as a normal player
                ProfileNameModifier.NORMAL.spawnPlayer(viewer, (Player) this._entity, this._entity.getEntityId(), false, null, meta -> {});
            }
        }
    }

    @Override
    public void transformToEyes(Matrix4x4 transform) {
        transform.translate(0.0, -1.5, 0.0);
    }

    @Override
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = FirstPersonViewMode.DEFAULT;
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        boolean new_isFake;

        if (seat.isRotationLocked() && this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        if (this.isEmpty()) {
            new_isFake = false;
        } else {
            if (TCConfig.enableSeatThirdPersonView &&
                !new_smoothCoasters &&
                this.isPlayer())
            {
                new_firstPersonMode = FirstPersonViewMode.THIRD_P;
            }

            new_isFake = this.isPlayer();
        }

        // When we change whether a fake entity is displayed, hide for everyone and make visible again
        if (silent) {
            // Explicitly requested we do not send any packets
            this.setFake(new_isFake);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            return;
        }

        if (new_isFake != this.isFake()) {
            // Fake entity changed, this requires the entity to be respawned for everyone
            // When upside-down changes for a Player seated entity, also perform a respawn
            Entity entity = this.getEntity();
            Collection<Player> viewers = seat.getViewersSynced();
            for (Player viewer : viewers) {
                if (new_smoothCoasters && viewer == entity) {
                    // Don't respawn firstPerson if using SmoothCoasters
                    continue;
                }
                seat.makeHidden(viewer);
            }
            this.setFake(new_isFake);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            for (Player viewer : viewers) {
                if (new_smoothCoasters && viewer == entity) {
                    continue;
                }
                seat.makeVisibleImpl(viewer);
            }
        } else if (new_firstPersonMode != seat.firstPerson.getLiveMode()) {
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
