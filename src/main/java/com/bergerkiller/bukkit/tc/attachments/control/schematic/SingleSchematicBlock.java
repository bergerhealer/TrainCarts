package com.bergerkiller.bukkit.tc.attachments.control.schematic;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
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
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metadata;

    public SingleSchematicBlock(double x, double y, double z, BlockData blockData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = EntityUtil.getUniqueEntityId();
        this.entityUUID = UUID.randomUUID();
        this.metadata = new DataWatcher();

        metadata.watch(DisplayHandle.DATA_INTERPOLATION_DURATION, 3);
        metadata.watch(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
        metadata.watch(DisplayHandle.DATA_SCALE, new Vector(1, 1, 1));
        metadata.watch(DisplayHandle.DATA_TRANSLATION, new Vector());
        metadata.watch(DisplayHandle.DATA_LEFT_ROTATION, new Quaternion());
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
     * Updates the rotation and scale of this schematic block. Updates the relative
     * rotation and translation of this block around 0,0,0. Sends these updates
     * to all viewers specified, and future spawns will use it.
     *
     * @param rotation Rotation Quaternion
     * @param scale Scale of the overall structure
     * @param spacing Block spacing between all individual blocks (0 for none)
     * @param viewers Recipients of the metadata updates
     */
    public void sync(Quaternion rotation, Vector scale, Vector spacing, Iterable<AttachmentViewer> viewers) {
        Vector translation = new Vector(
                (scale.getX() * (x - 0.5)) + (spacing.getX() * x),
                (scale.getY() * y) + (spacing.getY() * y),
                (scale.getZ() * (z - 0.5)) + (spacing.getZ() * z));
        rotation.transformPoint(translation);

        metadata.set(DisplayHandle.DATA_SCALE, scale);
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
