package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Displays a train coupler using display entities.
 * A single block is stretched to show off the length.
 */
public class VirtualDisplayTrainCoupler extends VirtualTrainCoupler {
    private static final double COUPLER_DIAMETER = 0.2;

    private Vector position;
    private final int mountEntityId;
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metadata;

    private static final DataWatcher.Prototype LINE_METADATA = DataWatcher.Prototype.build()
            .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
            .setClientDefault(DisplayHandle.DATA_TRANSLATION, new Vector())
            .setClientDefault(DisplayHandle.DATA_LEFT_ROTATION, new Quaternion())
            .setClientDefault(DisplayHandle.DATA_SCALE, new Vector(1, 1, 1))
            .setClientDefault(DisplayHandle.DATA_INTERPOLATION_DURATION, 0)
            .set(DisplayHandle.DATA_INTERPOLATION_DURATION, 3)
            .setClientDefault(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0)
            .setClientDefault(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, BlockData.AIR)
            .set(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, BlockData.fromMaterial(
                    MaterialUtil.getMaterial("LIGHT_GRAY_CONCRETE")))
            .create();

    public VirtualDisplayTrainCoupler(AttachmentManager manager) {
        super(manager);
        this.mountEntityId = EntityUtil.getUniqueEntityId();
        this.entityId = EntityUtil.getUniqueEntityId();
        this.entityUUID = UUID.randomUUID();
        this.metadata = LINE_METADATA.create();
    }

    @Override
    public void update(Matrix4x4 transform, double length) {
        this.position = transform.toVector();

        Vector v = new Vector(-0.5 * COUPLER_DIAMETER, 0.0, 0.0);
        transform.getRotation().transformPoint(v);

        this.metadata.forceSet(DisplayHandle.DATA_LEFT_ROTATION, transform.getRotation());
        this.metadata.forceSet(DisplayHandle.DATA_TRANSLATION, v);
        this.metadata.forceSet(DisplayHandle.DATA_SCALE, new Vector(COUPLER_DIAMETER, COUPLER_DIAMETER, length));
        this.metadata.forceSet(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        throw new UnsupportedOperationException("Must specify a transform with length");
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        byte data = (color != null) ? (byte) EntityHandle.DATA_FLAG_GLOWING : (byte) 0;
        metadata.set(EntityHandle.DATA_FLAGS, data);
    }

    @Override
    protected void applyGlowColorForViewer(AttachmentViewer viewer, ChatColor color) {
        viewer.updateGlowColor(entityUUID, color);
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        // Spawn the display entity itself
        {
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(this.entityUUID);
            spawnPacket.setEntityType(VirtualDisplayEntity.BLOCK_DISPLAY_ENTITY_TYPE);
            spawnPacket.setPosX(position.getX() - motion.getX());
            spawnPacket.setPosY(position.getY() - motion.getY());
            spawnPacket.setPosZ(position.getZ() - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            viewer.send(spawnPacket);
            viewer.send(createMetaPacket(true));
        }

        // Spawn invisible marker armorstand mount
        {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(mountEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.ARMOR_STAND);
            spawnPacket.setPosX(position.getX() - motion.getX());
            spawnPacket.setPosY(position.getY() - motion.getY());
            spawnPacket.setPosZ(position.getZ() - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setHeadYaw(0.0f);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, VirtualDisplayEntity.ARMORSTAND_MOUNT_METADATA);
        }

        // Mount all line blocks into the armorstand
        viewer.send(PacketPlayOutMountHandle.createNew(mountEntityId, new int[] {entityId}));
    }

    private PacketPlayOutEntityMetadataHandle createMetaPacket(boolean includeUnchangedData) {
        return PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, includeUnchangedData);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(new int[] { mountEntityId, entityId }));
    }

    @Override
    public void syncPosition(boolean absolute) {
        // Sync metadata of the display blocks
        broadcast(createMetaPacket(false));

        // Just sync absolute all the time, this isn't used often enough for it to warrant a lot of code
        // int entityId, double posX, double posY, double posZ, float yaw, float pitch, boolean onGround)
        broadcast(PacketPlayOutEntityTeleportHandle.createNew(mountEntityId,
                position.getX(), position.getY(), position.getZ(),
                0.0f, 0.0f, false));
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return entityId == mountEntityId;
    }
}
