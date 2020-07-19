package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.Collection;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
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
 * Information for a seated entity in a 'normal' way. The entity can only be
 * upright or upside-down. This is the classic behavior seats have in Traincarts.
 */
public class SeatedEntityNormal extends SeatedEntity {
    private boolean _upsideDown = false;
    private int _fakeEntityId = -1;
    private boolean _fake = false;

    public SeatedEntityNormal(CartAttachmentSeat seat) {
        super(seat);
    }

    public boolean isUpsideDown() {
        return this._upsideDown;
    }

    public void setUpsideDown(boolean upsideDown) {
        this._upsideDown = upsideDown;
    }

    @Override
    public int getId() {
        return this._fake ? this._fakeEntityId : ((this._entity == null) ? -1 : this._entity.getEntityId());
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

    @Override
    public void refreshMetadata(Player viewer) {
        if (!this.isPlayer() && this._upsideDown) {
            // Apply metadata 'Dinnerbone' with nametag invisible
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME, ProfileNameModifier.UPSIDEDOWN.getPlayerName());
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            super.refreshMetadata(viewer);
        }
    }

    public void makeFakePlayerVisible(Player viewer) {
        if (isInvisibleTo(viewer)) {
            return;
        }

        Consumer<DataWatcher> metaFunction = metadata -> {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING, false);
        };
        if (this._upsideDown) {
            ProfileNameModifier.UPSIDEDOWN.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, false, this.orientation, metaFunction);
        } else {
            ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, false, this.orientation, metaFunction);
        }

        // Unmount from the original vehicle and mount the new fake entity instead
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
        vmh.unmount(this.parentMountId, this._entity.getEntityId());
        vmh.mount(this.parentMountId, this._fakeEntityId);
    }

    public void makeFakePlayerHidden(Player viewer) {
        if (isInvisibleTo(viewer)) {
            return;
        }

        if (this._fake && isPlayer()) {
            // Destroy old fake player entity
            VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId}));
            vmc.remove(this._fakeEntityId);

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

    @Override
    public void makeVisible(Player viewer, boolean fake) {
        super.makeVisible(viewer, fake);

        if (this._fake && fake) {
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
        FirstPersonViewMode new_firstPersonMode = FirstPersonViewMode.DEFAULT;
        boolean new_smoothCoasters;

        // Whether a fake entity is used to represent this seated entity
        boolean new_isFake;

        // Whether the (fake) entity is displayed upside-down
        boolean new_isUpsideDown;

        if (seat.isRotationLocked() && this.isPlayer()) {
            new_smoothCoasters = TrainCarts.plugin.getSmoothCoastersAPI().isEnabled((Player) this.getEntity());
        } else {
            new_smoothCoasters = false;
        }

        if (this.isEmpty()) {
            new_isFake = false;
            new_isUpsideDown = false;
        } else {
            Quaternion rotation = seat.getTransform().getRotation();
            double selfPitch = getQuaternionPitch(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());

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
                else if (new_smoothCoasters) {
                    // Smooth coasters can't deal well switching between mounts
                    // Stay in the virtual camera view mode
                    new_firstPersonMode = FirstPersonViewMode.FLOATING;
                } else {
                    new_firstPersonMode = FirstPersonViewMode.DEFAULT;
                }
            }

            // Whether a fake entity is used to represent this seated entity
            new_isFake = this.isPlayer() && (new_isUpsideDown || new_firstPersonMode.hasFakePlayer());
        }

        // When we change whether a fake entity is displayed, hide for everyone and make visible again
        if (silent) {
            // Explicitly requested we do not send any packets
            this.setFake(new_isFake);
            this.setUpsideDown(new_isUpsideDown);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
            return;
        }

        if (new_isFake != this.isFake() || (this.isPlayer() && new_isUpsideDown != this.isUpsideDown())) {
            // Do we refresh the first player view as well?
            boolean refreshFPV = false;
            if (new_firstPersonMode != seat.firstPerson.getLiveMode() || new_firstPersonMode.hasFakePlayer()) {
                refreshFPV = true;
            }

            // Fake entity changed, this requires the entity to be respawned for everyone
            Entity entity = this.getEntity();
            Collection<Player> viewers = seat.getViewersSynced();
            for (Player viewer : viewers) {
                if (refreshFPV || viewer != entity) {
                    seat.makeHidden(viewer);
                }
            }
            this.setFake(new_isFake);
            this.setUpsideDown(new_isUpsideDown);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.firstPerson.setUseSmoothCoasters(new_smoothCoasters);
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
                        this.refreshMetadata(viewer);
                    }
                }
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
}
