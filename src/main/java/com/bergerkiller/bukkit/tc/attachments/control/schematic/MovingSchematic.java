package com.bergerkiller.bukkit.tc.attachments.control.schematic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Moves a cuboid of blocks from a schematic around using a single armorstand as a base
 * entity for the smooth interpolation.
 */
public class MovingSchematic extends VirtualSpawnableObject {
    private final int mountEntityId;
    private final List<SingleSchematicBlock> blocks = new ArrayList<>();
    private final Vector livePos = new Vector();
    private final Vector syncPos = new Vector();
    private final Quaternion liveRot = new Quaternion();
    private IntVector3 blockBounds = new IntVector3(1, 1, 1);
    private final Vector scale = new Vector(1.0, 1.0, 1.0);
    private final Vector origin = new Vector(0.0, 0.0, 0.0);
    private final Vector spacing = new Vector(0.0, 0.0, 0.0);
    private boolean hasSpacing = false;
    private boolean hasOrigin = false;
    private boolean hasClipping = true;
    private float bbSize = 1.5F; // Default value
    private int[] cachedBlockEntityIds = null;
    private boolean hasKnownPosition = false;

    public MovingSchematic(AttachmentManager manager) {
        super(manager);
        this.mountEntityId = EntityUtil.getUniqueEntityId();
    }

    /**
     * Sets the total block dimensions of this moving schematic. This, together with
     * scale, is used to figure out the size of the clipping bounding box.
     *
     * @param blockBounds Block bounds of the schematic
     */
    public void setBlockBounds(IntVector3 blockBounds) {
        if (!this.blockBounds.equals(blockBounds)) {
            this.blockBounds = blockBounds;
            rescaleAllBlocks();
        }
    }

    /**
     * Adds a single block to be displayed. If the BlockData is AIR, does not add
     * the block.
     *
     * @param x X-coordinate relative to the origin
     * @param y Y-coordinate relative to the origin
     * @param z Z-coordinate relative to the origin
     * @param blockData BlockData of the block
     */
    public void addBlock(double x, double y, double z, BlockData blockData) {
        if (!MaterialUtil.ISAIR.get(blockData)) {
            SingleSchematicBlock block = new SingleSchematicBlock(x, y, z, blockData);
            this.blocks.add(block);
            this.cachedBlockEntityIds = null;
            if (hasKnownPosition) {
                if (hasSpacing) {
                    block.setScaleAndSpacing(scale, origin, spacing);
                } else {
                    block.setScaleZeroSpacing(scale, origin);
                }
                block.setClipBox(bbSize);
                block.sync(liveRot, Collections.emptyList());
                forAllViewers(v -> block.spawn(v, syncPos, new Vector(0.0, 0.0, 0.0)));
            }
        }
    }

    /**
     * Creates an oriented bounding box based on the position, orientation and scale of this
     * schematic.
     *
     * @return BBOX
     */
    public OrientedBoundingBox createBBOX() {
        Vector bbSize = new Vector((blockBounds.x + spacing.getX() * (blockBounds.x - 1)) * scale.getX(),
                                   (blockBounds.y + spacing.getY() * (blockBounds.y - 1)) * scale.getY(),
                                   (blockBounds.z + spacing.getZ() * (blockBounds.z - 1)) * scale.getZ());
        Vector position = livePos.clone().add(liveRot.upVector().multiply(0.5 * bbSize.getY()));
        if (hasOrigin) {
            Vector offset = origin.clone();
            liveRot.transformPoint(offset);
            position.subtract(offset);
        }
        return new OrientedBoundingBox(position, bbSize, liveRot);
    }

    /**
     * Gets a transformation matrix of the exact position of the origin point
     *
     * @return Origin point transform
     */
    public Matrix4x4 createOriginPointTransform() {
        Matrix4x4 transform = Matrix4x4.translation(livePos);
        transform.rotate(liveRot);
        transform.translate(origin);
        return transform;
    }

    public void resendMounts() {
        if (hasKnownPosition) {
            broadcast(PacketPlayOutMountHandle.createNew(mountEntityId, getBlockEntityIds()));
        }
    }

    private int[] getBlockEntityIds() {
        int[] ids = cachedBlockEntityIds;
        if (ids == null) {
            cachedBlockEntityIds = ids = blocks.stream().mapToInt(
                    SingleSchematicBlock::getEntityId).toArray();
        }
        return ids;
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return entityId == mountEntityId; // Others can't be interacted with, so ignore those
    }

    public void setHasClipping(boolean clipping) {
        if (this.hasClipping != clipping) {
            this.hasClipping = clipping;
            rescaleAllBlocks();
        }
    }

    public void setScale(Vector3 scale) {
        if (this.scale.getX() != scale.x || this.scale.getY() != scale.y || this.scale.getZ() != scale.z) {
            MathUtil.setVector(this.scale, scale.x, scale.y, scale.z);
            rescaleAllBlocks();
        }
    }

    public void setScale(Vector scale) {
        if (this.scale.getX() != scale.getX() || this.scale.getY() != scale.getY() || this.scale.getZ() != scale.getZ()) {
            MathUtil.setVector(this.scale, scale);
            rescaleAllBlocks();
        }
    }

    public void setSpacing(Vector spacing) {
        if (this.spacing.getX() != spacing.getX() || this.spacing.getY() != spacing.getY() || this.spacing.getZ() != spacing.getZ()) {
            MathUtil.setVector(this.spacing, spacing);
            hasSpacing = (spacing.getX() != 0.0 || spacing.getY() != 0.0 || spacing.getZ() != 0.0);
            rescaleAllBlocks();
        }
    }

    public void setOrigin(Vector origin) {
        if (this.origin.getX() != origin.getX() || this.origin.getY() != origin.getY() || this.origin.getZ() != origin.getZ()) {
            MathUtil.setVector(this.origin, origin);
            hasOrigin = (origin.getX() != 0.0 || origin.getY() != 0.0 || origin.getZ() != 0.0);
            rescaleAllBlocks();
        }
    }

    public boolean hasOrigin() {
        return hasOrigin;
    }

    public Vector getOrigin() {
        return origin;
    }

    private void rescaleAllBlocks() {
        float newBBSize = this.hasClipping
                ? (float) (VirtualDisplayEntity.BBOX_FACT * Util.absMaxAxis(blockBounds.toVector().add(spacing).multiply(scale)))
                : 0.0f;

        boolean clipChanged = (this.bbSize != newBBSize);
        this.bbSize = newBBSize;

        if (hasKnownPosition) {
            Float bbSize = Float.valueOf(this.bbSize);
            if (hasSpacing) {
                for (SingleSchematicBlock block : blocks) {
                    block.setScaleAndSpacing(scale, origin, spacing);
                    if (clipChanged) {
                        block.setClipBox(bbSize);
                    }
                    block.sync(liveRot, getViewers());
                }
            } else {
                for (SingleSchematicBlock block : blocks) {
                    block.setScaleZeroSpacing(scale, origin);
                    if (clipChanged) {
                        block.setClipBox(bbSize);
                    }
                    block.sync(liveRot, getViewers());
                }
            }
        }
    }

    private void syncBlockPositions() {
        if (hasKnownPosition) {
            for (SingleSchematicBlock block : blocks) {
                block.sync(liveRot, getViewers());
            }
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        // Spawn invisible marker armorstand mount that all display entities are mounted to
        {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.mountEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.ARMOR_STAND);
            spawnPacket.setPosX(this.syncPos.getX() - motion.getX());
            spawnPacket.setPosY(this.syncPos.getY() - motion.getY());
            spawnPacket.setPosZ(this.syncPos.getZ() - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setHeadYaw(0.0f);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, VirtualDisplayEntity.ARMORSTAND_MOUNT_METADATA);
        }

        // Spawn all individual blocks
        for (SingleSchematicBlock block : blocks) {
            block.spawn(viewer, syncPos, motion);
        }

        // Mount all individual blocks into the single armorstand
        viewer.send(PacketPlayOutMountHandle.createNew(mountEntityId, getBlockEntityIds()));
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        int[] ids = getBlockEntityIds();
        ids = Arrays.copyOf(ids, ids.length + 1);
        ids[ids.length - 1] = mountEntityId;
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(ids));
    }

    public void updatePosition(Matrix4x4 transform) {
        MathUtil.setVector(livePos, transform.toVector());
        liveRot.setTo(transform.getRotation());

        if (hasOrigin) {
            Vector offset = origin.clone();
            liveRot.transformPoint(offset);
            livePos.add(offset);
        }

        if (!hasKnownPosition) {
            hasKnownPosition = true;
            MathUtil.setVector(syncPos, livePos);
            rescaleAllBlocks();
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        // Sync rotation and relative translations of all the block entities
        syncBlockPositions();

        // Move the armorstand itself
        {
            // Force an absolute teleport if the change is too big
            double dx = 0.0, dy = 0.0, dz = 0.0;
            if (!absolute) {
                dx = (livePos.getX() - syncPos.getX());
                dy = (livePos.getY() - syncPos.getY());
                dz = (livePos.getZ() - syncPos.getZ());
                double abs_delta = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
                absolute = (abs_delta > EntityNetworkController.MAX_RELATIVE_DISTANCE);
            }

            if (absolute) {
                // Teleport the entity
                MathUtil.setVector(syncPos, livePos);
                broadcast(PacketPlayOutEntityTeleportHandle.createNew(mountEntityId,
                        syncPos.getX(), syncPos.getY(), syncPos.getZ(),
                        0.0f, 0.0f, false));
            } else {
                // Perform a relative movement update
                PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle packet = PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle.createNew(
                        mountEntityId,
                        dx, dy, dz,
                        false);

                MathUtil.addToVector(syncPos, packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
                broadcast(packet);
            }
        }
    }
}
