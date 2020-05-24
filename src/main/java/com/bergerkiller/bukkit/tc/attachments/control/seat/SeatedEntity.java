package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

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

    public void makeVisible(Player viewer, int mountEntityId) {
        VehicleMountController vmh = PlayerUtil.getVehicleMountController(viewer);
        if (this._fake) {
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
            if (this._upsideDown) {
                ProfileNameModifier.UPSIDEDOWN.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId);
            } else {
                ProfileNameModifier.NO_NAMETAG.spawnPlayer(viewer, (Player) this._entity, this._fakeEntityId);
            }

            // Unmount from the original vehicle and mount the new fake entity instead
            vmh.unmount(mountEntityId, this._entity.getEntityId());
            vmh.mount(mountEntityId, this._fakeEntityId);
        } else {
            // Send metadata
            refreshMetadata(viewer);

            // Mount entity in vehicle
            vmh.mount(mountEntityId, this._entity.getEntityId());
        }
    }

    public void makeHidden(Player viewer) {
        if (this._fake) {
            // Despawn the fake player entity
            if (isPlayer()) {
                // Destroy old fake player entity
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(new int[] {this._fakeEntityId}));
                PlayerUtil.getVehicleMountController(viewer).remove(this._fakeEntityId);

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
        }

        // Resend the correct metadata for the entity/player
        if (!isEmpty()) {
            resetMetadata(viewer);
            PlayerUtil.getVehicleMountController(viewer).remove(this._entity.getEntityId());
        }
    }
}
