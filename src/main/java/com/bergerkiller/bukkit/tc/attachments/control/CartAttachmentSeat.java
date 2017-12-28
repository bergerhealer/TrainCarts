package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.old.FakePlayer;
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
    private VirtualEntity _fakeCameraMount = null;
    private VirtualEntity _fakeMount = null; // This mount is moved where the passenger should be
    private int _parentMountId = -1;
    private boolean _hasPosition = false;

    public void updateSeater() {
        for (Player viewer : this.controller.getViewers()) {
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
            if (!this._hasPosition) {
                this._parentMountId = this.parent.getMountEntityId();
            }

            // No parent node mount is used, we have to create our own!
            if (this._parentMountId == -1) {
                if (this._fakeMount == null) {
                    this._fakeMount = new VirtualEntity(this.controller);
                    this._fakeMount.setEntityType(EntityType.CHICKEN);
                    this._fakeMount.setRelativeOffset(0.0, -0.625, 0.0);
                    this._fakeMount.setHasRotation(false);

                    // Put the entity on a fake mount that we move around at an offset
                    this._fakeMount.updatePosition(this.transform);
                    this._fakeMount.syncPosition(true);
                    this._fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                    this._fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);

                    // Spawn for ALL viewers
                    Vector motion = calcMotion();
                    for (Player all_viewers_viewer : this.controller.getViewers()) {
                        this._fakeMount.spawn(all_viewers_viewer, motion);
                    }
                }
                this._parentMountId = this._fakeMount.getEntityId();
            }
        }

        PassengerController pc = this.controller.getPassengerController(viewer);

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
        this._hasPosition = this.config.isNode("position");
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
                this._fakeCameraMount = new VirtualEntity(this.controller);

                this._fakeCameraMount.setEntityType(EntityType.CHICKEN);
                this._fakeCameraMount.setPosition(new Vector(0.0, 1.0, 0.0));
                this._fakeCameraMount.setRelativeOffset(0.0, -1.32, 0.0);
                this._fakeCameraMount.setHasRotation(false);

                // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                this._fakeCameraMount.updatePosition(this.transform);
                this._fakeCameraMount.syncPosition(true);
                this._fakeCameraMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                this._fakeCameraMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                this._fakeCameraMount.spawn(viewer, calcMotion());

                this.controller.getPassengerController(viewer).mount(this._fakeCameraMount.getEntityId(), this._entity.getEntityId());
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

            PassengerController pc = this.controller.getPassengerController(viewer);
            pc.remove(this._entity.getEntityId(), false);
            if (this._fakeEntityId != -1) {
                pc.remove(this._fakeEntityId, false);
            }
        }
    }

    @Override
    public void onMove(boolean absolute) {
        if (this._entity instanceof Player && this._fakeEntityId != -1) {
            // When upside-down, we have a virtual camera for the player
            // This is controlled by having the player mounted on an invisible entity that we control
            // This entity must be updated here to have the correct position of the camera
            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(this.transform);
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
        if (this._fakeMount != null) {
            this._fakeMount.updatePosition(this.transform);
            this._fakeMount.syncPosition(absolute);
        }
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
            for (PassengerController pc : this.controller.getPassengerControllers()) {
                pc.unmount(this._parentMountId, this._entity.getEntityId());
            }
            if (this._fakeCameraMount != null && this._entity instanceof Player) {
                this.controller.getPassengerController((Player) this._entity).unmount(this._fakeCameraMount.getEntityId(), this._entity.getEntityId());
            }
            for (Player viewer : this.controller.getViewers()) {
                this.makeHidden(viewer);
            }
        }

        // Switch entity
        this._entity = entity;
        this._fakeEntityId = -1;
        this._hideRealPlayerNextTick = (entity instanceof Player);

        // Re-seat new entity
        if (this._entity != null) {
            for (Player viewer : this.controller.getViewers()) {
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
            for (Player viewer : this.controller.getViewers()) {
                this.makeHidden(viewer);
            }

            // Change it
            this._upsideDown = upsideDown;
            if (!this._upsideDown) {
                this._fakeEntityId = -1;
            }

            // Respawn the new player to all viewers with the upside-down state changed
            for (Player viewer : this.controller.getViewers()) {
                this.makeVisible(viewer);
            }
        } else {
            // Change it
            this._upsideDown = upsideDown;

            // Refresh metadata to reflect this change for the Entity
            // We achieve the upside-down state using nametags
            if (this._entity != null) {
                for (Player viewer : this.controller.getViewers()) {
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
        if (!(this._entity instanceof Player) || this._upsideDown || !this.controller.getViewers().contains(this._entity)) {
            this._useVirtualCamera = use;
            return;
        }

        // Not upside-down and the virtual camera must be activated or de-activated for the entity only
        this.makeHidden((Player) this._entity);
        this._useVirtualCamera = use;
        this.makeVisible((Player) this._entity);
    }

    @Override
    public void onTick() {
        float selfPitch = (float) this.transform.getYawPitchRoll().getX();

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
                for (Player viewer : this.controller.getViewers()) {
                    if (this._upsideDown || (this._useVirtualCamera && viewer == this._entity)) {
                        this.hideRealPlayer(viewer);
                    }
                }
            }
        }

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
                for (Player viewer : this.controller.getViewers()) {
                    PacketUtil.sendPacket(viewer, lookPacket);
                }
                this._fakeEntityLastYaw = protYaw;
                this._fakeEntityLastPitch = protPitch;
            }

            if (protHeadRot != this._fakeEntityLastHeadYaw) {
                CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this._fakeEntityId);
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
                for (Player viewer : this.controller.getViewers()) {
                    PacketUtil.sendPacket(viewer, headPacket);
                }
                this._fakeEntityLastHeadYaw = protHeadRot;
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
