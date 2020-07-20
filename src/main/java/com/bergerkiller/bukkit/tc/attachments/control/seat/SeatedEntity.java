package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutUpdateAttributesHandle;

/**
 * Represents the visible, seated entity inside a seat. Sometimes this entity is a fake
 * version of the actual entity inside in order to apply additional transformations to it,
 * without the actual player entity sending packets that can corrupt it.
 */
public abstract class SeatedEntity {
    protected Entity _entity = null;
    protected DisplayMode _displayMode = DisplayMode.DEFAULT;
    protected final CartAttachmentSeat seat;
    public final SeatOrientation orientation = new SeatOrientation();

    // The fake mount is used when this seat has a position set, or otherwise cannot
    // mount the passenger to a parent attachment. The parentMountId is set to the
    // entity id of the vehicle this passenger is mounted to.
    public VirtualEntity fakeMount = null;
    public int parentMountId = -1;

    public SeatedEntity(CartAttachmentSeat seat) {
        this.seat = seat;
    }

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
    public abstract int getId();

    protected void hideRealPlayer(Player viewer) {
        if (this._entity == viewer) {
            // Sync to self: make the real player invisible using a metadata change
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(viewer.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            // Sync to others: destroy the original player
            PlayerUtil.getVehicleMountController(viewer).despawn(this._entity.getEntityId());
        }
    }

    /**
     * Sends the metadata information for the seated entity
     * 
     * @param viewer
     */
    public void refreshMetadata(Player viewer) {
        resetMetadata(viewer);
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

    public void makeVisible(Player viewer, boolean fake) {
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
                    this.fakeMount.setEntityType(EntityType.ARMOR_STAND);
                    this.fakeMount.setSyncMode(SyncMode.SEAT);
                    this.fakeMount.setRelativeOffset(this.orientation.getMountOffset());

                    // Put the entity on a fake mount that we move around at an offset
                    this.fakeMount.updatePosition(seat.getTransform(), new Vector(0.0, (double) this.orientation.getMountYaw(), 0.0));
                    this.fakeMount.syncPosition(true);
                    this.fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                    this.fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                    this.fakeMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                            EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                            EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                            EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
                }
                this.parentMountId = this.fakeMount.getEntityId();
            }
        }

        // Spawn fake mount, if used
        if (this.fakeMount != null) {
            this.fakeMount.spawn(viewer, seat.calcMotion());

            // Also send zero-max-health if the viewer is the one sitting in the entity
            if (this._entity == viewer) {
                PacketUtil.sendPacket(viewer, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this.fakeMount.getEntityId()));
            }
        }
    }

    public void makeHidden(Player viewer, boolean fake) {
        // Resend the correct metadata for the entity/player
        if (!isEmpty()) {
            resetMetadata(viewer);
            PlayerUtil.getVehicleMountController(viewer).unmount(this.parentMountId, this._entity.getEntityId());
        }

        if (this.fakeMount != null) {
            this.fakeMount.destroy(viewer);
        }
    }

    /**
     * Updates the display mode of the Entity. Display-specific operations can occur here.
     * Silent is set to true when the entity has just been set, because right after calling this
     * the seat is made visible to everyone again. No spawning should occur when silent
     * is true!
     * 
     * @param seat
     * @param silent Whether to send new spawn/make-visible packets to players or not
     */
    public abstract void updateMode(boolean silent);

    /**
     * Gets whether this seat is invisible to a viewer. This is the case if the viewer
     * is the entity inside this seat and the INVISIBLE first person view mode is used.
     * 
     * @param viewer
     * @return True if the viewer can not see the entity inside this seat
     */
    public boolean isInvisibleTo(Player viewer) {
        return this._entity == viewer && this.seat.firstPerson.getLiveMode() == FirstPersonViewMode.INVISIBLE;
    }

    public static enum DisplayMode {
        DEFAULT, /* Player is displayed either upright or upside-down in a cart */
        ELYTRA_SIT /* Player is in sitting pose while flying in an elytra */
        //ELYTRA /* Player is in elytra flying pose */ //TODO!
    }

    /*
     * Copied from BKCommonLib 1.15.2 Quaternion getPitch()
     * Once we depend on 1.15.2 or later, this can be removed and replaced with transform.getRotationPitch()
     */
    public static double getQuaternionPitch(double x, double y, double z, double w) {
        final double test = 2.0 * (w * x - y * z);
        if (Math.abs(test) < (1.0 - 1E-15)) {
            double pitch = Math.asin(test);
            double roll_x = 0.5 - (x * x + z * z);
            if (roll_x <= 0.0 && (Math.abs((w * z + x * y)) > roll_x)) {
                pitch = -pitch;
                pitch += (pitch < 0.0) ? Math.PI : -Math.PI;
            }
            return Math.toDegrees(pitch);
        } else if (test < 0.0) {
            return -90.0;
        } else {
            return 90.0;
        }
    }
}

