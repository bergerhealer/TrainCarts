package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.function.Function;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Represents the visible, seated entity inside a seat. Sometimes this entity is a fake
 * version of the actual entity inside in order to apply additional transformations to it,
 * without the actual player entity sending packets that can corrupt it.
 */
public abstract class SeatedEntity {
    protected Entity _entity = null;
    protected DisplayMode displayMode = DisplayMode.DEFAULT;
    protected final CartAttachmentSeat seat;
    public final SeatOrientation orientation = new SeatOrientation();

    // The fake mount is used when this seat has a position set, or otherwise cannot
    // mount the passenger to a parent attachment. The parentMountId is set to the
    // entity id of the vehicle this passenger is mounted to.
    private VirtualEntity fakeMount = null;
    public int parentMountId = -1;

    // Whether this seated entity was spawned to the player itself
    // This is the case when it is displayed in first-person, as a third-person view mode
    // This is set/unset when makeVisibleFirstPerson and makeHiddenFirstPerson are called.
    private boolean madeVisibleInFirstPerson = false;

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
        return this.displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    protected void hideRealPlayer(Player viewer) {
        if (this._entity == viewer) {
            // Sync to self: make the real player invisible using a metadata change
            FirstPersonView.setPlayerVisible(viewer, false);
        } else {
            // Sync to others: destroy the original player
            PlayerUtil.getVehicleMountController(viewer).despawn(this._entity.getEntityId());
        }
    }

    protected void showRealPlayer(Player viewer) {
        // Respawn the actual player or clean up the list
        // Only needed when the player is not the viewer
        if (viewer == this._entity) {
            // Can not respawn yourself! Make visible using metadata.
            FirstPersonView.setPlayerVisible(viewer, true);
        } else {
            // Respawns the player as a normal player
            VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
            vmc.respawn((Player) this._entity, (theViewer, thePlayer) -> {
                FakePlayerSpawner.NORMAL.spawnPlayer(theViewer, thePlayer, thePlayer.getEntityId(), false, null, meta -> {});
            });
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

    /**
     * If needed, spawns a vehicle mount to make an Entity id available for mounting
     * a passenger directly to the vehicle.
     *
     * @param viewer
     * @return vehicle mount id to which a passenger can be mounted
     */
    public int spawnVehicleMount(Player viewer) {        
        // Spawn fake mount if one is needed
        if (this.parentMountId == -1) {
            // Use parent node for mounting point, unless not possible
            // Making use of SEAT_PARENT will disable any additional transforms
            if (seat.getConfiguredPosition().anchor == AttachmentAnchor.SEAT_PARENT &&
                seat.getConfiguredPosition().isIdentity() &&
                seat.getParent() != null)
            {
                this.parentMountId = ((CartAttachment) seat.getParent()).getMountEntityId();
            }

            // No parent node mount is used, create a fake mount
            if (this.parentMountId == -1) {
                if (this.fakeMount == null) {
                    this.fakeMount = createPassengerVehicle();
                    this.fakeMount.updatePosition(seat.getTransform(), new Vector(0.0, (double) this.orientation.getMountYaw(), 0.0));
                    this.fakeMount.syncPosition(true);
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

        return this.parentMountId;
    }

    /**
     * If a fake mount was created by {@link #spawnVehicleMount(Player)}, despawns
     * that fake mount. Otherwise does nothing.
     *
     * @param viewer
     */
    public void despawnVehicleMount(Player viewer) {
        if (fakeMount != null) {
            fakeMount.destroy(viewer);

            // If no more viewers use it, reset
            if (!fakeMount.hasViewers()) {
                fakeMount = null;
                parentMountId = -1;
            }
        }
    }

    protected void updateVehicleMountPosition(Matrix4x4 transform) {
        if (fakeMount != null) {
            fakeMount.updatePosition(transform, new Vector(0.0, (double) this.orientation.getMountYaw(), 0.0));
        }
    }

    protected void syncVehicleMountPosition(boolean absolute) {
        if (fakeMount != null) {
            fakeMount.syncPosition(absolute);
        }
    }

    public final void makeVisibleFirstPerson(Player viewer) {
        madeVisibleInFirstPerson = true;
        makeVisible(viewer);
    }

    public final void makeHiddenFirstPerson(Player viewer) {
        makeHidden(viewer);
        madeVisibleInFirstPerson = false;
    }

    /**
     * Whether this seated entity is spawned to itself - the entity being a Player.
     * This is true when the player can view himself sitting from a third-person
     * perspective.
     *
     * @return True if made visible in first-person
     */
    public final boolean isMadeVisibleInFirstPerson() {
        return madeVisibleInFirstPerson;
    }

    /**
     * Gets the seat-relative x/y/z offset away from the seat cameras should be positioned
     * to view this seated entity in third-person. For large entities, this should be
     * further away than closer-by ones.
     *
     * @return camera offset for viewing this entity in THIRD_P mode
     */
    public abstract Vector getThirdPersonCameraOffset();

    /**
     * Spawns this seated entity for a viewer. Mounts any real entity
     * into its seat.
     *
     * @param viewer
     */
    public abstract void makeVisible(Player viewer);

    /**
     * De-spawns this seated entity for a viewer. Unmounts any real entity
     * from the seat.
     *
     * @param viewer
     */
    public abstract void makeHidden(Player viewer);

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
     * Called to update the third-person viewed orientation of this seated entity
     *
     * @param transform
     */
    public abstract void updatePosition(Matrix4x4 transform);

    /**
     * Called to send periodic movement update packets
     *
     * @param absolute True if this is an absolute position update
     */
    public abstract void syncPosition(boolean absolute);

    /**
     * Creates a new suitable vehicle for putting passengers in. Passengers mounted to
     * this entity will be positioned so their butt is at the input transform.
     *
     * @return New vehicle
     */
    protected VirtualEntity createPassengerVehicle() {
        VirtualEntity mount = new VirtualEntity(seat.getManager());
        mount.setEntityType(EntityType.ARMOR_STAND);
        mount.setSyncMode(seat.isMinecartInterpolation() ? SyncMode.SEAT_MINECART_FIX : SyncMode.SEAT);
        mount.setRelativeOffset(0.0, -VirtualEntity.ARMORSTAND_BUTT_OFFSET, 0.0);
        mount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        mount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
        mount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        return mount;
    }

    public static enum DisplayMode {
        DEFAULT(SeatedEntityNormal::new), /* Player is displayed either upright or upside-down in a cart */
        ELYTRA_SIT(SeatedEntityElytra::new), /* Player is in sitting pose while flying in an elytra */
        NO_NAMETAG(SeatedEntityNormal::new); /* Same as DEFAULT, but no nametags are shown */

        private final Function<CartAttachmentSeat, SeatedEntity> _constructor;

        private DisplayMode(Function<CartAttachmentSeat, SeatedEntity> constructor) {
            this._constructor = constructor;
        }

        public SeatedEntity create(CartAttachmentSeat seat) {
            SeatedEntity seated = _constructor.apply(seat);
            seated.setDisplayMode(this);
            return seated;
        }
    }
}

