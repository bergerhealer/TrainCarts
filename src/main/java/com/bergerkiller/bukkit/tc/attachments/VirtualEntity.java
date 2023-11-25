package com.bergerkiller.bukkit.tc.attachments;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityVelocityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle.PacketPlayOutEntityLookHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveLookHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityTrackerEntryStateHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol.
 * The entity can be spawned or destroyed for individual players.
 */
public class VirtualEntity extends VirtualSpawnableObject {
    /**
     * Add this offset to the sit butt offset and the position is exactly where the center of the head is.
     * This is where the player looks from.
     */
    public static final double PLAYER_SIT_BUTT_EYE_HEIGHT = 1.0;
    /**
     * The offset from the player/villager's feet to where the eyes/camera are located while standing
     */
    public static final double PLAYER_STANDING_EYE_HEIGHT = 1.62;
    /**
     * The height offset a player sits on top of a chicken
     */
    public static final double PLAYER_SIT_CHICKEN_BUTT_OFFSET = -0.62;
    /**
     * The height offset above which a butt rests on top of a MARKER armor stand
     *
     * @deprecated Use {@link AttachmentViewer#getArmorStandButtOffset()} instead, as this is now
     *             game-version dependent.
     */
    @Deprecated
    public static final double ARMORSTAND_BUTT_OFFSET = 0.27;

    private final int entityId;
    private final UUID entityUUID;
    protected DataWatcher metaData;
    private double posX, posY, posZ;
    private boolean posSet;
    private final Vector liveAbsPos;
    private final Vector syncAbsPos;
    private final Vector velSyncAbsPos;
    private float liveYaw, livePitch;
    private float syncYaw, syncPitch;
    private double liveVel;
    private double syncVel;
    private Vector relativePos = new Vector();
    private ByViewerPositionAdjustment byViewerPositionAdjustment = null;
    private EntityType entityType = EntityType.CHICKEN;
    private boolean entityTypeIsMinecart = false;
    private boolean respawnOnPitchFlip = false;
    private int rotateCtr = 0;
    private SyncMode syncMode = SyncMode.NORMAL;
    private boolean minecartInterpolation = false;
    private boolean useParentMetadata = false;
    private Vector yawPitchRoll = new Vector(0.0, 0.0, 0.0);

    public VirtualEntity(AttachmentManager manager) {
        this(manager, EntityUtil.getUniqueEntityId(), UUID.randomUUID());
    }

    public VirtualEntity(AttachmentManager manager, int entityId, UUID entityUUID) {
        super(manager);
        this.entityId = entityId;
        this.entityUUID = entityUUID;
        this.metaData = new DataWatcher();
        this.liveAbsPos = new Vector();
        this.syncAbsPos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        this.velSyncAbsPos = new Vector(Double.NaN, Double.NaN, Double.NaN);
        this.syncVel = 0.0;
        this.posX = this.posY = this.posZ = 0.0;
        this.posSet = false;
    }

    public DataWatcher getMetaData() {
        return this.metaData;
    }

    public UUID getEntityUUID() {
        return this.entityUUID;
    }

    public int getEntityId() {
        return this.entityId;
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return entityId == this.entityId;
    }

    public double getPosX() {
        return this.liveAbsPos.getX();
    }

    public double getPosY() {
        return this.liveAbsPos.getY();
    }

    public double getPosZ() {
        return this.liveAbsPos.getZ();
    }

    public Vector getPos() {
        return this.liveAbsPos;
    }

    public boolean isMountable() {
        return VehicleMountRegistry.isMountable(this.entityType);
    }

    public double getMountOffset() {
        return VehicleMountRegistry.getOffset(this.entityType);
    }

    public boolean syncPositionIfMounted() {
        return VehicleMountRegistry.syncPositionIfMounted(this.entityType);
    }

    /**
     * Sets whether to destroy and re-spawn the entity when pitch flips
     * between -180 and 180.
     *
     * @param respawn True to respawn on pitch flip
     */
    public void setRespawnOnPitchFlip(boolean respawn) {
        this.respawnOnPitchFlip = respawn;
    }

    /**
     * Sets the relative position of this Entity
     * 
     * @param position
     */
    public void setPosition(Vector position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        this.posSet = true;
    }

    public Vector getRelativeOffset() {
        return relativePos;
    }

    public void setRelativeOffset(Vector offset) {
        MathUtil.setVector(relativePos, offset);
    }

    public void setRelativeOffset(double dx, double dy, double dz) {
        MathUtil.setVector(relativePos, dx, dy, dz);
    }

    public void addRelativeOffset(Vector offset) {
        relativePos.add(offset);
    }

    public void addRelativeOffset(double dx, double dy, double dz) {
        MathUtil.addToVector(relativePos, dx, dy, dz);
    }

    public void setByViewerPositionAdjustment(ByViewerPositionAdjustment adjustment) {
        this.byViewerPositionAdjustment = adjustment;
    }

    public void setSyncMode(SyncMode mode) {
        this.syncMode = mode;
        if (mode == SyncMode.SEAT) {
            this.livePitch = this.syncPitch = 0.0f;
        }
    }

    @Override
    public void setUseMinecartInterpolation(boolean use) {
        this.minecartInterpolation = use;
    }

    public Vector getYawPitchRoll() {
        return this.yawPitchRoll;
    }

    public Vector getSyncPos() {
        return this.syncAbsPos;
    }

    public float getLiveYaw() {
        return this.liveYaw;
    }

    public float getLivePitch() {
        return this.livePitch;
    }

    public float getSyncYaw() {
        return this.syncYaw;
    }

    public float getSyncPitch() {
        return this.syncPitch;
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        Quaternion rotation = transform.getRotation();
        Vector f = rotation.forwardVector();
        double yaw, pitch;

        if (this.hasPitch()) {
            Vector u = rotation.upVector();

            // Compute yawmode factor - whether to use the forward or up-vector for computing yaw
            // A value below 0.0 indicates the forward vector should be used (mostly horizontal)
            // A value above 1.0 indicates the up vector should be used (mostly vertical)
            // A value between 0.0 and 1.0 selects a smooth combination of both
            final double yawmode_factor_start = 0.9;
            final double yawmode_factor_end = 0.99;
            double yawmode_factor = (Math.abs(f.getY()) - yawmode_factor_start) / (1.0 - yawmode_factor_end);

            // Invert up-vector when upside-down
            // Up-vector is only used when the entity is vertical - so this is fine.
            boolean isFrontSideDown = (f.getY() < 0.0);

            if (u.getY() < 0.0) {
                // Upside-down
                pitch = 180.0 + MathUtil.getLookAtPitch(f.getX(), -f.getY(), f.getZ());
                f.multiply(-1.0);
            } else {
                // Upright
                pitch = MathUtil.getLookAtPitch(f.getX(), f.getY(), f.getZ());
            }

            if (isFrontSideDown) {
                u.multiply(-1.0);
            }

            if (yawmode_factor <= 0.0) {
                // Horizontal, use forward vector for yaw
                yaw = MathUtil.getLookAtYaw(-f.getZ(), f.getX());
            } else if (yawmode_factor >= 1.0) {
                // Vertical, use up-vector for yaw
                yaw = MathUtil.getLookAtYaw(u.getZ(), -u.getX());
            } else {
                // Mix of the above
                double ax = yawmode_factor *  u.getZ() + (1.0 - yawmode_factor) * -f.getZ();
                double az = yawmode_factor * -u.getX() + (1.0 - yawmode_factor) * f.getX();
                yaw = MathUtil.getLookAtYaw(ax, az);
            }
        } else {
            // If this entity has no pitch - return yaw instantly
            yaw = MathUtil.getLookAtYaw(-f.getZ(), f.getX());
            pitch = 0.0;
        }

        updatePosition(transform, new Vector(pitch, yaw, 0.0));
    }

    /**
     * Updates the position of the displayed part
     * 
     * @param transform relative to which the part should be positioned
     * @param yawPitchRoll rotation
     */
    public void updatePosition(Matrix4x4 transform, Vector yawPitchRoll) {
        if (this.posSet) {
            Vector v = new Vector(this.posX, this.posY, this.posZ);
            transform.transformPoint(v);
            updatePosition(v, yawPitchRoll);
        } else {
            updatePosition(transform.toVector(), yawPitchRoll);
        }
    }

    /**
     * Updates the position of the displayed part
     * 
     * @param position to move at, the transform-relative position of this entity is ignored
     * @param yawPitchRoll rotation
     */
    public void updatePosition(Vector position, Vector yawPitchRoll) {
        MathUtil.setVector(this.liveAbsPos, position);
        this.liveAbsPos.add(this.relativePos);

        this.yawPitchRoll = yawPitchRoll;
        this.liveYaw = (float) this.yawPitchRoll.getY();
        if (this.syncMode != SyncMode.SEAT && this.hasPitch()) {
            livePitch = (float) this.yawPitchRoll.getX();
        } else {
            livePitch = 0.0f;
        }
        if (this.entityTypeIsMinecart) {
            this.liveYaw -= 90.0f;
        }

        // If sync is not yet set, set it to live
        if (Double.isNaN(this.syncAbsPos.getX())) {
            this.syncPositionSilent();
        }

        // Calculate the velocity by comparing the last synchronized position with the live position
        // This should only be done when sound is enabled for the Minecart
        // Velocity is used exclusively for controlling the minecart's audio level
        // When derailed, no audio should be made. Otherwise, the velocity speed controls volume.
        // Only applies when used in a minecart member network environment
        liveVel = 0.0;
        if (this.entityTypeIsMinecart && this.manager instanceof AttachmentControllerMember) {
            MinecartMember<?> member = ((AttachmentControllerMember) manager).getMember();
            if (member.hasInitializedGroup() && member.getGroup().getProperties().isSoundEnabled() && !member.isDerailed()) {
                if (!Double.isNaN(this.velSyncAbsPos.getX())) {
                    liveVel = this.liveAbsPos.distance(this.velSyncAbsPos);
                }
                MathUtil.setVector(this.velSyncAbsPos, this.liveAbsPos);

                // Limit to a maximum of 1.0, above this it's kind of pointless
                if (liveVel > 1.0) liveVel = 1.0;

                // Audio cutoff
                if (liveVel < 0.001) liveVel = 0.0;
            }
        }
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
        this.entityTypeIsMinecart = isMinecart(entityType);
    }

    public EntityType getEntityType() {
        return this.entityType;
    }

    public boolean isMinecart() {
        return this.entityTypeIsMinecart;
    }

    /**
     * Sets whether the controller entity metadata is used in place of this entity's metadata
     * 
     * @param use
     */
    public void setUseParentMetadata(boolean use) {
        this.useParentMetadata = use;
    }

    // Avoid breaking API compatibility
    @Deprecated
    public void addViewerWithoutSpawning(Player viewer) {
        super.addViewerWithoutSpawning(viewer);
    }

    // Avoid breaking API compatibility
    public void addViewerWithoutSpawning(AttachmentViewer viewer) {
        super.addViewerWithoutSpawning(viewer);
    }

    // Avoid breaking API compatibility
    public boolean hasViewers() {
        return super.hasViewers();
    }

    // Avoid breaking API compatibility
    @Deprecated
    public boolean isViewer(Player viewer) {
        return super.isViewer(viewer);
    }

    // Avoid breaking API compatibility
    public boolean isViewer(AttachmentViewer viewer) {
        return super.isViewer(viewer);
    }

    // Avoid breaking API compatibility
    @Deprecated
    public final void spawn(Player viewer, Vector motion) {
        super.spawn(viewer, motion);
    }

    // Avoid breaking API compatibility
    public final void spawn(AttachmentViewer viewer, Vector motion) {
        super.spawn(viewer, motion);
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        //motX = motY = motZ = 0.0;

        //System.out.println("SPAWN " + this.syncAbsX + "/" + this.syncAbsY + "/" + this.syncAbsZ + " ID=" + this.entityUUID);

        // Position to spawn at
        Vector spawnPos = this.syncAbsPos.clone();
        if (byViewerPositionAdjustment != null) {
            byViewerPositionAdjustment.adjust(viewer, spawnPos);
        }
        spawnPos.subtract(motion);

        // Create a spawn packet appropriate for the type of entity being spawned
        if (isLivingEntity()) {
            // Spawn living entity
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(this.entityUUID);
            spawnPacket.setEntityType(this.entityType);
            spawnPacket.setPosX(spawnPos.getX());
            spawnPacket.setPosY(spawnPos.getY());
            spawnPacket.setPosZ(spawnPos.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(this.syncYaw);
            spawnPacket.setPitch(this.syncPitch);
            spawnPacket.setHeadYaw((this.syncMode == SyncMode.ITEM) ? 0.0f : this.syncYaw);
            viewer.sendEntityLivingSpawnPacket(spawnPacket, getUsedMeta());
        } else {
            // Spawn entity (generic)
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(this.entityUUID);
            spawnPacket.setEntityType(this.entityType);
            spawnPacket.setPosX(spawnPos.getX());
            spawnPacket.setPosY(spawnPos.getY());
            spawnPacket.setPosZ(spawnPos.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(this.syncYaw);
            spawnPacket.setPitch(this.syncPitch);
            viewer.send(spawnPacket);

            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, getUsedMeta(), true);
            viewer.send(metaPacket.toCommonPacket());
        }

        if (this.syncMode == SyncMode.SEAT) {
            PacketPlayOutRelEntityMoveLookHandle movePacket = PacketPlayOutRelEntityMoveLookHandle.createNew(
                    this.entityId,
                    motion.getX(), motion.getY(), motion.getZ(),
                    this.syncYaw,
                    this.syncPitch,
                    false);
            viewer.send(movePacket);
        } else if (motion.lengthSquared() > 0.001) {
            CommonPacket movePacket = PacketType.OUT_ENTITY_MOVE.newInstance(this.entityId, motion.getX(), motion.getY(), motion.getZ(), false);
            viewer.send(movePacket);
        }

        // Resend velocity if one is set
        if (this.syncVel > 0.0) {
            viewer.send(PacketPlayOutEntityVelocityHandle.createNew(this.entityId, this.syncVel, 0.0, 0.0));
        }
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        this.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING,
                color != null);
        this.syncMetadata();
    }

    @Override
    protected void applyGlowColorForViewer(AttachmentViewer viewer, ChatColor color) {
        viewer.updateGlowColor(this.entityUUID, color);
    }

    public void syncMetadata() {
        DataWatcher metaData = getUsedMeta();
        if (metaData.isChanged()) {
            broadcast(PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metaData, false));
        }
    }

    /**
     * Sets the synchronized position of this entity without sending entity move/teleport packets
     */
    public void syncPositionSilent() {
        MathUtil.setVector(this.syncAbsPos, this.liveAbsPos);
        this.syncYaw = this.liveYaw;
        this.syncPitch = this.livePitch;
        this.syncVel = this.liveVel;
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (!this.hasViewers()) {
            // No viewers. Assign live to sync right away.
            syncPositionSilent();
            return;
        }

        // Synchronize velocity
        // Minecraft does not play minecart audio for the Y-axis. To make sound on vertical rails,
        // we instead apply the vector length to just the X-axis so that this works.
        // Velocity packets are only relevant when minecarts are used (with audio enabled)
        if (Math.abs(this.liveVel - this.syncVel) > 0.01 || (this.syncVel > 0.0 && this.liveVel == 0.0)) {
            this.syncVel = this.liveVel;

            broadcast(PacketPlayOutEntityVelocityHandle.createNew(this.entityId, this.syncVel, 0.0, 0.0));
        }

        // Synchronize metadata
        this.syncMetadata();

        // Live motion. Check if the distance change is too large.
        double dx = (this.liveAbsPos.getX() - this.syncAbsPos.getX());
        double dy = (this.liveAbsPos.getY() - this.syncAbsPos.getY());
        double dz = (this.liveAbsPos.getZ() - this.syncAbsPos.getZ());
        double abs_delta = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        boolean largeChange = (abs_delta > EntityNetworkController.MAX_RELATIVE_DISTANCE);

        // Detect a glitched pitch rotation, and perform a respawn then
        if (this.respawnOnPitchFlip && this.syncPitch != this.livePitch && Util.isProtocolRotationGlitched(this.syncPitch, this.livePitch)) {
            this.forAllViewers(this::sendDestroyPacketsWithoutVMC);
            this.syncPositionSilent();
            for (AttachmentViewer viewer : this.getViewers()) {
                sendSpawnPackets(viewer, largeChange ? new Vector() : new Vector(dx, dy, dz));
            }
            return;
        }

        // When an absolute update is required, send a teleport packet and refresh the synchronized position instantly
        if (absolute || largeChange) {
            if (byViewerPositionAdjustment != null) {
                // Different position for every player, potentially
                Vector pos = new Vector();
                for (AttachmentViewer viewer : getViewers()) {
                    MathUtil.setVector(pos, liveAbsPos);
                    byViewerPositionAdjustment.adjust(viewer, pos);
                    viewer.send(PacketPlayOutEntityTeleportHandle.createNew(this.entityId,
                            pos.getX(), pos.getY(), pos.getZ(),
                            this.liveYaw, this.livePitch, false));
                }
            } else {
                // Same packet for everyone
                broadcast(PacketPlayOutEntityTeleportHandle.createNew(this.entityId,
                        this.liveAbsPos.getX(), this.liveAbsPos.getY(), this.liveAbsPos.getZ(),
                        this.liveYaw, this.livePitch, false));
            }
            syncPositionSilent();
            refreshHeadRotation();
            return;
        }

        boolean moved, rotated, rotatedNow;

        // Check that the position changed meaningfully (can be represented in protocol)
        // TODO: make this a constant somewhere
        moved = (abs_delta >= (1.0 / 4096.0));

        // Check for changes in rotation
        rotatedNow = EntityTrackerEntryStateHandle.hasProtocolRotationChanged(this.liveYaw, this.syncYaw) ||
                     EntityTrackerEntryStateHandle.hasProtocolRotationChanged(this.livePitch, this.syncPitch);

        // Remember the rotation change for X more ticks. This prevents partial rotation on the client.
        rotated = false;
        if (rotatedNow) {
            forceSyncRotation();
            rotated = true;
        } else if (rotateCtr > 0) {
            rotateCtr--;
            rotated = true;
        }

        // Refresh head rotation first
        if (rotatedNow) {
            this.refreshHeadRotation();
        }

        // When trying to imitate the minecart's update rate, perform less delta movement than reality
        if (this.minecartInterpolation) {
            final double FACTOR = 3.0 / 5.0;
            dx *= FACTOR;
            dy *= FACTOR;
            dz *= FACTOR;
        }

        if (moved && rotated) {
            // Position and rotation changed
            PacketPlayOutRelEntityMoveLookHandle packet = PacketPlayOutRelEntityMoveLookHandle.createNew(
                    this.entityId,
                    dx, dy, dz,
                    this.liveYaw,
                    this.livePitch,
                    false);

            this.syncYaw = packet.getYaw();
            this.syncPitch = packet.getPitch();
            MathUtil.addToVector(this.syncAbsPos, packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
            broadcast(packet);
        } else if (moved) {
            // Only position changed
            PacketPlayOutRelEntityMoveHandle packet = PacketPlayOutRelEntityMoveHandle.createNew(
                    this.entityId,
                    dx, dy, dz,
                    false);

            MathUtil.addToVector(this.syncAbsPos, packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
            broadcast(packet);
        } else if (rotated) {
            // Only rotation changed
            for (AttachmentViewer viewer : this.getViewers()) {
                if (viewer.evaluateGameVersion(">=", "1.15")) {
                    // On minecraft 1.15 and later there is a Minecraft client bug
                    // Sending an Entity Look packet causes the client to cancel/ignore previous movement updates
                    // This results in the entity position going out of sync
                    // A workaround is sending a movement + look packet instead, which appears to work around that.
                    PacketPlayOutRelEntityMoveLookHandle packet = PacketPlayOutRelEntityMoveLookHandle.createNew(
                            this.entityId,
                            0.0, 0.0, 0.0,
                            this.liveYaw,
                            this.livePitch,
                            false);

                    viewer.send(packet);
                    this.syncYaw = packet.getYaw();
                    this.syncPitch = packet.getPitch();
                } else {
                    PacketPlayOutEntityLookHandle packet = PacketPlayOutEntityLookHandle.createNew(
                            this.entityId,
                            this.liveYaw,
                            this.livePitch,
                            false);

                    viewer.send(packet);
                    this.syncYaw = packet.getYaw();
                    this.syncPitch = packet.getPitch();
                }
            }
        }
    }

    /**
     * Sends the correct rotation of the entity for the following 14 ticks.
     * This helps unstick the client.
     */
    public void forceSyncRotation() {
        this.rotateCtr = 14;
    }

    private void refreshHeadRotation() {
        // Refresh head rotation first
        if (this.syncMode.isNormal() && isLivingEntity()) {
            CommonPacket packet = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this.entityId);
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, this.liveYaw);
            broadcast(packet);
        }
    }

    public static boolean isLivingEntity(org.bukkit.entity.EntityType entityType) {
        Class<?> entityClass = entityType.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }

    private boolean isLivingEntity() {
        return isLivingEntity(this.entityType);
    }

    public void respawnForAll(final Vector motion) {
        forAllViewers(this::sendDestroyPacketsWithoutVMC);
        syncPosition(true);
        forAllViewers(v -> sendSpawnPackets(v, motion));
    }

    // Avoid breaking API compatibility
    public void destroyForAll() {
        super.destroyForAll();
    }

    // Avoid breaking API compatibility
    @Deprecated
    public void destroy(Player viewer) {
        super.destroy(viewer);
    }

    // Avoid breaking API compatibility
    public void destroy(AttachmentViewer viewer) {
        super.destroy(viewer);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        sendDestroyPacketsWithoutVMC(viewer);
        viewer.getVehicleMountController().remove(this.entityId);
    }

    private void sendDestroyPacketsWithoutVMC(AttachmentViewer viewer) {
        if (this.syncVel > 0.0) {
            viewer.send(PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, new Vector()));
        }
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNewSingle(this.entityId);
        viewer.send(destroyPacket);
    }

    // Avoid breaking API compatibility
    public void broadcast(CommonPacket packet) {
        super.broadcast(packet);
    }

    // Avoid breaking API compatibility
    public void broadcast(PacketHandle packet) {
        super.broadcast(packet);
    }

    private DataWatcher getUsedMeta() {
        if (this.useParentMetadata && this.manager instanceof AttachmentControllerMember) {
            return ((AttachmentControllerMember) this.manager).getMember().getEntity().getMetaData();
        } else {
            return this.metaData;
        }
    }

    private boolean hasPitch() {
        return this.entityTypeIsMinecart || isLivingEntity();
    }

    public static boolean isMinecart(EntityType entityType) {
        switch (entityType) {
        case MINECART:
        case MINECART_CHEST:
        case MINECART_FURNACE:
        case MINECART_TNT:
        case MINECART_COMMAND:
        case MINECART_MOB_SPAWNER:
        case MINECART_HOPPER:
            return true;
        default:
            return false;
        }
    }

    public static enum SyncMode {
        /** Omits all information except what is required for displaying an item in an ArmorStand */
        ITEM(false),
        /** Position and rotation is updated */
        NORMAL(true),
        /** Only position and vehicle yaw is updated */
        SEAT(false);

        private final boolean _normal;

        private SyncMode(boolean normal) {
            this._normal = normal;
        }

        public boolean isNormal() {
            return this._normal;
        }
    }

    /**
     * Stores the vertical offset where passengers are mounted inside other entities.
     * Some entities, like horses, shouldn't be mounted directly because they are
     * controlled by the player riding it.
     */
    private static class VehicleMountRegistry {
        private static final Map<EntityType, Double> _lookup = new EnumMap<>(EntityType.class);
        private static final Set<EntityType> _unmountable = EnumSet.noneOf(EntityType.class);
        private static final Set<EntityType> _noPositionSyncIfMounted = EnumSet.noneOf(EntityType.class);
        private static final Double DEFAULT_OFFSET = 1.0;
        static {
            register("AXOLOTL", 0.59);
            register("BAT", 1.0);
            register("BEE", 0.7);
            register("BLAZE", 1.6);
            register("BOAT", 0.2, false, true);
            register("CAT", 0.8);
            register("CAVE_SPIDER", 0.5);
            register("CHICKEN", 0.62);
            register("COD", 0.5);
            register("COW", 1.3);
            register("CREEPER", 1.55);
            register("DOLPHIN", 0.75);
            register("DONKEY", 1.15);
            register("DROWNED", 1.75);
            register("ENDERMAN", 2.45);
            register("ENDERMITE", 0.5);
            register("ENDER_DRAGON", 3.4, false, true);
            register("EVOKER", 1.75);
            register("FALLING_BLOCK", 1.0);
            register("FOX", 0.8);
            register("GHAST", 4.0, false, true);
            register("GIANT", 12.0, false, true);
            register("GLOW_SQUID", 0.9);
            register("GOAT", 1.25);
            register("GUARDIAN", 0.92);
            register("HOGLIN", 1.5);
            register("HORSE", 1.4, false, true);
            register("HUSK", 1.75);
            register("ILLUSIONER", 1.75);
            register("IRON_GOLEM", 2.3);
            register("LEASH_HITCH", 0.97);
            register("LLAMA", 1.37, false, true);
            register(e -> e.name().contains("MINECART"), 0.27);
            register("MULE", 1.22);
            register("MUSHROOM_COW", 1.3);
            register("OCELOT", 0.8);
            register("PANDA", 1.2);
            register("PARROT", 0.96);
            register("PHANTOM", 0.67);
            register("PIG", 0.965);
            register("PIGLIN", 2.05);
            register("PIGLIN_BRUTE", 1.75);
            register("PILLAGER", 1.75);
            register("POLAR_BEAR", 1.305);
            register("PRIMED_TNT", 1.0);
            register("PUFFERFISH", 0.55);
            register("RABBIT", 0.63);
            register("RAVAGER", 2.4);
            register("SALMON", 0.58);
            register("SHEEP", 1.25);
            register("SHULKER", 1.0, false, false);
            register("SHULKER_BULLET", 0.52);
            register("SILVERFISH", 0.51);
            register("SKELETON", 1.75);
            register("SKELETON_HORSE", 1.3, false, true);
            register("SMALL_FIREBALL", 0.52);
            register("SNOWMAN", 1.67);
            register("SPIDER", 0.73);
            register("SQUID", 0.88);
            register("STRAY", 1.775);
            register("STRIDER", 1.79, false, true);
            register("TRADER_LLAMA", 1.36);
            register("TURTLE", 0.58);
            register("VEX", 0.88);
            register("VILLAGER", 1.75);
            register("VINDICATOR", 1.75);
            register("WANDERING_TRADER", 1.75);
            register("WITCH", 1.75);
            register("WITHER", 3.5, false, true);
            register("WITHER_SKELETON", 2.07);
            register("WITHER_SKULL", 0.52);
            register("WOLF", 0.92);
            register("ZOGLIN", 1.53);
            register("ZOMBIE", 1.75);
            register("ZOMBIE_HORSE", 1.47);
            register("ZOMBIE_VILLAGER", 1.75);
            register("ZOMBIFIED_PIGLIN", 1.75);
            register("ZOMBIE_VILLAGER", 1.75);
        }

        public static double getOffset(EntityType type) {
            return _lookup.getOrDefault(type, DEFAULT_OFFSET);
        }

        public static boolean isMountable(EntityType type) {
            return !_unmountable.contains(type);
        }

        public static boolean syncPositionIfMounted(EntityType type) {
            return !_noPositionSyncIfMounted.contains(type);
        }

        private static void register(Predicate<EntityType> condition, double offset) {
            register(condition, offset, true, true);
        }

        private static void register(Predicate<EntityType> condition, double offset, boolean mountable, boolean syncPosition) {
            Stream.of(EntityType.values()).filter(condition).forEachOrdered(type -> {
                _lookup.put(type, offset);
                if (!mountable) {
                    _unmountable.add(type);
                }
                if (!syncPosition) {
                    _noPositionSyncIfMounted.add(type);
                }
            });
        }

        private static void register(String name, double offset) {
            register(name, offset, true, true);
        }

        private static void register(String name, double offset, boolean mountable, boolean syncPosition) {
            try {
                EntityType type = EntityType.valueOf(name);
                _lookup.put(type, offset);
                if (!mountable) {
                    _unmountable.add(type);
                }
                if (!syncPosition) {
                    _noPositionSyncIfMounted.add(type);
                }
            } catch (IllegalArgumentException ex) { /* ignore */ }
        }
    }

    /**
     * Callback that adjusts the position of this virtual entity based on the type
     * of viewer that views it. This allows for different offsets to be specified
     * for different Minecraft client versions, for example.<br>
     * <br>
     * This requires the adjustment to be a constant, as relative updates are not
     * forwarded to this function.
     */
    @FunctionalInterface
    public interface ByViewerPositionAdjustment {
        void adjust(AttachmentViewer viewer, Vector position);
    }
}
