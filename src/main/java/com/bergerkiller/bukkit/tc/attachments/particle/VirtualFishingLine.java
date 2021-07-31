package com.bergerkiller.bukkit.tc.attachments.particle;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.projectile.EntityFishingHookHandle;

/**
 * Tracks and updates a single fishing line connecting two points
 */
public class VirtualFishingLine {
    /** Offset to position the player so that the held fishing rod line is exactly at 0/0/0 */
    private static final Vector OFFSET_PLAYER = new Vector(-0.35, -1.17, -0.8);
    /** Offset to position the silverfish mount of the player to align at 0/0/0 */
    private static final Vector OFFSET_HOLDER = new Vector(-0.35, -1.04, -0.8);
    /** Offset to position the hooked silverfish entity to align hook-line at 0/0/0 */
    private static final Vector OFFSET_HOOKED = new Vector(0.0, - 0.49, 0.0);
    private final int hookedEntityId, holderEntityId, holderPlayerEntityId, hookEntityId;

    public VirtualFishingLine() {
        this(false);
    }

    public VirtualFishingLine(boolean useViewerAsHolder) {
        this.hookedEntityId = EntityUtil.getUniqueEntityId();
        this.holderEntityId = useViewerAsHolder ? -1 : EntityUtil.getUniqueEntityId();
        this.holderPlayerEntityId = useViewerAsHolder ? -1 : EntityUtil.getUniqueEntityId();
        this.hookEntityId = EntityUtil.getUniqueEntityId();
    }

    /**
     * Spawns the fishing line
     *
     * @param viewer Viewing player to spawn the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void spawn(Player viewer, Vector positionA, Vector positionB) {
        // Spawn the invisible entity that holds the other end of the fishing hook
        // Seems that this must be a player entity, so just spawn clones of the viewer
        if (this.holderPlayerEntityId != -1) {
            FakePlayerSpawner.NO_NAMETAG_RANDOM.spawnPlayerSimple(viewer, viewer, this.holderPlayerEntityId, spawnPacket -> {
                spawnPacket.setPosX(positionA.getX() + OFFSET_PLAYER.getX());
                spawnPacket.setPosY(positionA.getY() + OFFSET_PLAYER.getY());
                spawnPacket.setPosZ(positionA.getZ() + OFFSET_PLAYER.getZ());
            }, meta -> {
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            });
        }

        // This is a vehicle the fake player entity sits in. We must put the player in
        // a vehicle, otherwise the player ends up rotating when moved.
        if (this.holderEntityId != -1) {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.holderEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.SILVERFISH);
            spawnPacket.setPosX(positionA.getX() + OFFSET_HOLDER.getX());
            spawnPacket.setPosY(positionA.getY() + OFFSET_HOLDER.getY());
            spawnPacket.setPosZ(positionA.getZ() + OFFSET_HOLDER.getZ());

            DataWatcher meta = new DataWatcher();
            meta.set(EntityHandle.DATA_NO_GRAVITY, true);
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, meta);

            PacketUtil.sendPacket(viewer, PacketPlayOutMountHandle.createNew(
                    this.holderEntityId, new int[] { this.holderPlayerEntityId }));
        }

        // Spawn the invisible entity that is hooked by a fishing hook
        {
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.hookedEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.SILVERFISH);
            spawnPacket.setPosX(positionB.getX() + OFFSET_HOOKED.getX());
            spawnPacket.setPosY(positionB.getY() + OFFSET_HOOKED.getY());
            spawnPacket.setPosZ(positionB.getZ() + OFFSET_HOOKED.getZ());

            DataWatcher meta = new DataWatcher();
            meta.set(EntityHandle.DATA_NO_GRAVITY, true);
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, meta);
        }

        // Spawn the fishing hook connecting the two
        {
            // Spawn packet
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
            spawnPacket.setEntityId(this.hookEntityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.FISHING_HOOK);
            spawnPacket.setPosX(positionB.getX());
            spawnPacket.setPosY(positionB.getY());
            spawnPacket.setPosZ(positionB.getZ());
            spawnPacket.setExtraData((this.holderPlayerEntityId == -1) ? viewer.getEntityId() : this.holderPlayerEntityId);
            PacketUtil.sendPacket(viewer, spawnPacket);

            // Metadata packet
            DataWatcher meta = new DataWatcher();
            meta.set(EntityFishingHookHandle.DATA_HOOKED_ENTITY_ID, OptionalInt.of(this.hookedEntityId));
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.hookEntityId, meta, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        }
    }

    /**
     * Refreshes the positions of the start and end point of the virtual line
     *
     * @param viewers Viewing players to update the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void update(Iterable<Player> viewers, Vector positionA, Vector positionB) {
        // Teleport holder entity
        if (this.holderEntityId != -1) {
            final PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                    this.holderEntityId,
                    positionA.getX() + OFFSET_HOLDER.getX(),
                    positionA.getY() + OFFSET_HOLDER.getY(),
                    positionA.getZ() + OFFSET_HOLDER.getZ(),
                    0.0f, 0.0f, false);
            viewers.forEach(p -> PacketUtil.sendPacket(p, packet));
        }

        // Teleport hooked entity
        {
            final PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                    this.hookedEntityId,
                    positionB.getX() + OFFSET_HOOKED.getX(),
                    positionB.getY() + OFFSET_HOOKED.getY(),
                    positionB.getZ() + OFFSET_HOOKED.getZ(),
                    0.0f, 0.0f, false);
            viewers.forEach(p -> PacketUtil.sendPacket(p, packet));
        }
    }

    /**
     * Destroys the fishing line, if spawned
     *
     * @param viewer
     */
    public void destroy(Player viewer) {
        int[] entityIds = IntStream.of(this.hookedEntityId, this.holderEntityId,
                                       this.holderPlayerEntityId, this.hookEntityId)
                .filter(id -> id != -1)
                .toArray();
        if (PacketPlayOutEntityDestroyHandle.canDestroyMultiple()) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewMultiple(entityIds));
        } else {
            for (int entityId : entityIds) {
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(entityId));
            }
        }
    }
}
