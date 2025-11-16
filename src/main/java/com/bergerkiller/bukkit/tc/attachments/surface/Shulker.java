package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntityShulkerHandle;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.UUID;

/**
 * The information tracked for a single shulker entity. This is cached
 * in the ShulkerTracker to improve performance of creating / despawning
 * them, and this also avoids rapid use of the entity ids.<br>
 * <br>
 * A shulker instance can only be used for a single viewer. Position change
 * tracking is not tracked per player.
 */
final class Shulker {
    /** ShulkerTracker owner of this shulker */
    public final ShulkerTracker shulkerTracker;
    /** Entity ID used to spawn the mount of this shulker */
    public final int mountEntityId = EntityUtil.getUniqueEntityId();
    /** Entity ID used to spawn the shulker itself */
    public final int entityId = EntityUtil.getUniqueEntityId();
    /** The direction to push players during spawning if they are inside */
    public BlockFace pushDirection;
    /** Last-calculated position for this shulker */
    public double x, y, z;
    /** Last position synchronized to the viewer */
    public double sync_x, sync_y, sync_z;
    /** State to see if this shulker is picked */
    public boolean picked = false;
    /** Whether this shulker requires destroying (before spawning */
    private boolean pendingDestroy = false;
    /** Whether this shulker requires spawning (after destroying) */
    private boolean pendingSpawn = false;
    /** Whether this shulker requires a movement update */
    private boolean pendingMove = false;

    public Shulker(ShulkerTracker shulkerTracker) {
        this.shulkerTracker = shulkerTracker;
    }

    private static final DataWatcher SHULKER_MOUNT_METADATA = DataWatcher.Prototype.build()
            .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
            .setClientByteDefault(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, 0)
            .setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                            EntityArmorStandHandle.DATA_FLAG_IS_SMALL |
                            EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE)
            .setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE)
            .create().create();

    private static final EnumMap<BlockFace, DataWatcher> SHULKER_METADATA_BY_FACE = new EnumMap<>(BlockFace.class);
    static {
        DataWatcher.Prototype SHULKER_METADATA_PROTOTYPE = DataWatcher.Prototype.build()
                .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
                .setClientDefault(EntityShulkerHandle.DATA_FACE_DIRECTION, BlockFace.SOUTH)
                .setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING)
                //.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE)
                .create();

        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            DataWatcher dw = SHULKER_METADATA_PROTOTYPE.create();
            dw.set(EntityShulkerHandle.DATA_FACE_DIRECTION, face);
            SHULKER_METADATA_BY_FACE.put(face, dw);
        }
    }

    public void scheduleMovement() {
        if (!pendingSpawn) {
            if (!pendingMove) {
                pendingMove = true;
                shulkerTracker.shulkersToMove.add(this);
            }
        }
    }

    void scheduleSpawn() {
        if (!pendingSpawn) {
            pendingSpawn = true;
            shulkerTracker.shulkersToSpawn.add(this);
        }
    }

    void scheduleDestroy() {
        if (pendingSpawn) {
            pendingSpawn = false;
        } else if (!pendingDestroy) {
            pendingDestroy = true;
            shulkerTracker.shulkersToDestroy.add(this);
        }
        pendingMove = false;
    }

    /**
     * Clears the pending-move flag. If set, returns false. If not set, returns true.
     * For internal use in removeIf().
     *
     * @return True if actually not moved
     */
    boolean clearMove() {
        if (pendingMove) {
            pendingMove = false;
            return false;
        } else {
            return true;
        }
    }

    /**
     * Clears the pending-spawn flag. If set, returns false. If not set, returns true.
     * For internal use in removeIf().
     *
     * @return True if actually not spawned
     */
    boolean clearSpawn() {
        if (pendingSpawn) {
            pendingSpawn = false;
            return false;
        } else {
            return true;
        }
    }

    /**
     * Clears the pending-destroy flag. If set, returns false. If not set, returns true.
     * For internal use in removeIf().
     *
     * @return True if actually not destroyed
     */
    boolean clearDestroy() {
        if (pendingDestroy) {
            pendingDestroy = false;
            return false;
        } else {
            return true;
        }
    }

    public void syncPositionSilent() {
        this.sync_x = x;
        this.sync_y = y;
        this.sync_z = z;
    }

    public void syncPosition(AttachmentViewer viewer) {
        if (x != sync_x || y != sync_y || z != sync_z) {
            syncPositionSilent();

            PacketPlayOutEntityTeleportHandle p = PacketPlayOutEntityTeleportHandle.createNew(
                    this.mountEntityId, x, y - 0.5, z, 0.0f, 0.0f, false);
            viewer.send(p);
        }
    }

    public void spawn(AttachmentViewer viewer) {
        syncPositionSilent();

        // Spawn mount it is inside of
        {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(mountEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.ARMOR_STAND);
            spawnPacket.setPosX(x);
            spawnPacket.setPosY(y - 0.5);
            spawnPacket.setPosZ(z);
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setHeadYaw(0.0f);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, SHULKER_MOUNT_METADATA);
        }

        // Shulker metadata. We set the face in such a way that the shulker won't snap to blocks
        // behind it.
        DataWatcher shulkerMeta = SHULKER_METADATA_BY_FACE.get(pushDirection);
        if (shulkerMeta == null) {
            throw new IllegalStateException("Invalid push direction: " + pushDirection);
        }

        // Spawn shulker itself
        {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(entityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.SHULKER);
            spawnPacket.setPosX(x);
            spawnPacket.setPosY(y - 1.0);
            spawnPacket.setPosZ(z);
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setHeadYaw(0.0f);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, shulkerMeta);
        }

        // Mount shulker into mount
        viewer.getVehicleMountController().mount(mountEntityId, entityId);
    }
}
