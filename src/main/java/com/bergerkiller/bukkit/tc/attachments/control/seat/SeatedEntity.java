package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutUpdateAttributesHandle;

/**
 * Represents the visible, seated entity inside a seat. Sometimes this entity is a fake
 * version of the actual entity inside in order to apply additional transformations to it,
 * without the actual player entity sending packets that can corrupt it.
 */
public class SeatedEntity {
    private Entity _entity = null;
    private int _fakeEntityId = -1;
    private boolean _fake = false;
    private boolean _upsideDown = false;
    private DisplayMode _displayMode = DisplayMode.DEFAULT;
    public final SeatOrientation orientation = new SeatOrientation();

    // The fake mount is used when this seat has a position set, or otherwise cannot
    // mount the passenger to a parent attachment. The parentMountId is set to the
    // entity id of the vehicle this passenger is mounted to.
    public VirtualEntity fakeMount = null;
    public int parentMountId = -1;

    /**
     * Gets whether this seat is empty
     * 
     * @return True if empty
     */
    public boolean isEmpty() {
        return this._entity == null;
    }

    /**
     * Gets whether there is a player inside this seat
     * 
     * @return True if the seated entity is a Player
     */
    public boolean isPlayer() {
        return this._entity instanceof Player;
    }

    /**
     * Gets the entity currently inside the seat
     * 
     * @return seated entity
     */
    public Entity getEntity() {
        return this._entity;
    }

    /**
     * Sets the entity currently inside the seat
     * 
     * @param entity
     */
    public void setEntity(Entity entity) {
        this._entity = entity;
    }

    public DisplayMode getDisplayMode() {
        return this._displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this._displayMode = displayMode;
    }

    /**
     * Gets the entity id of the visible, seated entity. If a fake entity representation is used,
     * then the fake entity id is returned instead.
     * 
     * @return seated entity id
     */
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

    public boolean isUpsideDown() {
        return this._upsideDown;
    }

    public void setUpsideDown(boolean upsideDown) {
        this._upsideDown = upsideDown;
    }

    public void hideRealPlayer(Player viewer) {
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
    }

    /**
     * Sends the metadata information for the seated entity
     * 
     * @param viewer
     */
    public void refreshMetadata(Player viewer) {
        if (!this.isPlayer() && this._upsideDown) {
            // Apply metadata 'Dinnerbone' with nametag invisible
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME, ProfileNameModifier.UPSIDEDOWN.getPlayerName());
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            resetMetadata(viewer);
        }
    }

    /**
     * Resets the metadata of this seated entity to the actual metadata of the entity
     * 
     * @param viewer
     */
    public void resetMetadata(Player viewer) {
        DataWatcher metaTmp = EntityHandle.fromBukkit(this._entity).getDataWatcher();
        PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true));
    }

    public void makeVisible(CartAttachmentSeat seat, Player viewer, boolean fake) {
        // Spawn fake mount if one is needed
        if (this.parentMountId == -1) {
            // Use parent node for mounting point, unless not possible or we have a position set for the seat
            if (seat.getParent() != null && seat.getConfiguredPosition().isDefault()) {
                this.parentMountId = ((CartAttachment) seat.getParent()).getMountEntityId();
            }

            // No parent node mount is used, create a fake mount
            if (this.parentMountId == -1) {
                if (this.fakeMount == null) {
                    this.fakeMount = new VirtualEntity(seat.getManager());
                    this.fakeMount.setEntityType(EntityType.CHICKEN);
                    this.fakeMount.setSyncMode(SyncMode.SEAT);
                    this.fakeMount.setRelativeOffset(this.orientation.getMountOffset());

                    // Put the entity on a fake mount that we move around at an offset
                    this.fakeMount.updatePosition(seat.getTransform(), new Vector(0.0, (double) this.orientation.getMountYaw(), 0.0));
                    this.fakeMount.syncPosition(true);
                    this.fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                    this.fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                }
                this.parentMountId = this.fakeMount.getEntityId();
            }
        }

        // Spawn fake mount, if used
        if (this.fakeMount != null) {
            this.fakeMount.spawn(viewer, seat.calcMotion());

            // Also send zero-max-health if the viewer is the one sitting in the entity
            if (this._entity == viewer && Common.hasCapability("Common:PacketPlayOutUpdateAttributes:createZeroMaxHealth")) {
                PacketUtil.sendPacket(viewer, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this.fakeMount.getEntityId()));
            }
        }

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

    public void makeFakePlayerVisible(Player viewer) {
        // Respawn an upside-down player in its place
        Consumer<DataWatcher> metaFunction = metadata -> {
            if (_displayMode == DisplayMode.ELYTRA || _displayMode == DisplayMode.ELYTRA_SIT) {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING, true);
            } else {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING, false);
            }
        };
        if (this._upsideDown) {
            ProfileNameModifier.UPSIDEDOWN.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, this.orientation, metaFunction);
        } else {
            ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId, this.orientation, metaFunction);
        }

        // Unmount from the original vehicle and mount the new fake entity instead
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
        vmh.unmount(this.parentMountId, this._entity.getEntityId());
        vmh.mount(this.parentMountId, this._fakeEntityId);
    }

    public void makeHidden(Player viewer, boolean fake) {
        // Despawn the fake player entity
        if (fake) {
            makeFakePlayerHidden(viewer);
        }

        // Respawn the actual player or clean up the list
        // Only needed when the player is not the viewer
        if (this._fake && fake && isPlayer()) {
            if (viewer == this._entity) {
                // Can not respawn yourself! Only undo listing.
                ProfileNameModifier.NORMAL.sendListInfo(viewer, (Player) this._entity);
            } else {
                // Respawns the player as a normal player
                ProfileNameModifier.NORMAL.spawnPlayer(viewer, (Player) this._entity, this._entity.getEntityId(), null, meta -> {});
            }
        }

        // Resend the correct metadata for the entity/player
        if (!isEmpty()) {
            resetMetadata(viewer);
            PlayerUtil.getVehicleMountController(viewer).remove(this._entity.getEntityId());
        }

        if (this.fakeMount != null) {
            this.fakeMount.destroy(viewer);
        }
    }

    public void makeFakePlayerHidden(Player viewer) {
        if (this._fake && isPlayer()) {
            // Destroy old fake player entity
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId}));
            PlayerUtil.getVehicleMountController(viewer).remove(this._fakeEntityId);
        }
    }

    public static enum DisplayMode {
        DEFAULT, /* Player is displayed either upright or upside-down in a cart */
        ELYTRA_SIT, /* Player is in sitting pose while flying in an elytra */
        ELYTRA /* Player is in elytra flying pose */
    }
}

