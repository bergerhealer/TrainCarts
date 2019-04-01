package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.old.FakePlayer;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

public class CartAttachmentSeat extends CartAttachment {
    private boolean _upsideDown = false;
    private boolean _useVirtualCamera = false;
    private boolean _hideRealPlayerNextTick = false;
    private Entity _entity = null;
    private int _fakeEntityId = -1;
    private int _fakeEntityLastYaw = 0;
    private int _fakeEntityLastPitch = 0;
    private int _fakeEntityLastHeadYaw = 0;
    private int _fakeEntityRotationCtr = 0;
    private VirtualEntity _fakeCameraMount = null;
    private VirtualEntity _fakeMount = null; // This mount is moved where the passenger should be
    private int _parentMountId = -1;
    private boolean _rotationLocked = false;
    private ObjectPosition _ejectPosition = new ObjectPosition();
    private boolean _ejectLockRotation = false;

    public void updateSeater() {
        for (Player viewer : this.getViewers()) {
            updateSeater(viewer);
        }
    }

    public void updateSeater(Player viewer) {
        if (this._entity == null) {
            return;
        }

        // Find a parent to mount to
        if (this._parentMountId == -1) {
            // Use parent node for mounting point, unless not possible or we have a position set for the seat
            if (this.getParent() != null && this.getConfiguredPosition().isDefault()) {
                this._parentMountId = ((CartAttachment) this.getParent()).getMountEntityId();
            }

            // No parent node mount is used, we have to create our own!
            if (this._parentMountId == -1) {
                if (this._fakeMount == null) {
                    this._fakeMount = new VirtualEntity(this.getController());
                    this._fakeMount.setEntityType(EntityType.CHICKEN);
                    this._fakeMount.setRelativeOffset(0.0, -0.625, 0.0);
                    this._fakeMount.setSyncMode(SyncMode.SEAT);

                    // Put the entity on a fake mount that we move around at an offset
                    this._fakeMount.updatePosition(this.getTransform());
                    this._fakeMount.syncPosition(true);
                    this._fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                    this._fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);

                    // Spawn for ALL viewers
                    Vector motion = calcMotion();
                    for (Player all_viewers_viewer : this.getViewers()) {
                        this._fakeMount.spawn(all_viewers_viewer, motion);
                    }
                }
                this._parentMountId = this._fakeMount.getEntityId();
            }
        }

        PassengerController pc = this.getController().getPassengerController(viewer);

        if ((this._fakeEntityId != -1) && (this._upsideDown || (this._useVirtualCamera && viewer == this._entity))) {
            pc.unmount(this._parentMountId, this._entity.getEntityId());
            pc.mount(this._parentMountId, this._fakeEntityId);
        } else {
            pc.unmount(this._parentMountId, this._fakeEntityId);
            pc.mount(this._parentMountId, this._entity.getEntityId());
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this._rotationLocked = this.getConfig().get("lockRotation", false);

        ConfigurationNode ejectPosition = this.getConfig().getNode("ejectPosition");
        this._ejectPosition.load(ejectPosition);
        this._ejectLockRotation = ejectPosition.get("lockRotation", false);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.setEntity(null);
    }

    @Override
    public void makeVisible(Player viewer) {
        if (this._entity == null) {
            return;
        }

        boolean useVirtualCamera = (this._upsideDown && this._entity instanceof Player) ||
                (this._useVirtualCamera && this._entity == viewer);


        if (useVirtualCamera) {
            // Use a fake entity
            if (this._fakeEntityId == -1) {
                this._fakeEntityId = EntityUtil.getUniqueEntityId();
            }

            this.hideRealPlayer(viewer);

            // Mount the player on an invisible entity we control
            if (this._entity == viewer && this._fakeCameraMount == null) {
                this._fakeCameraMount = new VirtualEntity(this.getController());

                this._fakeCameraMount.setEntityType(EntityType.CHICKEN);
                this._fakeCameraMount.setPosition(new Vector(0.0, 1.0, 0.0));
                this._fakeCameraMount.setRelativeOffset(0.0, -1.32, 0.0);
                this._fakeCameraMount.setSyncMode(SyncMode.SEAT);

                // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                this._fakeCameraMount.updatePosition(this.getTransform());
                this._fakeCameraMount.syncPosition(true);
                this._fakeCameraMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                this._fakeCameraMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                this._fakeCameraMount.spawn(viewer, calcMotion());
                this._fakeCameraMount.syncPosition(true);

                this.getController().getPassengerController(viewer).mount(this._fakeCameraMount.getEntityId(), this._entity.getEntityId());
            }

            // Respawn an upside-down player
            if (this._upsideDown) {
                ProfileNameModifier.UPSIDEDOWN.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId);
            } else {
                ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId);
            }
        } else {
            // Refreshes metadata - will set nametag to make other entities render upside-down
            this.refreshMetadata(viewer, false);
        }

        // Respawn fake mount, if used
        if (this._fakeMount != null) {
            this._fakeMount.spawn(viewer, calcMotion());
        }

        // Re-attach entity to it's appropriate mount
        this.updateSeater(viewer);

        // If rotation locked, send the rotation of the passenger if available
        if (this.isRotationLocked() && this._entity != null) {
            int entityId = (this._fakeEntityId != -1) ? this._fakeEntityId : this._entity.getEntityId();

            // Do not send viewer to self - bad things happen
            if (entityId != viewer.getEntityId()) {
                CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, entityId);
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) this._fakeEntityLastHeadYaw);
                PacketUtil.sendPacket(viewer, headPacket);

                CommonPacket lookPacket = PacketType.OUT_ENTITY_LOOK.newInstance();
                lookPacket.write(PacketType.OUT_ENTITY_LOOK.entityId, entityId);
                lookPacket.write(PacketPlayOutEntityHandle.T.dyaw_raw.toFieldAccessor(), (byte) this._fakeEntityLastYaw);
                lookPacket.write(PacketPlayOutEntityHandle.T.dpitch_raw.toFieldAccessor(), (byte) this._fakeEntityLastPitch);
                PacketUtil.sendPacket(viewer, lookPacket);
            }
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        if (this._entity instanceof Player && this._fakeEntityId != -1) {
            // Destroy old fake player entity
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId}));

            // Respawns the player as a normal player
            // Only needed when the player is not the viewer
            if (viewer == this._entity) {
                // Can not respawn yourself! Only undo listing.
                ProfileNameModifier.NORMAL.sendListInfo(viewer, (Player) this._entity);
            } else {
                // Respawns the player as a normal player
                ProfileNameModifier.NORMAL.spawnPlayer(viewer, (Player) this._entity, this._entity.getEntityId());
            }
        }
        if (this._entity == viewer && this._fakeCameraMount != null) {
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }
        if (this._fakeMount != null) {
            this._fakeMount.destroy(viewer);
        }

        // reset the metadata and passenger info
        if (this._entity != null) {
            this.refreshMetadata(viewer, true);

            PassengerController pc = this.getController().getPassengerController(viewer);
            pc.remove(this._entity.getEntityId(), false);
            if (this._fakeEntityId != -1) {
                pc.remove(this._fakeEntityId, false);
            }
        }
    }

    protected Vector calcMotion() {
        AttachmentInternalState state = this.getInternalState();
        Vector pos_old = state.last_transform.toVector();
        Vector pos_new = state.curr_transform.toVector();
        return pos_new.subtract(pos_old);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        if (this._fakeMount != null &&
            this.getConfiguredPosition().isDefault() &&
            this.getParent() != null)
        {
            this.getParent().applyDefaultSeatTransform(transform);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        if (this._entity instanceof Player && this._fakeEntityId != -1) {
            // When upside-down, we have a virtual camera for the player
            // This is controlled by having the player mounted on an invisible entity that we control
            // This entity must be updated here to have the correct position of the camera
            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(this.getTransform());
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
        if (this._fakeMount != null) {
            this._fakeMount.updatePosition(this.getTransform());
            this._fakeMount.syncPosition(absolute);
        }
    }

    /**
     * Whether the passengers inside have their rotation locked based on the orientation of this seat
     * 
     * @return True if rotation is locked
     */
    public boolean isRotationLocked() {
        return this._rotationLocked;
    }

    /**
     * Gets the Entity that is displayed and controlled in this seat
     * 
     * @return seated entity
     */
    public Entity getEntity() {
        return this._entity;
    }

    /**
     * Sets the Entity that is displayed and controlled.
     * Any previously set entity is reset to the defaults.
     * The new entity has seated entity specific settings applied to it.
     * 
     * @param entity to set to
     */
    public void setEntity(Entity entity) {
        if (this._entity == entity) {
            return;
        }

        // If a previous entity was set, unseat it
        if (this._entity != null) {
            for (PassengerController pc : this.getController().getPassengerControllers()) {
                pc.unmount(this._parentMountId, this._entity.getEntityId());
            }
            if (this._fakeCameraMount != null && this._entity instanceof Player) {
                this.getController().getPassengerController((Player) this._entity).unmount(this._fakeCameraMount.getEntityId(), this._entity.getEntityId());
            }
            for (Player viewer : this.getViewers()) {
                this.makeHidden(viewer);
            }
            TrainCarts.plugin.getSeatAttachmentMap().remove(this._entity.getEntityId(), this);
        }

        // Switch entity
        this._entity = entity;
        this._fakeEntityId = -1;
        this._hideRealPlayerNextTick = (entity instanceof Player);

        // Re-seat new entity
        if (this._entity != null) {
            TrainCarts.plugin.getSeatAttachmentMap().set(this._entity.getEntityId(), this);
            for (Player viewer : this.getViewers()) {
                this.makeVisible(viewer);
            }
        }
    }

    /**
     * Gets whether the seated entity is displayed sitting upside-down
     * 
     * @return True if upside-down
     */
    public boolean isUpsideDown() {
        return this._upsideDown;
    }

    /**
     * Sets whether the seated entity is displayed sitting upside-down
     * 
     * @param upsideDown state to set to
     */
    public void setUpsideDown(boolean upsideDown) {
        if (this._upsideDown == upsideDown) {
            return;
        }

        if (this._entity instanceof Player) {
            // Despawn the old player to all viewers
            for (Player viewer : this.getViewers()) {
                this.makeHidden(viewer);
            }

            // Change it
            this._upsideDown = upsideDown;
            if (!this._upsideDown) {
                this._fakeEntityId = -1;
            }

            // Respawn the new player to all viewers with the upside-down state changed
            for (Player viewer : this.getViewers()) {
                this.makeVisible(viewer);
            }
        } else {
            // Change it
            this._upsideDown = upsideDown;

            // Refresh metadata to reflect this change for the Entity
            // We achieve the upside-down state using nametags
            if (this._entity != null) {
                for (Player viewer : this.getViewers()) {
                    this.refreshMetadata(viewer, false);
                }
            }
        }
    }

    /**
     * Gets whether the viewer uses a virtual camera instead of the view from the Player entity itself
     * Only has effect if the entity is a Player.
     * When upside-down, a virtual camera is automatically used.
     * 
     * @return true if a virtual camera is used
     */
    public boolean useVirtualCamera() {
        return this._useVirtualCamera;
    }

    /**
     * Sets whether the viewer uses a virtual camera instead of the view from the Player entity itself.
     * Only has effect if the entity is a Player.
     * When upside-down, a virtual camera is automatically used.
     * 
     * @param use option
     */
    public void setUseVirtualCamera(boolean use) {
        if (this._useVirtualCamera == use) {
            return;
        }

        // When the entity isn't a player or is already shown upside-down, ignore further operations
        if (!(this._entity instanceof Player) || this._upsideDown || !this.getViewers().contains(this._entity)) {
            this._useVirtualCamera = use;
            return;
        }

        // Not upside-down and the virtual camera must be activated or de-activated for the entity only
        this.makeHidden((Player) this._entity);
        this._useVirtualCamera = use;
        this.makeVisible((Player) this._entity);
    }

    public int getPassengerYaw() {
        return this._fakeEntityLastYaw;
    }

    public int getPassengerPitch() {
        return this._fakeEntityLastPitch;
    }

    public int getPassengerHeadYaw() {
        return this._fakeEntityLastHeadYaw;
    }

    /**
     * Calculates the eject position of the seat
     * 
     * @param passenger to check eject position for
     * @return eject position
     */
    public Location getEjectPosition(Entity passenger) {
        CartProperties cprop = this.getController().getMember().getProperties();

        Matrix4x4 tmp = this.getTransform().clone();
        this._ejectPosition.anchor.apply(this, tmp);

        // Translate eject offset specified in the cart's properties
        tmp.translate(cprop.exitOffset);

        // Apply transformation of eject position (translation, then rotation)
        tmp.multiply(this._ejectPosition.transform);

        // Apply eject rotation specified in the cart's properties on top
        tmp.rotateYawPitchRoll(cprop.exitPitch, cprop.exitYaw, 0.0f);

        org.bukkit.World w = this.getController().getEntity().getWorld();
        Vector pos = tmp.toVector();
        Vector ypr = tmp.getYawPitchRoll();
        float yaw = (float) ypr.getY();
        float pitch = (float) ypr.getX();

        // When rotation is not locked, preserve original orientation of passenger
        if (!this._ejectLockRotation && passenger != null) {
            Location curr_loc;
            if (passenger instanceof LivingEntity) {
                curr_loc = ((LivingEntity) passenger).getEyeLocation();
            } else {
                curr_loc = passenger.getLocation();
            }
            yaw = curr_loc.getYaw();
            pitch = curr_loc.getPitch();
        }

        return new Location(w, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
    }

    @Override
    public boolean isHiddenWhenInactive() {
        return false;
    }

    @Override
    public void onTick() {
        float selfPitch = (float) this.getTransform().getYawPitchRoll().getX();

        if (MathUtil.getAngleDifference(selfPitch, 180.0f) < 89.0f) {
            // Beyond the point where the entity should be rendered upside-down
            setUpsideDown(true);
        } else if (MathUtil.getAngleDifference(selfPitch, 0.0f) < 89.0f) {
            // Beyond the point where the entity should be rendered normally again
            setUpsideDown(false);
        }

        if (TCConfig.enableSeatThirdPersonView && ((selfPitch < -46.0f) || (selfPitch > 46.0f))) {
            setUseVirtualCamera(true);
        } else {
            setUseVirtualCamera(false);
        }

        // Hides the real player a second time the next tick after the entity in the seat was switched
        // This patches some glitches when a player switches between minecarts (ejects & enters in 1 tick)
        // Hiding is only done when the player is actually invisible
        if (this._hideRealPlayerNextTick) {
            this._hideRealPlayerNextTick = false;
            if (this._entity != null) {
                for (Player viewer : this.getViewers()) {
                    if (this._upsideDown || (this._useVirtualCamera && viewer == this._entity)) {
                        this.hideRealPlayer(viewer);
                    }
                }
            }
        }

        if (this._entity != null && this.isRotationLocked()) {
            EntityHandle realPlayer = EntityHandle.fromBukkit(this._entity);
            float yaw;
            if (this._fakeMount != null) {
                yaw = (float) this._fakeMount.getYawPitchRoll().getY();
            } else {
                yaw = (float) this.getTransform().getYawPitchRoll().getY();
            }

            float pitch = realPlayer.getPitch();
            float headRot = realPlayer.getHeadRotation();

            // Reverse the values and correct head yaw, because the player is upside-down
            if (this._upsideDown) {
                pitch = -pitch;
                headRot = -headRot + 2.0f * yaw;
            }

            // Limit head rotation within range of yaw
            final float HEAD_ROT_LIM = 30.0f;
            if (MathUtil.getAngleDifference(headRot, yaw) > HEAD_ROT_LIM) {
                if (MathUtil.getAngleDifference(headRot, yaw + HEAD_ROT_LIM) <
                    MathUtil.getAngleDifference(headRot, yaw - HEAD_ROT_LIM)) {
                    headRot = yaw + HEAD_ROT_LIM;
                } else {
                    headRot = yaw - HEAD_ROT_LIM;
                }
            }

            // Protocolify
            int entityId = (this._fakeEntityId != -1) ? this._fakeEntityId : this._entity.getEntityId();
            int protYaw = EntityTrackerEntryHandle.getProtocolRotation(yaw);
            int protPitch = EntityTrackerEntryHandle.getProtocolRotation(pitch);
            int protHeadRot = EntityTrackerEntryHandle.getProtocolRotation(headRot);

            // Refresh head rotation
            if (protHeadRot != this._fakeEntityLastHeadYaw) {
                CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, entityId);
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
                for (Player viewer : this.getViewers()) {
                    if (viewer.getEntityId() != entityId) {
                        PacketUtil.sendPacket(viewer, headPacket);
                    }
                }
                this._fakeEntityLastHeadYaw = protHeadRot;
            }

            // Refresh body yaw and head pitch
            // Repeat this packet every 15 ticks to make sure the entity's orientation stays correct
            // The client will automatically rotate the body towards the head after a short delay
            // Sending look packets regularly prevents that from happening
            if (this._fakeEntityRotationCtr == 0 || protYaw != this._fakeEntityLastYaw || protPitch != this._fakeEntityLastPitch) {
                this._fakeEntityRotationCtr = 10;

                CommonPacket lookPacket = PacketType.OUT_ENTITY_LOOK.newInstance();
                lookPacket.write(PacketType.OUT_ENTITY_LOOK.entityId, entityId);
                lookPacket.write(PacketPlayOutEntityHandle.T.dyaw_raw.toFieldAccessor(), (byte) protYaw);
                lookPacket.write(PacketPlayOutEntityHandle.T.dpitch_raw.toFieldAccessor(), (byte) protPitch);
                for (Player viewer : this.getViewers()) {
                    if (viewer.getEntityId() != entityId) {
                        PacketUtil.sendPacket(viewer, lookPacket);
                    }
                }
                this._fakeEntityLastYaw = protYaw;
                this._fakeEntityLastPitch = protPitch;
            } else {
                this._fakeEntityRotationCtr--;
            }
        } else {
            // Refresh head rotation and body yaw/pitch for a fake player entity
            if (this._entity instanceof Player && this._fakeEntityId != -1) {
                EntityHandle realPlayer = EntityHandle.fromBukkit(this._entity);
                float yaw = realPlayer.getYaw();
                float pitch = realPlayer.getPitch();
                float headRot = realPlayer.getHeadRotation();

                // Reverse the values and correct head yaw, because the player is upside-down
                if (this._upsideDown) {
                    pitch = -pitch;
                    headRot = -headRot + 2.0f * yaw;
                }

                // Protocolify
                int protYaw = EntityTrackerEntryHandle.getProtocolRotation(yaw);
                int protPitch = EntityTrackerEntryHandle.getProtocolRotation(pitch);
                int protHeadRot = EntityTrackerEntryHandle.getProtocolRotation(headRot);

                if (protYaw != this._fakeEntityLastYaw || protPitch != this._fakeEntityLastPitch) {
                    CommonPacket lookPacket = PacketType.OUT_ENTITY_LOOK.newInstance();
                    lookPacket.write(PacketType.OUT_ENTITY_LOOK.entityId, this._fakeEntityId);
                    lookPacket.write(PacketPlayOutEntityHandle.T.dyaw_raw.toFieldAccessor(), (byte) protYaw);
                    lookPacket.write(PacketPlayOutEntityHandle.T.dpitch_raw.toFieldAccessor(), (byte) protPitch);
                    for (Player viewer : this.getViewers()) {
                        PacketUtil.sendPacket(viewer, lookPacket);
                    }
                    this._fakeEntityLastYaw = protYaw;
                    this._fakeEntityLastPitch = protPitch;
                }

                if (protHeadRot != this._fakeEntityLastHeadYaw) {
                    CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                    headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this._fakeEntityId);
                    headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
                    for (Player viewer : this.getViewers()) {
                        PacketUtil.sendPacket(viewer, headPacket);
                    }
                    this._fakeEntityLastHeadYaw = protHeadRot;
                }
            }
        }
    }

    private void hideRealPlayer(Player viewer) {
        if (this._entity == viewer) {
            // Sync to self: make the real player invisible using a metadata change
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true);
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
     * @param reset when true, resets metadata for the entity at all times
     */
    private void refreshMetadata(Player viewer, boolean reset) {
        if (!(this._entity instanceof Player) && this._upsideDown && !reset) {
            // Apply metadata 'Dinnerbone' with nametag invisible
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME, FakePlayer.DisplayMode.UPSIDEDOWN.getPlayerName());
            metaTmp.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            // Send the 'real' metadata state of the Entity to the viewer to restore nametags / disable invisibility
            DataWatcher metaTmp = EntityHandle.fromBukkit(this._entity).getDataWatcher();
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this._entity.getEntityId(), metaTmp, true));
        }
    }

}
