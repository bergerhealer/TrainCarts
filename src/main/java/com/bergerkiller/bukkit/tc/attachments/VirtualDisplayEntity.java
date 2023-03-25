package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Extra utilities to move a 1.19.4+ Display entity around
 */
public abstract class VirtualDisplayEntity extends VirtualSpawnableObject {
    public static final EntityType BLOCK_DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("BLOCK_DISPLAY"), null);
    public static final EntityType ITEM_DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("ITEM_DISPLAY"), null);

    // This (unchanging) read-only metadata is used when spawning the mount of the display entity
    public static final DataWatcher ARMORSTAND_MOUNT_METADATA = new DataWatcher();
    static {
        ARMORSTAND_MOUNT_METADATA.watch(EntityHandle.DATA_NO_GRAVITY, true);
        ARMORSTAND_MOUNT_METADATA.watch(EntityHandle.DATA_FLAGS,
                (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        ARMORSTAND_MOUNT_METADATA.watch(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                (byte) (EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                        EntityArmorStandHandle.DATA_FLAG_IS_SMALL |
                        EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE));
    }

    // Entity tracking code
    private final int mountEntityId;
    private final int displayEntityId;
    private final UUID displayEntityUUID;
    private final EntityType entityType;
    private final Vector syncPos;
    private final Vector livePos;
    private final Quaternion liveRot;
    protected final DataWatcher metadata;

    // Properties
    private final Vector scale;

    public VirtualDisplayEntity(AttachmentManager manager, EntityType entityType) {
        super(manager);
        mountEntityId = EntityUtil.getUniqueEntityId();
        displayEntityId = EntityUtil.getUniqueEntityId();
        displayEntityUUID = UUID.randomUUID();
        this.entityType = entityType;
        syncPos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        livePos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        liveRot = new Quaternion();
        metadata = new DataWatcher();
        metadata.watch(DisplayHandle.DATA_INTERPOLATION_DURATION, 3);
        metadata.watch(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
        metadata.watch(DisplayHandle.DATA_SCALE, new Vector(1, 1, 1));
        metadata.watch(DisplayHandle.DATA_TRANSLATION, new Vector());
        metadata.watch(DisplayHandle.DATA_LEFT_ROTATION, new Quaternion());
        metadata.watch(DisplayHandle.DATA_RIGHT_ROTATION, new Quaternion());

        scale = new Vector(1.0, 1.0, 1.0);
    }

    protected Vector computeTranslation(Quaternion rotation) {
        return new Vector();
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return entityId == mountEntityId || entityId == displayEntityId;
    }

    public Vector getScale() {
        return scale;
    }

    public void setScale(Vector3 scale) {
        if (this.scale.getX() != scale.x || this.scale.getY() != scale.y || this.scale.getZ() != scale.z) {
            MathUtil.setVector(this.scale, scale.x, scale.y, scale.z);
            this.metadata.set(DisplayHandle.DATA_SCALE, this.scale);
        }
    }

    public void setScale(Vector scale) {
        if (this.scale.getX() != scale.getX() || this.scale.getY() != scale.getY() || this.scale.getZ() != scale.getZ()) {
            MathUtil.setVector(this.scale, scale);
            this.metadata.set(DisplayHandle.DATA_SCALE, this.scale);
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        // Update position
        MathUtil.setVector(livePos, transform.toVector());
        // Update rotation
        liveRot.setTo(transform.getRotation());

        // Ensure synchronized the first time
        if (Double.isNaN(syncPos.getX())) {
            MathUtil.setVector(syncPos, livePos);
            syncPosition(true); // Initialize metadata
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        // Spawn invisible marker armorstand mount
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
            viewer.sendEntityLivingSpawnPacket(spawnPacket, ARMORSTAND_MOUNT_METADATA);
        }

        // Spawn the display entity itself
        {
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
            spawnPacket.setEntityId(this.displayEntityId);
            spawnPacket.setEntityUUID(this.displayEntityUUID);
            spawnPacket.setEntityType(this.entityType);
            spawnPacket.setPosX(this.syncPos.getX() - motion.getX());
            spawnPacket.setPosY(this.syncPos.getY() - motion.getY());
            spawnPacket.setPosZ(this.syncPos.getZ() - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            viewer.send(spawnPacket);
            viewer.send(PacketPlayOutEntityMetadataHandle.createNew(this.displayEntityId, metadata, true));
        }

        // Mount the display entity inside the mount
        viewer.getVehicleMountController().mount(this.mountEntityId, this.displayEntityId);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(
                new int[] { this.displayEntityId, this.mountEntityId }));
        viewer.getVehicleMountController().remove(this.displayEntityId);
        viewer.getVehicleMountController().remove(this.mountEntityId);
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        this.metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING,
                color != null);
        this.syncMeta();
    }

    @Override
    protected void applyGlowColorForViewer(AttachmentViewer viewer, ChatColor color) {
        viewer.updateGlowColor(displayEntityUUID, color);
    }

    @Override
    public void syncPosition(boolean absolute) {
        metadata.forceSet(DisplayHandle.DATA_TRANSLATION, this.computeTranslation(this.liveRot));
        metadata.forceSet(DisplayHandle.DATA_LEFT_ROTATION, this.liveRot);
        metadata.forceSet(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);

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

        // Send metadata changes to update the orientation
        syncMeta();
    }

    protected void syncMeta() {
        broadcast(PacketPlayOutEntityMetadataHandle.createNew(displayEntityId, metadata, false));
    }
}
