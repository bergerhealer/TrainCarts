package com.bergerkiller.bukkit.tc.attachments.old;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

/**
 * An Entity sitting inside a cart seat. This can be a Player or a mob, or some other kind of Entity.
 * The seated entity acts as a clone in the case of upside-down players, and controls the entity itself otherwise.
 * Like the name, the seated entity does not move around, but has head/body rotation logic.
 * The actual seating logic (parenting with a vehicle) does not occur here, and should be handled
 * by the owner of this seated entity. The {@link #getEntityId(viewer)} should be used for that.
 */
public class SeatAttachment implements ICartAttachmentOld {
    private boolean _upsideDown = false;
    private boolean _useVirtualCamera = false;
    private boolean _hideRealPlayerNextTick = false;
    private Entity _entity = null;
    private int _fakeEntityId = -1;
    private int _fakeEntityLastYaw = 0;
    private int _fakeEntityLastPitch = 0;
    private int _fakeEntityLastHeadYaw = 0;
    private VirtualEntity _fakeCameraMount = null;
    private final Set<Player> _viewers = new HashSet<Player>();
    private CartAttachmentOwner owner;

    public SeatAttachment(CartAttachmentOwner owner) {
        this.owner = owner;
    }

    /**
     * Gets the Entity Id of the entity that is shown seated
     * 
     * @param viewer of the Entity
     * @return seated entity Id
     */
    public int getEntityId(Player viewer) {
        if ((this._fakeEntityId != -1) && (this._upsideDown || (this._useVirtualCamera && viewer == this._entity))) {
            return this._fakeEntityId;
        }
        if (this._entity != null) {
            return this._entity.getEntityId();
        } else {
            return -1;
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
            for (Player viewer : this._viewers) {
                this.makeHidden(viewer);
            }
        }

        // Switch entity
        this._entity = entity;
        this._fakeEntityId = -1;
        this._hideRealPlayerNextTick = (entity instanceof Player);

        // Re-seat new entity
        if (this._entity != null) {
            for (Player viewer : this._viewers) {
                this.makeVisible(viewer);
            }
        }
        this.owner.onAttachmentsChanged();
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
            for (Player viewer : this._viewers) {
                this.makeHidden(viewer);
            }

            // Change it
            this._upsideDown = upsideDown;
            if (!this._upsideDown) {
                this._fakeEntityId = -1;
            }

            // Respawn the new player to all viewers with the upside-down state changed
            for (Player viewer : this._viewers) {
                this.makeVisible(viewer);
            }

            // Needs re-seating
            this.owner.onAttachmentsChanged();
        } else {
            // Change it
            this._upsideDown = upsideDown;

            // Refresh metadata to reflect this change for the Entity
            // We achieve the upside-down state using nametags
            if (this._entity != null) {
                for (Player viewer : this._viewers) {
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
        if (!(this._entity instanceof Player) || this._upsideDown || !this._viewers.contains(this._entity)) {
            this._useVirtualCamera = use;
            return;
        }

        // Not upside-down and the virtual camera must be activated or de-activated for the entity only
        this.makeHidden((Player) this._entity);
        this._useVirtualCamera = use;
        this._fakeEntityId = -1;
        this.makeVisible((Player) this._entity);

        // Needs re-seating
        this.owner.onAttachmentsChanged();
    }

    @Override
    public boolean addViewer(Player viewer) {
        if (!this._viewers.add(viewer)) {
            return false;
        }

        if (this._entity != null) {
            this.makeVisible(viewer);
        }
        return true;
    }

    @Override
    public boolean removeViewer(Player viewer) {
        if (!this._viewers.remove(viewer)) {
            return false;
        }

        makeHidden(viewer);
        return true;
    }

    @Override
    public void onTick() {
        // Hides the real player a second time the next tick after the entity in the seat was switched
        // This patches some glitches when a player switches between minecarts (ejects & enters in 1 tick)
        // Hiding is only done when the player is actually invisible
        if (this._hideRealPlayerNextTick) {
            this._hideRealPlayerNextTick = false;
            if (this._entity != null) {
                for (Player viewer : this._viewers) {
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
                for (Player viewer : this._viewers) {
                    PacketUtil.sendPacket(viewer, lookPacket);
                }
                this._fakeEntityLastYaw = protYaw;
                this._fakeEntityLastPitch = protPitch;
            }

            if (protHeadRot != this._fakeEntityLastHeadYaw) {
                CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this._fakeEntityId);
                headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
                for (Player viewer : this._viewers) {
                    PacketUtil.sendPacket(viewer, headPacket);
                }
                this._fakeEntityLastHeadYaw = protHeadRot;
            }


        }
    }

    @Override
    public void onSyncAtt(boolean absolute) {
        if (this._entity instanceof Player && this._fakeEntityId != -1) {
            // When upside-down, we have a virtual camera for the player
            // This is controlled by having the player mounted on an invisible entity that we control
            // This entity must be updated here to have the correct position of the camera
            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(this.owner.getTransform(true));
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
    }

    // called when the seated entity is made invisible. Resets the entity to its unseated defaults.
    private void makeHidden(Player viewer) {
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

        // reset the metadata
        if (this._entity != null) {
            this.refreshMetadata(viewer, true);
        }
    }

    // called when the seated entity is made visible. Applies properties as required
    private void makeVisible(Player viewer) {
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
                this._fakeCameraMount = new VirtualEntity(null); // Broke!

                this._fakeCameraMount.setPosition(new Vector(0.0, 1.0, 0.0));
                this._fakeCameraMount.setRelativeOffset(0.0, -1.32, 0.0);

                // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                this._fakeCameraMount.updatePosition(owner.getTransform(false));
                this._fakeCameraMount.syncPosition(true);
                this._fakeCameraMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                this._fakeCameraMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                //this._fakeCameraMount.setPassengers(new int[] {this._entity.getEntityId()});
                this._fakeCameraMount.spawn(viewer, owner.getLastMovement());
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
