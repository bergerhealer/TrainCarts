package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
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

import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Extra utilities to move a 1.19.4+ Display entity around
 */
public abstract class VirtualDisplayEntity extends VirtualSpawnableObject {
    public static final EntityType BLOCK_DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("BLOCK_DISPLAY"), null);
    public static final EntityType ITEM_DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("ITEM_DISPLAY"), null);
    public static final EntityType TEXT_DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("TEXT_DISPLAY"), null);
    public static final EntityType INTERACTION_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("INTERACTION"), null);

    /**
     * This multiplier is needed for the bounding box (clip box) size to make sure this
     * block remains visible even when rotated 45 degrees.
     */
    public static final double BBOX_FACT = 1.0 / MathUtil.HALFROOTOFTWO;

    // This (unchanging) read-only metadata is used when spawning the mount of the display entity
    public static final DataWatcher ARMORSTAND_MOUNT_METADATA = new DataWatcher();
    static {
        ARMORSTAND_MOUNT_METADATA.set(EntityHandle.DATA_NO_GRAVITY, true);
        ARMORSTAND_MOUNT_METADATA.set(EntityHandle.DATA_FLAGS,
                (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        ARMORSTAND_MOUNT_METADATA.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                (byte) (EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                        EntityArmorStandHandle.DATA_FLAG_IS_SMALL |
                        EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE));
    }

    /**
     * Creates DataWatchers with the base metadata default values for a new display entity
     */
    public static final DataWatcher.Prototype BASE_DISPLAY_METADATA = DataWatcher.Prototype.build()
            .setClientDefault(DisplayHandle.DATA_INTERPOLATION_DURATION, 0)
            .set(DisplayHandle.DATA_INTERPOLATION_DURATION, 3)
            .set(DisplayHandle.DATA_POS_ROT_INTERPOLATION_DURATION, 3) // Only seen by 1.20.2+ clients
            .setClientDefault(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0)
            .setClientDefault(DisplayHandle.DATA_SCALE, new Vector(1, 1, 1))
            .setClientDefault(DisplayHandle.DATA_TRANSLATION, new Vector())
            .setClientDefault(DisplayHandle.DATA_LEFT_ROTATION, new Quaternion())
            .setClientDefault(DisplayHandle.DATA_RIGHT_ROTATION, new Quaternion())
            .setClientDefault(DisplayHandle.DATA_BRIGHTNESS_OVERRIDE, Brightness.UNSET)
            .setClientDefault(DisplayHandle.DATA_WIDTH, 0.0f)
            .setClientDefault(DisplayHandle.DATA_HEIGHT, 0.0f)
            .create();

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
    protected final Vector scale;
    private Brightness brightness;

    public VirtualDisplayEntity(AttachmentManager manager, EntityType entityType) {
        this(manager, entityType, BASE_DISPLAY_METADATA.create());
    }

    public VirtualDisplayEntity(AttachmentManager manager, EntityType entityType, DataWatcher metadata) {
        super(manager);
        mountEntityId = EntityUtil.getUniqueEntityId();
        displayEntityId = EntityUtil.getUniqueEntityId();
        displayEntityUUID = UUID.randomUUID();
        this.entityType = entityType;
        this.metadata = metadata;

        syncPos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        livePos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        liveRot = new Quaternion();

        scale = new Vector(1.0, 1.0, 1.0);
        brightness = Brightness.UNSET;
    }

    public DataWatcher getMetadata() {
        return metadata;
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
            onScaleUpdated();
        }
    }

    public void setScale(Vector scale) {
        if (this.scale.getX() != scale.getX() || this.scale.getY() != scale.getY() || this.scale.getZ() != scale.getZ()) {
            MathUtil.setVector(this.scale, scale);
            onScaleUpdated();
        }
    }

    protected void onScaleUpdated() {
        this.metadata.set(DisplayHandle.DATA_SCALE, this.scale);
    }

    /**
     * Sets a brightness (emission) override. Use Brightness.UNSET to disable.
     *
     * @param brightness Emissive block and sky light
     */
    public void setBrightness(Brightness brightness) {
        if (!this.brightness.equals(brightness)) {
            this.brightness = brightness;
            this.metadata.set(DisplayHandle.DATA_BRIGHTNESS_OVERRIDE, brightness);
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        // Update position
        MathUtil.setVector(livePos, transform.toVector());
        // Update rotation
        liveRot.setTo(transform.getRotation());
        // Allow further changes
        onRotationUpdated(liveRot);

        // Ensure synchronized the first time
        if (Double.isNaN(syncPos.getX())) {
            MathUtil.setVector(syncPos, livePos);
            syncPosition(true); // Initialize metadata
        }
    }

    protected void onRotationUpdated(Quaternion rotation) {
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        boolean canInterpolate = viewer.supportsDisplayEntityLocationInterpolation();

        // Spawn invisible marker armorstand mount to perform the movement, if interpolation isn't supported
        if (!canInterpolate) {
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
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
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

        if (!canInterpolate) {
            // Mount the display entity inside the mount
            viewer.getVehicleMountController().mount(this.mountEntityId, this.displayEntityId);
        }
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        if (viewer.supportsDisplayEntityLocationInterpolation()) {
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this.displayEntityId));
            viewer.getVehicleMountController().remove(this.displayEntityId);
        } else {
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewMultiple(
                    new int[] { this.displayEntityId, this.mountEntityId }));
            viewer.getVehicleMountController().remove(this.displayEntityId);
            viewer.getVehicleMountController().remove(this.mountEntityId);
        }
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
    public void setUseMinecartInterpolation(boolean use) {
        metadata.set(DisplayHandle.DATA_INTERPOLATION_DURATION, use ? 5 : 3);
    }

    @Override
    public void syncPosition(boolean absolute) {
        metadata.forceSet(DisplayHandle.DATA_TRANSLATION, this.computeTranslation(this.liveRot));
        metadata.forceSet(DisplayHandle.DATA_LEFT_ROTATION, this.liveRot);
        metadata.forceSet(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);

        // Force an absolute teleport if the change is too big
        final double dx, dy, dz;
        if (!absolute) {
            dx = (livePos.getX() - syncPos.getX());
            dy = (livePos.getY() - syncPos.getY());
            dz = (livePos.getZ() - syncPos.getZ());
            double abs_delta = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
            absolute = (abs_delta > EntityNetworkController.MAX_RELATIVE_DISTANCE);
        } else {
            dx = 0.0;
            dy = 0.0;
            dz = 0.0;
        }

        if (absolute) {
            // Teleport the entity
            MathUtil.setVector(syncPos, livePos);

            syncPositionLogic(id -> PacketPlayOutEntityTeleportHandle.createNew(id,
                    syncPos.getX(), syncPos.getY(), syncPos.getZ(),
                    0.0f, 0.0f, false));
        } else {
            // Perform a relative movement update
            PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle packet = syncPositionLogicAlwaysCreate(
                    id -> PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle.createNew(id,
                            dx, dy, dz,
                            false));

            MathUtil.addToVector(syncPos, packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
        }

        // Send metadata changes to update the orientation
        syncMeta();
    }

    private <T extends PacketHandle> T syncPositionLogicAlwaysCreate(IntFunction<T> packetCreator) {
        return syncPositionLogic(packetCreator).orElseGet(() -> packetCreator.apply(displayEntityId));
    }

    /**
     * Helper function. Automatically builds the right packet for synchronizing position using
     * the mount versus the display entity itself, depending on if this is supported by
     * the client(s).
     *
     * @param packetCreator Callback to create the packet to send, with as input the entity id
     *                      to sync
     */
    private <T extends PacketHandle> Optional<T> syncPositionLogic(IntFunction<T> packetCreator) {
        T packetForNewClients = null;
        T packetForOldClients = null;
        for (AttachmentViewer viewer : getViewers()) {
            if (viewer.supportsDisplayEntityLocationInterpolation()) {
                if (packetForNewClients == null) {
                    packetForNewClients = packetCreator.apply(displayEntityId);
                }
                viewer.send(packetForNewClients);
            } else {
                if (packetForOldClients == null) {
                    packetForOldClients = packetCreator.apply(mountEntityId);
                }
                viewer.send(packetForOldClients);
            }
        }
        if (packetForNewClients != null) {
            return Optional.of(packetForNewClients);
        } else if (packetForOldClients != null) {
            return Optional.of(packetForOldClients);
        } else {
            return Optional.empty();
        }
    }

    protected void syncMeta() {
        broadcast(PacketPlayOutEntityMetadataHandle.createNew(displayEntityId, metadata, false));
    }

    /**
     * Reads a configured brightness from YAML configuration
     *
     * @param config Configuration
     * @return Configured Brightness
     */
    public static Brightness loadBrightnessFromConfig(ConfigurationNode config) {
        ConfigurationNode brightnessConfig = config.getNodeIfExists("brightness");
        if (brightnessConfig != null) {
            return Brightness.blockAndSkyLight(brightnessConfig.get("block", 0),
                                               brightnessConfig.get("sky", 0));
        }
        return Brightness.UNSET;
    }

    /**
     * Writes a brightness value to YAML configuration. If unset, removes the config.
     *
     * @param config Configuration to write to
     * @param brightness Brightness value to write
     */
    public static void saveBrightnessToConfig(ConfigurationNode config, Brightness brightness) {
        if (brightness == Brightness.UNSET) {
            config.remove("brightness");
        } else {
            ConfigurationNode brightnessConfig = config.getNode("brightness");
            brightnessConfig.set("block", brightness.blockLight());
            brightnessConfig.set("sky", brightness.skyLight());
        }
    }
}
