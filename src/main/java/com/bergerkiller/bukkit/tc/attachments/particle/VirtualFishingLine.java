package com.bergerkiller.bukkit.tc.attachments.particle;

import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.DataWatcherObjectHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityFishingHookHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.mountiplex.reflection.SafeField;

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
            PacketPlayOutNamedEntitySpawnHandle spawnPacket = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
            spawnPacket.setEntityId(this.holderPlayerEntityId);
            spawnPacket.setEntityUUID(viewer.getUniqueId());
            spawnPacket.setPosX(positionA.getX() + OFFSET_PLAYER.getX());
            spawnPacket.setPosY(positionA.getY() + OFFSET_PLAYER.getY());
            spawnPacket.setPosZ(positionA.getZ() + OFFSET_PLAYER.getZ());

            DataWatcher meta = new DataWatcher();
            meta.set(EntityHandle.DATA_NO_GRAVITY, true);
            meta.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, true);
            PacketUtil.sendNamedEntitySpawnPacket(viewer, spawnPacket, meta);
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
            meta.set(DATA_FISHINGHOOK_HOOKED_ENTITY, OptionalInt.of(this.hookedEntityId));
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
        if (this.holderEntityId == -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(
                    new int[] { this.hookedEntityId, this.hookEntityId }));
        } else {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNew(
                    new int[] { this.hookedEntityId, this.holderEntityId,
                            this.holderPlayerEntityId, this.hookEntityId }));
        }
    }

    // Note: can be removed once BKCommonLib 1.16.5-v2 or later becomes a dependency
    private static final DataWatcher.Key<OptionalInt> DATA_FISHINGHOOK_HOOKED_ENTITY;
    static {
        if (Common.getVersion() > Common.VERSION ||
            Common.hasCapability("Common:FishingHookFixes1.16") ||
            Common.evaluateMCVersion("<=", "1.15.2")
        ) {
            // Works fine on 1.15.2 and earlier, and once patched on 1.16 and later.
            DATA_FISHINGHOOK_HOOKED_ENTITY = EntityFishingHookHandle.DATA_HOOKED_ENTITY_ID;
        } else {
            // Was broken in BKCommonLib. Workaround get field using reflection.
            DATA_FISHINGHOOK_HOOKED_ENTITY = new DataWatcher.Key<OptionalInt>(
                    SafeField.get(EntityFishingHookHandle.T.getType(),
                                  Common.evaluateMCVersion(">=", "1.16.5") ? "HOOKED_ENTITY" : "e",
                                  DataWatcherObjectHandle.T.getType()), DataWatcher.Key.Type.ENTITY_ID);
        }
    }
}
