package com.bergerkiller.bukkit.tc.attachments.particle;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.projectile.EntityFishingHookHandle;

/**
 * Tracks and updates a single fishing line connecting two points
 */
public class VirtualFishingLine {
    private static final Offsets OFFSETS_1_8 = new Offsets(
            /** Offset to position the player so that the held fishing rod line is exactly at 0/0/0 */
            new Vector(0.35, -1.17, -0.8),
            /** Offset to position the silverfish mount of the player to align at 0/0/0 */
            new Vector(0.35, -1.04, -0.8),
            /** Offset to position the hooked silverfish entity to align hook-line at 0/0/0 */
            new Vector(0.0, -0.49, 0.0));
    private static final Offsets OFFSETS_1_11 = new Offsets(
            /** Offset to position the player so that the held fishing rod line is exactly at 0/0/0 */
            new Vector(-0.35, -1.17, -0.8),
            /** Offset to position the silverfish mount of the player to align at 0/0/0 */
            new Vector(-0.35, -1.04, -0.8),
            /** Offset to position the hooked silverfish entity to align hook-line at 0/0/0 */
            new Vector(0.0, -0.49, 0.0));

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

    private Offsets offsets(AttachmentViewer viewer) {
        if (viewer.evaluateGameVersion(">=", "1.11")) {
            return OFFSETS_1_11;
        } else {
            return OFFSETS_1_8;
        }
    }

    /**
     * Spawns the fishing line
     *
     * @param viewer Viewing player to spawn the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void spawn(Player viewer, Vector positionA, Vector positionB) {
        spawn(AttachmentViewer.fallback(viewer), positionA, positionB);
    }

    /**
     * Spawns the fishing line
     *
     * @param viewer Viewing player to spawn the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void spawn(AttachmentViewer viewer, Vector positionA, Vector positionB) {
        // Spawn entities that wield the fishing rod
        spawnWithoutLine(viewer, positionA, positionB);
        // Spawn the fishing hook connecting the two
        spawnLine(viewer, positionA, positionB);
    }

    /**
     * Spawns the fishing line
     *
     * @param viewer Viewing player to spawn the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void spawnWithoutLine(AttachmentViewer viewer, Vector positionA, Vector positionB) {
        ArrayList<UUID> uuids = new ArrayList<>(3);
        spawnWithoutLineCollectUUIDs(viewer, positionA, positionB, uuids);
        viewer.sendDisableCollision(uuids);
    }

    void spawnWithoutLineCollectUUIDs(AttachmentViewer viewer, Vector positionA, Vector positionB, List<UUID> uuids) {
        final Offsets OFFSET = offsets(viewer);

        // Spawn the invisible entity that holds the other end of the fishing hook
        // Seems that this must be a player entity, so just spawn clones of the viewer
        if (this.holderPlayerEntityId != -1) {
            FakePlayerSpawner.NO_NAMETAG_RANDOM.spawnPlayerSimple(viewer, viewer.getPlayer(), this.holderPlayerEntityId, spawnPacket -> {
                spawnPacket.setPosX(positionA.getX() + OFFSET.PLAYER.getX());
                spawnPacket.setPosY(positionA.getY() + OFFSET.PLAYER.getY());
                spawnPacket.setPosZ(positionA.getZ() + OFFSET.PLAYER.getZ());
            }, meta -> {
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            });
        }

        // This is a vehicle the fake player entity sits in. We must put the player in
        // a vehicle, otherwise the player ends up rotating when moved.
        if (this.holderEntityId != -1) {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);

            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.holderEntityId);
            spawnPacket.setEntityUUID(uuid);
            spawnPacket.setEntityType(EntityType.SILVERFISH);
            spawnPacket.setPosX(positionA.getX() + OFFSET.HOLDER.getX());
            spawnPacket.setPosY(positionA.getY() + OFFSET.HOLDER.getY());
            spawnPacket.setPosZ(positionA.getZ() + OFFSET.HOLDER.getZ());

            DataWatcher meta = new DataWatcher();
            meta.set(EntityHandle.DATA_NO_GRAVITY, true);
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, meta);

            viewer.getVehicleMountController().mount(this.holderEntityId, this.holderPlayerEntityId);
        }

        // Spawn the invisible entity that is hooked by a fishing hook
        {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);

            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.hookedEntityId);
            spawnPacket.setEntityUUID(uuid);
            spawnPacket.setEntityType(EntityType.SILVERFISH);
            spawnPacket.setPosX(positionB.getX() + OFFSET.HOOKED.getX());
            spawnPacket.setPosY(positionB.getY() + OFFSET.HOOKED.getY());
            spawnPacket.setPosZ(positionB.getZ() + OFFSET.HOOKED.getZ());

            DataWatcher meta = new DataWatcher();
            meta.set(EntityHandle.DATA_NO_GRAVITY, true);
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, meta);
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
        updateViewers(AttachmentViewer.fallbackIterable(viewers), positionA, positionB);
    }

    /**
     * Refreshes the positions of the start and end point of the virtual line
     *
     * @param viewers Viewing players to update the fishing line for
     * @param positionA Start position of the line
     * @param positionB End position of the line, a dobber is displayed here
     */
    public void updateViewers(Iterable<AttachmentViewer> viewers, Vector positionA, Vector positionB) {
        for (AttachmentViewer viewer : viewers) {
            final Offsets OFFSET = offsets(viewer);

            // Teleport holder entity
            if (this.holderEntityId != -1) {
                final PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                        this.holderEntityId,
                        positionA.getX() + OFFSET.HOLDER.getX(),
                        positionA.getY() + OFFSET.HOLDER.getY(),
                        positionA.getZ() + OFFSET.HOLDER.getZ(),
                        0.0f, 0.0f, false);
                viewer.send(packet);
            }

            // Teleport hooked entity
            {
                final PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                        this.hookedEntityId,
                        positionB.getX() + OFFSET.HOOKED.getX(),
                        positionB.getY() + OFFSET.HOOKED.getY(),
                        positionB.getZ() + OFFSET.HOOKED.getZ(),
                        0.0f, 0.0f, false);
                viewer.send(packet);
            }
        }
    }

    /**
     * Destroys the fishing line, if spawned
     *
     * @param viewer
     */
    public void destroy(Player viewer) {
        destroy(AttachmentViewer.fallback(viewer));
    }

    /**
     * Destroys the fishing line, if spawned
     *
     * @param viewer
     */
    public void destroy(AttachmentViewer viewer) {
        int[] entityIds = IntStream.of(this.hookedEntityId, this.holderEntityId,
                                       this.holderPlayerEntityId, this.hookEntityId)
                .filter(id -> id != -1)
                .toArray();
        if (PacketPlayOutEntityDestroyHandle.canDestroyMultiple()) {
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(entityIds));
        } else {
            for (int entityId : entityIds) {
                viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(entityId));
            }
        }
    }

    public void spawnLine(AttachmentViewer viewer, Vector positionA, Vector positionB) {
        // Spawn packet
        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
        spawnPacket.setEntityId(this.hookEntityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.FISHING_HOOK);
        spawnPacket.setPosX(positionB.getX());
        spawnPacket.setPosY(positionB.getY() - 0.25);
        spawnPacket.setPosZ(positionB.getZ());
        spawnPacket.setExtraData((this.holderPlayerEntityId == -1) ? viewer.getEntityId() : this.holderPlayerEntityId);
        viewer.send(spawnPacket);

        // Metadata packet
        DataWatcher meta = new DataWatcher();
        meta.set(EntityFishingHookHandle.DATA_HOOKED_ENTITY_ID, OptionalInt.of(this.hookedEntityId));
        meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.hookEntityId, meta, true);
        viewer.send(metaPacket);
    }

    /**
     * Temporarily hides this fishing line to the player. The entities that hold the line
     * stay spawned
     *
     * @param viewer
     */
    public void destroyLine(AttachmentViewer viewer) {
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this.hookEntityId));
    }

    private static class Offsets {
        /** Offset to position the player so that the held fishing rod line is exactly at 0/0/0 */
        public final Vector PLAYER;
        /** Offset to position the silverfish mount of the player to align at 0/0/0 */
        public final Vector HOLDER;
        /** Offset to position the hooked silverfish entity to align hook-line at 0/0/0 */
        public final Vector HOOKED;

        public Offsets(Vector OFFSET_PLAYER, Vector OFFSET_HOLDER, Vector OFFSET_HOOKED) {
            this.PLAYER = OFFSET_PLAYER;
            this.HOLDER = OFFSET_HOLDER;
            this.HOOKED = OFFSET_HOOKED;
        }
    }
}
