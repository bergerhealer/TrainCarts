package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * The information tracked for a single shulker entity. This is cached
 * in the ShulkerCache to improve performance of creating / despawning
 * them, and this also avoids rapid use of the entity ids.<br>
 * <br>
 * A shulker instance can only be used for a single viewer. Position change
 * tracking is not tracked per player.
 */
final class Shulker {
    /** Entity ID used to spawn the mount of this shulker */
    public final int mountEntityId = EntityUtil.getUniqueEntityId();
    /** Entity ID used to spawn the shulker itself */
    public final int entityId = EntityUtil.getUniqueEntityId();
    /** Last-calculated position for this shulker */
    public double x, y, z;
    /** Last position synchronized to the viewer */
    public double sync_x, sync_y, sync_z;
    /** State to see if this shulker is picked */
    public boolean picked = false;
    /** Whether this shulker requires destroying (before spawning */
    public boolean pendingDestroy = false;
    /** Whether this shulker requires spawning (after destroying) */
    public boolean pendingSpawn = false;

    private static final DataWatcher SHULKER_MOUNT_METADATA = DataWatcher.Prototype.build()
            .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
            .setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE)
            .create().create();

    private static final DataWatcher SHULKER_METADATA = DataWatcher.Prototype.build()
            .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
            //.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE)
            .create().create();

    public void syncPositionSilent() {
        this.sync_x = x;
        this.sync_y = y;
        this.sync_z = z;
    }

    public void syncPosition(AttachmentViewer viewer) {
        if (x != sync_x || y != sync_y || z != sync_z) {
            syncPositionSilent();

            PacketPlayOutEntityTeleportHandle p = PacketPlayOutEntityTeleportHandle.createNew(
                    this.mountEntityId, x, y - 1.2, z + 0.1, 0.0f, 0.0f, false);
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
            spawnPacket.setEntityType(EntityType.CHICKEN);
            spawnPacket.setPosX(x);
            spawnPacket.setPosY(y -1.2);
            spawnPacket.setPosZ(z + 0.1);
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setHeadYaw(0.0f);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, SHULKER_MOUNT_METADATA);
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
            viewer.sendEntityLivingSpawnPacket(spawnPacket, SHULKER_METADATA);
        }

        // Mount shulker into mount
        viewer.getVehicleMountController().mount(mountEntityId, entityId);
    }
}
