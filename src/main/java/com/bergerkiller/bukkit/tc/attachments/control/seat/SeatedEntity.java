package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.function.Function;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.neznamytabnametaghider.TabNameTagHider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
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
    protected Entity entity = null;
    protected int tickEntered = -1;
    protected boolean showDummy = false; // Whether to show a dummy player sitting in the seat
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

    // Used to hide & restore custom TAB plugin nametags
    private TabNameTagHider.TabPlayerNameTagHider tabNameTagHider = null;

    public SeatedEntity(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    /**
     * Gets whether this seat is empty
     * 
     * @return True if empty
     */
    public boolean isEmpty() {
        return entity == null;
    }

    /**
     * Gets whether this seated entity displays anything. This is the case
     * when an entity is occupying this seat, or when debug display is active.
     *
     * @return True if this seated entity is displayed
     */
    public boolean isDisplayed() {
        return entity != null || showDummy;
    }

    /**
     * Gets whether a Player entity is inside this seat.
     * 
     * @return True if the seated entity is a Player
     */
    public boolean isPlayer() {
        return entity instanceof Player;
    }

    /**
     * Gets the entity currently inside the seat
     * 
     * @return seated entity
     */
    public Entity getEntity() {
        return this.entity;
    }

    /**
     * Gets the number of ticks this current Entity has been inside the seat.
     * Returns 0 if this seat has no passenger.
     *
     * @return Number of ticks the current passenger entity has been inside the seat
     */
    public int getTicksInSeat() {
        return (tickEntered == -1) ? 0 :(CommonUtil.getServerTicks() - tickEntered);
    }

    /**
     * Gets whether a dummy player is being displayed, providing no other
     * entity is inside the seat.
     *
     * @return Whether dummy player mode is active
     */
    public boolean isDummyPlayer() {
        return this.showDummy;
    }

    /**
     * Gets whether a dummy player is actually being displayed. If dummy
     * mode is active but a player is in the seat, returns false.
     *
     * @return True if the dummy player is displayed
     */
    public boolean isDummyPlayerDisplayed() {
        return this.showDummy && this.entity == null;
    }

    /**
     * Sets a player to act as a dummy entity displayed sitting in the seat
     *
     * @param show Whether to show the dummy player
     */
    public final void setShowDummyPlayer(boolean show) {
        if (this.showDummy != show) {
            this.showDummy = show;
            if (this.entity == null) {
                this.updateMode(true);
            }
        }
    }

    /**
     * Sets the entity currently inside the seat
     * 
     * @param entity
     */
    public final void setEntity(Entity entity) {
        // Restore nametag if one was hidden
        if (this.tabNameTagHider != null) {
            this.tabNameTagHider.show();
            this.tabNameTagHider = null;
        }

        // Update entity
        if (this.entity != entity) {
            this.tickEntered = (entity == null) ? -1 : CommonUtil.getServerTicks();
        }
        this.entity = entity;

        // Hide if view mode is no-nametag
        if (entity instanceof Player && this.getDisplayMode() == DisplayMode.NO_NAMETAG) {
            this.tabNameTagHider = seat.getPlugin().getTabNameHider((Player) entity);
            if (this.tabNameTagHider != null) {
                this.tabNameTagHider.hide();
            }
        }

        this.updateMode(true);
    }

    public DisplayMode getDisplayMode() {
        return this.displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    protected void hideRealPlayer(AttachmentViewer viewer) {
        if (this.entity == viewer.getPlayer()) {
            // Sync to self: make the real player invisible using a metadata change
            FirstPersonView.setPlayerVisible(viewer, false);
        } else {
            // Sync to others: destroy the original player
            viewer.getVehicleMountController().despawn(this.entity.getEntityId());
        }
    }

    protected void showRealPlayer(AttachmentViewer viewer) {
        // Respawn the actual player or clean up the list
        // Only needed when the player is not the viewer
        if (viewer.getPlayer() == this.entity) {
            // Can not respawn yourself! Make visible using metadata.
            FirstPersonView.setPlayerVisible(viewer, true);
        } else {
            // Respawns the player as a normal player
            VehicleMountController vmc = viewer.getVehicleMountController();
            vmc.respawn((Player) this.entity, (theViewer, thePlayer) -> {
                FakePlayerSpawner.NORMAL.spawnPlayer(theViewer, thePlayer, thePlayer.getEntityId(),
                        FakePlayerSpawner.FakePlayerPosition.ofPlayer(thePlayer),
                        meta -> {});
            });
        }
    }

    /**
     * Resets the metadata of this seated entity to the actual metadata of the entity
     * 
     * @param viewer
     */
    public void resetMetadata(AttachmentViewer viewer) {
        DataWatcher metaTmp = EntityHandle.fromBukkit(this.entity).getDataWatcher();
        viewer.send(PacketPlayOutEntityMetadataHandle.createNew(this.entity.getEntityId(), metaTmp, true));
    }

    /**
     * Gets the current exact head rotation of the Entity inside this seat. Returns a
     * default facing-forward if no entity, or the dummy player, is displayed. Takes
     * into account special fpv modes like spectator, where the head rotation is relative
     * to the seat orientation.
     *
     * @param transform Current body (butt) transformation of this seated entity
     * @return current head rotation
     */
    protected PassengerPose getCurrentHeadRotation(Matrix4x4 transform) {
        // When no entity is inside, show seat rotation, facing forwards (dummy player)
        if (this.isEmpty()) {
            return new PassengerPose(transform, transform.getRotation());
        }

        // If spectator mode is used, and is active, defer.
        if (seat.firstPerson instanceof FirstPersonViewSpectator && seat.firstPerson.player != null) {
            return new PassengerPose(transform, ((FirstPersonViewSpectator) seat.firstPerson)
                    .getCurrentHeadRotation(transform));
        }

        // If smooth coasters is used, then the player yaw/pitch is relative to the eye orientation
        // Defer to the Quaternion version of this method, and convert
        if (seat.useSmoothCoasters()) {
            return new PassengerPose(transform, getCurrentHeadRotationQuat(transform));
        }

        // Default: query the entity head pitch and yaw
        // When rotation is locked, use the body (transform) yaw
        EntityHandle entityHandle = EntityHandle.fromBukkit(entity);
        if (seat.isRotationLocked()) {
            return new PassengerPose(transform,
                    entityHandle.getPitch(),
                    entityHandle.getHeadRotation());
        } else {
            return new PassengerPose(entityHandle.getYaw(),
                    entityHandle.getPitch(),
                    entityHandle.getHeadRotation());
        }
    }

    /**
     * Same as {@link #getCurrentHeadRotation(Matrix4x4)} but returns a Quaternion instead.
     * If body rotation is locked, restricts head rotation to an appropriate amount.
     *
     * @param transform Current body (butt) transformation of this seated entity
     * @return current head rotation (as quaternion)
     */
    protected Quaternion getCurrentHeadRotationQuat(Matrix4x4 transform) {
        // When no entity is inside, show seat rotation, facing forwards (dummy player)
        if (this.isEmpty()) {
            return transform.getRotation();
        }

        // If spectator mode is used, and is active, defer.
        // No need to check for body locking, as that's already done in spectator mode at input stage.
        if (seat.firstPerson instanceof FirstPersonViewSpectator && seat.firstPerson.player != null) {
            return ((FirstPersonViewSpectator) seat.firstPerson).getCurrentHeadRotation(transform);
        }

        // If smooth coasters is used, then the player yaw/pitch is relative to the eye orientation
        if (seat.useSmoothCoasters()) {
            EntityHandle entityHandle = EntityHandle.fromBukkit(entity);
            float headYaw = entityHandle.getHeadRotation();
            float headPitch = entityHandle.getPitch();

            if (seat.isRotationLocked()) {
                headYaw = MathUtil.clamp(headYaw, FirstPersonView.BODY_LOCK_FOV_LIMIT);
            }

            Quaternion rotation = seat.firstPerson.getEyeTransform().getRotation();
            rotation.rotateY(-headYaw);
            rotation.rotateX(headPitch);
            return rotation;
        }

        if (seat.isRotationLocked()) {
            // Default: query the entity head pitch and yaw
            //          restrict head yaw to body yaw
            EntityHandle entityHandle = EntityHandle.fromBukkit(entity);
            PassengerPose pose = new PassengerPose(transform,
                    entityHandle.getPitch(),
                    entityHandle.getHeadRotation());

            pose = pose.limitHeadYaw(FirstPersonView.BODY_LOCK_FOV_LIMIT);

            Quaternion rotation = new Quaternion();
            rotation.rotateY(-pose.headYaw);
            rotation.rotateX(pose.headPitch);
            return rotation;
        } else {
            // Default: query the entity head pitch and yaw
            EntityHandle entityHandle = EntityHandle.fromBukkit(entity);
            Quaternion rotation = new Quaternion();
            rotation.rotateY(-entityHandle.getHeadRotation());
            rotation.rotateX(entityHandle.getPitch());
            return rotation;
        }
    }

    /**
     * If needed, spawns a vehicle mount to make an Entity id available for mounting
     * a passenger directly to the vehicle.
     *
     * @param viewer
     * @return vehicle mount id to which a passenger can be mounted
     */
    public int spawnVehicleMount(AttachmentViewer viewer) {
        // Spawn fake mount if one is needed
        if (this.parentMountId == -1) {
            // Use parent node for mounting point, unless not possible
            // Making use of SEAT_PARENT will disable any additional transforms
            // When seat rotation is locked, this cannot be used, because the sending of the 'real'
            // vehicles rotation messes with the seat rotation configured.
            if (seat.getConfiguredPosition().anchor == AttachmentAnchor.SEAT_PARENT &&
                seat.getConfiguredPosition().isIdentity() &&
                seat.getParent() != null &&
                !seat.isRotationLocked())
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
            if (this.entity == viewer.getPlayer()) {
                viewer.send(PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this.fakeMount.getEntityId()));
            }
        }

        return this.parentMountId;
    }

    /**
     * If a fake mount was created by {@link #spawnVehicleMount(AttachmentViewer)}, despawns
     * that fake mount. Otherwise does nothing.
     *
     * @param viewer
     */
    public void despawnVehicleMount(AttachmentViewer viewer) {
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

    public final void makeVisibleFirstPerson(AttachmentViewer viewer) {
        madeVisibleInFirstPerson = true;
        makeVisible(viewer);
    }

    public final void makeHiddenFirstPerson(AttachmentViewer viewer) {
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
     * Gets the seat-relative x/y/z offset away from the seat cameras should be positioned
     * to look exactly from where the player would normally look as this entity.
     *
     * @return camera offset for viewing from this entity in first-person
     */
    public abstract Vector getFirstPersonCameraOffset();

    /**
     * Gets whether this seated entity must be viewed with a fake third-person camera to
     * have the correct height offset.
     *
     * @return True if a fake camera mount should be used when viewed in first-person
     */
    public boolean isFirstPersonCameraFake() {
        // Only do it when the seat (butt) to camera (eye) offset is exactly vanilla
        // If not, we must use a fake mount to position it properly
        return this.getFirstPersonCameraOffset().getY() != VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
    }

    /**
     * Spawns this seated entity for a viewer. Mounts any real entity
     * into its seat.
     *
     * @param viewer
     */
    public abstract void makeVisible(AttachmentViewer viewer);

    /**
     * De-spawns this seated entity for a viewer. Unmounts any real entity
     * from the seat.
     *
     * @param viewer
     */
    public abstract void makeHidden(AttachmentViewer viewer);

    /**
     * Updates the display mode of the Entity. Display-specific operations can occur here.
     * Silent is set to true when the entity has just been set, because right after calling this
     * the seat is made visible to everyone again. No spawning should occur when silent
     * is true!
     *
     * @param silent Whether to send new spawn/make-visible packets to players or not
     */
    public void updateMode(boolean silent) {
        // Compute new first-person state of whether the player sees himself from third person using a fake camera
        FirstPersonViewMode new_firstPersonMode = this.seat.firstPerson.getMode();

        // No other mode is supported here
        if (new_firstPersonMode == FirstPersonViewMode.DYNAMIC) {
            new_firstPersonMode = FirstPersonViewMode.THIRD_P;
        }

        // If unchanged, do nothing
        if (new_firstPersonMode == seat.firstPerson.getLiveMode())
        {
            return;
        }

        // Sometimes a full reset of the FPV controller is required. Avoid when silent.
        AttachmentViewer viewer;
        if (!silent &&
            seat.firstPerson.doesViewModeChangeRequireReset(new_firstPersonMode) &&
            this.isPlayer() &&
            seat.getAttachmentViewersSynced().contains(viewer = seat.getManager().asAttachmentViewer((Player) this.getEntity())))
        {
            // Hide, change, and make visible again, just for the first-player-view player
            seat.makeHiddenImpl(viewer, true);
            seat.firstPerson.setLiveMode(new_firstPersonMode);
            seat.makeVisibleImpl(viewer, true);
            return;
        }

        // Silent update
        seat.firstPerson.setLiveMode(new_firstPersonMode);
    }

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
     * Called every time the attachment receives or loses focus.
     * This is the glowing effect that 'blinks'.
     *
     * @param focused
     */
    public abstract void updateFocus(boolean focused);

    /**
     * Creates a new suitable vehicle for putting passengers in. Passengers mounted to
     * this entity will be positioned so their butt is at the input transform.
     *
     * @return New vehicle
     */
    protected VirtualEntity createPassengerVehicle() {
        VirtualEntity mount = new VirtualEntity(seat.getManager());
        mount.setEntityType(EntityType.ARMOR_STAND);
        mount.setSyncMode(SyncMode.SEAT);
        mount.setUseMinecartInterpolation(seat.isMinecartInterpolation());
        mount.setRelativeOffset(0.0, -VirtualEntity.ARMORSTAND_BUTT_OFFSET, 0.0);
        mount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        mount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
        mount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        return mount;
    }

    /**
     * Gets whether this seated entity is using a particular Entity ID in its display.
     * Passenger entity ID is excluded.
     *
     * @param entityId
     * @return True if this entity ID is used for display
     */
    public abstract boolean containsEntityId(int entityId);

    public static enum DisplayMode {
        DEFAULT(SeatedEntityNormal::new), /* Player is displayed either upright or upside-down in a cart */
        ELYTRA_SIT(SeatedEntityElytra::new), /* Player is in sitting pose while flying in an elytra */
        STANDING(SeatedEntityStanding::new), /* Player is flying in a standing pose */
        HEAD(SeatedEntityHead::new), /* Players are replaced with player skulls with their face */
        NO_NAMETAG(SeatedEntityNormal::new), /* Same as DEFAULT, but no nametags are shown */
        INVISIBLE(SeatedEntityInvisible::new); /* Shows nothing, makes original passenger invisible */

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

    /**
     * Stores the passenger pose in a seat. This contains the yaw of the
     * body in the seat, and the yaw/pitch of the head of the entity.
     */
    public static class PassengerPose {
        public final float bodyYaw;
        public final float headPitch;
        public final float headYaw;

        public PassengerPose(Matrix4x4 bodyTransform, Quaternion headRotation) {
            this.bodyYaw = getMountYaw(bodyTransform);

            Vector ypr = headRotation.getYawPitchRoll();
            this.headPitch = (float) ypr.getX();
            this.headYaw = (float) ypr.getY();
        }

        public PassengerPose(Matrix4x4 bodyTransform, float headPitch, float headYaw) {
            this.bodyYaw = getMountYaw(bodyTransform);
            this.headPitch = headPitch;
            this.headYaw = headYaw;
        }

        public PassengerPose(float bodyYaw, float headPitch, float headYaw) {
            this.bodyYaw = bodyYaw;
            this.headPitch = headPitch;
            this.headYaw = headYaw;
        }

        /**
         * Transforms this pose to what should be used for upside-down players on Minecraft 1.17
         * and before. This works around a client bug on those versions.
         *
         * @return Upside-down fixed pose
         */
        public PassengerPose upsideDownFix_Pre_1_17() {
            return new PassengerPose(bodyYaw, -headPitch, -headYaw + 2.0f * bodyYaw);
        }

        public PassengerPose limitHeadYaw(float limit) {
            if (MathUtil.getAngleDifference(headYaw, bodyYaw) > limit) {
                if (MathUtil.getAngleDifference(headYaw, bodyYaw + limit) <
                    MathUtil.getAngleDifference(headYaw, bodyYaw - limit)) {
                    return new PassengerPose(bodyYaw, headPitch, bodyYaw + limit);
                } else {
                    return new PassengerPose(bodyYaw, headPitch, bodyYaw - limit);
                }
            }
            return this;
        }

        private static float getMountYaw(Matrix4x4 transform) {
            Vector f = transform.getRotation().forwardVector();
            return MathUtil.getLookAtYaw(-f.getZ(), f.getX());
        }
    }
}

