package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Uses 12 block display entities to create "lines" making up the bounding box
 */
public class VirtualDisplayBoundingBox extends VirtualBoundingBox {
    private final int mountEntityId;
    private final List<Line> lines = Arrays.asList(
            // Along X
            Line.transform(t -> t.applyPosition(0.0, 1.0, 1.0)
                                 .applyScaleX(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                                 .applyScaleX(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                                 .applyScaleX(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                                 .applyScaleX(1.0)),

            // Along Z
            Line.transform(t -> t.applyPosition(1.0, 1.0, 0.0)
                                 .applyScaleZ(1.0)),
            Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                                 .applyScaleZ(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                                 .applyScaleZ(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                                 .applyScaleZ(1.0)),

            // Along Y
            Line.transform(t -> t.applyPosition(1.0, 0.0, 1.0)
                                 .applyScaleY(1.0)),
            Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                                 .applyScaleY(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                                 .applyScaleY(1.0)),
            Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                                 .applyScaleY(1.0))
    );
    private final int[] lineEntityIds;
    private final List<UUID> lineEntityUUIDs;

    // Position info
    private final Vector position = new Vector();
    private final Vector size = new Vector();
    private final Quaternion rotation = new Quaternion();

    public VirtualDisplayBoundingBox(AttachmentManager manager) {
        super(manager);
        this.mountEntityId = EntityUtil.getUniqueEntityId();
        this.lineEntityIds = lines.stream().mapToInt(l -> l.entityId).toArray();
        this.lineEntityUUIDs = lines.stream().map(l -> l.entityUUID).collect(Collectors.toList());
    }

    @Override
    public void update(OrientedBoundingBox boundingBox) {
        MathUtil.setVector(position, boundingBox.getPosition());
        MathUtil.setVector(size, boundingBox.getSize());
        rotation.setTo(boundingBox.getOrientation());

        double minSize = 0.02 * Util.absMinAxis(size);
        double lineThickness = Math.min(0.3, minSize);
        for (Line line : lines) {
            LineTransformer transformer = new LineTransformer(line.metadata, lineThickness);
            line.transform.accept(transformer);
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        for (Line line : lines) {
            line.spawn(viewer, position, motion);
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
        viewer.send(PacketPlayOutMountHandle.createNew(mountEntityId, lineEntityIds));
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        int[] ids = Arrays.copyOf(lineEntityIds, lineEntityIds.length + 1);
        ids[ids.length - 1] = mountEntityId;
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(ids));
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        byte data = (color != null) ? (byte) EntityHandle.DATA_FLAG_GLOWING : (byte) 0;
        for (Line line : lines) {
            line.metadata.set(EntityHandle.DATA_FLAGS, data);
        }
    }

    @Override
    protected void applyGlowColorForViewer(AttachmentViewer viewer, ChatColor color) {
        viewer.updateGlowColor(lineEntityUUIDs, color);
    }

    @Override
    public void syncPosition(boolean absolute) {
        // Sync metadata of the display blocks
        for (Line line : lines) {
            broadcast(line.createMetaPacket(false));
        }

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

    private static class Line {
        private final Consumer<LineTransformer> transform;
        public final int entityId;
        public final UUID entityUUID;
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
                        MaterialUtil.getMaterial("BLACK_CONCRETE")))
                .create();

        public static Line transform(Consumer<LineTransformer> transform) {
            return new Line(transform);
        }

        private Line(Consumer<LineTransformer> transform) {
            this.transform = transform;
            entityId = EntityUtil.getUniqueEntityId();
            entityUUID = UUID.randomUUID();
            metadata = LINE_METADATA.create();
        }

        public PacketPlayOutEntityMetadataHandle createMetaPacket(boolean includeUnchangedData) {
            return PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, includeUnchangedData);
        }

        public void spawn(AttachmentViewer viewer, Vector position, Vector motion) {
            // Spawn the display entity itself
            {
                PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
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
        }
    }

    private class LineTransformer {
        public final DataWatcher metadata;
        public final double lineThickness;

        public LineTransformer(DataWatcher metadata, double lineThickness) {
            this.metadata = metadata;
            this.lineThickness = lineThickness;
        }

        /**
         * Generates the starting position of the line
         *
         * @param tx Position weight (X)
         * @param ty Position weight (Y)
         * @param tz Position weight (Z)
         * @return this
         */
        public LineTransformer applyPosition(double tx, double ty, double tz) {
            Vector v = new Vector((-0.5 + tx) * size.getX() - tx * lineThickness,
                                  (-0.5 + ty) * size.getY() - ty * lineThickness,
                                  (-0.5 + tz) * size.getZ() - tz * lineThickness);
            rotation.transformPoint(v);
            metadata.forceSet(DisplayHandle.DATA_LEFT_ROTATION, rotation);
            metadata.forceSet(DisplayHandle.DATA_TRANSLATION, v);
            metadata.forceSet(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
            return this;
        }

        public LineTransformer applyScaleX(double x) {
            metadata.forceSet(DisplayHandle.DATA_SCALE, new Vector(size.getX() * x, lineThickness, lineThickness));
            return this;
        }

        public LineTransformer applyScaleY(double y) {
            metadata.forceSet(DisplayHandle.DATA_SCALE, new Vector(lineThickness, size.getY() * y, lineThickness));
            return this;
        }

        public LineTransformer applyScaleZ(double z) {
            metadata.forceSet(DisplayHandle.DATA_SCALE, new Vector(lineThickness, lineThickness, size.getZ() * z));
            return this;
        }
    }
}
