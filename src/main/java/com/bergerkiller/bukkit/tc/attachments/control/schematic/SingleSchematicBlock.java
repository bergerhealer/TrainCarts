package com.bergerkiller.bukkit.tc.attachments.control.schematic;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.UUID;

/**
 * A single non-air block that is being displayed
 */
class SingleSchematicBlock {
    private final double x, y, z;
    private double sx, sy, sz; // Scaled x/y/z
    private final Vector translation;
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metadata;

    public SingleSchematicBlock(double x, double y, double z, BlockData blockData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.sx = x;
        this.sy = y;
        this.sz = z;
        this.translation = new Vector(x, y, z);
        this.entityId = EntityUtil.getUniqueEntityId();
        this.entityUUID = UUID.randomUUID();
        this.metadata = new DataWatcher();

        metadata.watch(DisplayHandle.DATA_INTERPOLATION_DURATION, 3);
        metadata.watch(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
        metadata.watch(DisplayHandle.DATA_SCALE, new Vector(1, 1, 1));
        metadata.watch(DisplayHandle.DATA_TRANSLATION, new Vector());
        metadata.watch(DisplayHandle.DATA_LEFT_ROTATION, new Quaternion());
        metadata.watch(DisplayHandle.DATA_WIDTH, (float) 1.5F);
        metadata.watch(DisplayHandle.DATA_HEIGHT, (float) 1.5F);
        metadata.watch(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, blockData);
    }

    /**
     * Gets the Entity ID of this single display block entity
     *
     * @return Entity id
     */
    public int getEntityId() {
        return entityId;
    }

    /**
     * Refreshes the scale and spacing used when displaying this block
     *
     * @param scale New scale factor
     * @param origin Origin position relative to which the root armorstand is placed
     * @param spacing New spacing
     * @param bb Bounding box size (width and height)
     */
    public void setScaleAndSpacing(Vector scale, Vector origin, Vector spacing, Float bb) {
        sx = scale.getX() * (x + (spacing.getX() * (x + 0.5))) - origin.getX();
        sy = scale.getY() * (y + (spacing.getY() * y)) - origin.getY();
        sz = scale.getZ() * (z + (spacing.getZ() * (z + 0.5))) - origin.getZ();
        metadata.set(DisplayHandle.DATA_SCALE, scale);
        metadata.set(DisplayHandle.DATA_WIDTH, bb);
        metadata.set(DisplayHandle.DATA_HEIGHT, bb);
    }

    /**
     * Refreshes the scale used when displaying this block. Assumes zero spacing (common)
     *
     * @param scale New scale factor
     * @param origin Origin position relative to which the root armorstand is placed
     * @param bb Bounding box size (width and height)
     */
    public void setScaleZeroSpacing(Vector scale, Vector origin, Float bb) {
        sx = scale.getX() * x - origin.getX();
        sy = scale.getY() * y - origin.getY();
        sz = scale.getZ() * z - origin.getZ();
        metadata.set(DisplayHandle.DATA_SCALE, scale);
        metadata.set(DisplayHandle.DATA_WIDTH, bb);
        metadata.set(DisplayHandle.DATA_HEIGHT, bb);
    }

    /**
     * Updates the rotation and scale of this schematic block. Updates the relative
     * rotation and translation of this block around 0,0,0. Sends these updates
     * to all viewers specified, and future spawns will use it.
     *
     * @param rotation Rotation Quaternion
     * @param viewers Recipients of the metadata updates
     */
    public void sync(Quaternion rotation, Iterable<AttachmentViewer> viewers) {
        Vector translation = this.translation;
        MathUtil.setVector(translation, sx, sy, sz);
        rotation.transformPoint(translation);
        metadata.forceSet(DisplayHandle.DATA_TRANSLATION, translation);
        metadata.forceSet(DisplayHandle.DATA_LEFT_ROTATION, rotation);
        metadata.forceSet(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);

        Iterator<AttachmentViewer> iter = viewers.iterator();
        if (iter.hasNext()) {
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(entityId, metadata, false);
            do {
                iter.next().send(packet);
            } while (iter.hasNext());
        }
    }

    /**
     * Spawns this single block to the viewer
     *
     * @param viewer Attachment Viewer
     * @param position Absolute position of 0,0,0
     * @param motion Initial motion
     */
    public void spawn(AttachmentViewer viewer, Vector position, Vector motion) {
        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        spawnPacket.setEntityId(entityId);
        spawnPacket.setEntityUUID(entityUUID);
        spawnPacket.setEntityType(VirtualDisplayEntity.BLOCK_DISPLAY_ENTITY_TYPE);
        spawnPacket.setPosX(position.getX());
        spawnPacket.setPosY(position.getY());
        spawnPacket.setPosZ(position.getZ());
        spawnPacket.setMotX(motion.getX());
        spawnPacket.setMotY(motion.getY());
        spawnPacket.setMotZ(motion.getZ());
        spawnPacket.setYaw(0.0f);
        spawnPacket.setPitch(0.0f);
        viewer.send(spawnPacket);
        viewer.send(PacketPlayOutEntityMetadataHandle.createNew(entityId, metadata, true));
    }
}
