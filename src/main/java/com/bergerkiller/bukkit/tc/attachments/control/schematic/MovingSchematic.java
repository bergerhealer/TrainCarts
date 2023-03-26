package com.bergerkiller.bukkit.tc.attachments.control.schematic;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
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
    private final Vector scale = new Vector(1.0, 1.0, 1.0);
    private final Vector spacing = new Vector(0.0, 0.0, 0.0);
    private final Vector spacingFactor = new Vector(1.0, 1.0, 1.0);
    private int[] cachedBlockEntityIds = null;
    private boolean isFirstSync = true;

    public MovingSchematic(AttachmentManager manager) {
        super(manager);
        this.mountEntityId = EntityUtil.getUniqueEntityId();
    }

    /**
     * Should be called up-front when initializing blocks: defines the factor with which
     * the spacing value is multiplied. Depends on the dimensions of the blocks.
     *
     * @param factor Spacing factor (default: 1 1 1)
     */
    public void setSpacingFactor(Vector factor) {
        MathUtil.setVector(spacingFactor, factor);
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
            if (!isFirstSync) {
                block.sync(liveRot, scale, spacing, Collections.emptyList());
                forAllViewers(v -> block.spawn(v, syncPos, new Vector(0.0, 0.0, 0.0)));
            }
        }
    }

    public void resendMounts() {
        if (!isFirstSync) {
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

    public void setScale(Vector3 scale) {
        if (this.scale.getX() != scale.x || this.scale.getY() != scale.y || this.scale.getZ() != scale.z) {
            MathUtil.setVector(this.scale, scale.x, scale.y, scale.z);
            syncAllBlocks();
        }
    }

    public void setScale(Vector scale) {
        if (this.scale.getX() != scale.getX() || this.scale.getY() != scale.getY() || this.scale.getZ() != scale.getZ()) {
            MathUtil.setVector(this.scale, scale);
            syncAllBlocks();
        }
    }

    public void setSpacing(Vector spacing) {
        if (this.spacing.getX() != spacing.getX() || this.spacing.getY() != spacing.getY() || this.spacing.getZ() != spacing.getZ()) {
            MathUtil.setVector(this.spacing, spacing);
            syncAllBlocks();
        }
    }

    private void syncAllBlocks() {
        for (SingleSchematicBlock block : blocks) {
            block.sync(liveRot, scale, spacing, getViewers());
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

        if (isFirstSync) {
            isFirstSync = false;
            MathUtil.setVector(syncPos, livePos);
            syncAllBlocks(); // Important for spawn() to work right
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        // Sync rotation and relative translations of all the block entities
        syncAllBlocks();

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
