package com.bergerkiller.bukkit.tc.attachments;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryStateHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutEntityLookHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveLookHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityVelocityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol.
 * The entity can be spawned or destroyed for individual players.
 */
public class VirtualEntity {
    /**
     * Add this offset to the sit butt offset and the position is exactly where the center of the head is.
     * This is where the player looks from.
     */
    public static final double PLAYER_SIT_BUTT_EYE_HEIGHT = 1.0;
    /**
     * The height offset a player sits on top of a chicken
     */
    public static final double PLAYER_SIT_CHICKEN_BUTT_OFFSET = -0.62;
    /**
     * The height offset a player sits on top of an armorstand
     */
    public static final double PLAYER_SIT_ARMORSTAND_BUTT_OFFSET = -0.27;
    /**
     * Look packet doesn't work on 1.15, glitches out, must send a look + move packet with 0/0/0 movement
     */
    private static final boolean IS_LOOK_PACKET_BUGGED = Common.evaluateMCVersion(">=", "1.15");
    /**
     * Legacy was needed for seats when a chicken was used
     */
    private static final boolean NEEDS_UNSTUCK_VECTOR = Boolean.FALSE.booleanValue();

    private final AttachmentManager manager;
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metaData;
    private double posX, posY, posZ;
    private boolean posSet;
    private double liveAbsX, liveAbsY, liveAbsZ;
    private double syncAbsX, syncAbsY, syncAbsZ;
    private double velSyncAbsX, velSyncAbsY, velSyncAbsZ;
    private float liveYaw, livePitch;
    private float syncYaw, syncPitch;
    private double liveVel;
    private double syncVel;
    private double relDx, relDy, relDz;
    private EntityType entityType = EntityType.CHICKEN;
    private boolean entityTypeIsMinecart = false;
    private int rotateCtr = 0;
    private SyncMode syncMode = SyncMode.NORMAL;
    private boolean useParentMetadata = false;
    private final ArrayList<Player> viewers = new ArrayList<Player>();
    private Vector yawPitchRoll = new Vector(0.0, 0.0, 0.0);

    /**
     * Deprecated: now uses Attachment Manager
     * 
     * @param controller
     * @param entityId
     * @param entityUUID
     */
    @Deprecated
    public VirtualEntity(MinecartMemberNetwork controller) {
        this((AttachmentManager) controller);
    }

    /**
     * Deprecated: now uses Attachment Manager
     * 
     * @param controller
     * @param entityId
     * @param entityUUID
     */
    @Deprecated
    public VirtualEntity(MinecartMemberNetwork controller, int entityId, UUID entityUUID) {
        this((AttachmentManager) controller, entityId, entityUUID);
    }

    public VirtualEntity(AttachmentManager manager) {
        this(manager, EntityUtil.getUniqueEntityId(), UUID.randomUUID());
    }

    public VirtualEntity(AttachmentManager manager, int entityId, UUID entityUUID) {
        this.manager = manager;
        this.entityId = entityId;
        this.entityUUID = entityUUID;
        this.metaData = new DataWatcher();
        this.syncAbsX = this.syncAbsY = this.syncAbsZ = Double.NaN;
        this.velSyncAbsX = this.velSyncAbsY = this.velSyncAbsZ = Double.NaN;
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

    public double getPosX() {
        return this.liveAbsX;
    }

    public double getPosY() {
        return this.liveAbsY;
    }

    public double getPosZ() {
        return this.liveAbsZ;
    }

    public boolean isMountable() {
        switch (this.entityType) {
        case HORSE:
        case BOAT:
            return false;
        default:
            return true;
        }
    }

    public double getMountOffset() {
        if (this.entityType.name().contains("MINECART")) {
            return 0.3;
        }
        switch (this.entityType) {
        case HORSE:
            return 1.4;
        case BOAT:
            return 0.2;
        default:
            return 1.0;
        }
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

    public void setRelativeOffset(Vector offset) {
        this.relDx = offset.getX();
        this.relDy = offset.getY();
        this.relDz = offset.getZ();
    }

    public void setRelativeOffset(double dx, double dy, double dz) {
        this.relDx = dx;
        this.relDy = dy;
        this.relDz = dz;
    }

    public void setSyncMode(SyncMode mode) {
        this.syncMode = mode;
        if (mode == SyncMode.SEAT) {
            this.livePitch = this.syncPitch = 0.0f;
        }
    }

    public Vector getYawPitchRoll() {
        return this.yawPitchRoll;
    }

    public Vector getSyncPos() {
        return new Vector(this.syncAbsX, this.syncAbsY, this.syncAbsZ);
    }

    /**
     * Updates the position of the displayed part
     * 
     * @param transform relative to which the part should be positioned
     * @param yawPitchRoll rotation
     */
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
        liveAbsX = position.getX() + this.relDx;
        liveAbsY = position.getY() + this.relDy;
        liveAbsZ = position.getZ() + this.relDz;

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
        if (Double.isNaN(this.syncAbsX)) {
            this.refreshSyncPos();
        }

        // Calculate the velocity by comparing the last synchronized position with the live position
        // This should only be done when sound is enabled for the Minecart
        // Velocity is used exclusively for controlling the minecart's audio level
        // When derailed, no audio should be made. Otherwise, the velocity speed controls volume.
        // Only applies when used in a minecart member network environment
        liveVel = 0.0;
        if (this.entityTypeIsMinecart && this.manager instanceof MinecartMemberNetwork) {
            MinecartMember<?> member = ((MinecartMemberNetwork) manager).getMember();
            if (!member.isUnloaded() && member.getGroup().getProperties().isSoundEnabled() && !member.isDerailed()) {
                if (!Double.isNaN(velSyncAbsX)) {
                    liveVel = MathUtil.distance(liveAbsX, liveAbsY, liveAbsZ, velSyncAbsX, velSyncAbsY, velSyncAbsZ);
                }
                velSyncAbsX = liveAbsX;
                velSyncAbsY = liveAbsY;
                velSyncAbsZ = liveAbsZ;

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

    /**
     * Sets whether the controller entity metadata is used in place of this entity's metadata
     * 
     * @param use
     */
    public void setUseParentMetadata(boolean use) {
        this.useParentMetadata = use;
    }

    public void spawn(Player viewer, Vector motion) {
        // Destroy first if needed. Shouldn't happen, but just in case.
        if (this.viewers.contains(viewer)) {
            this.destroy(viewer);
        }
        this.viewers.add(viewer);

        this.sendSpawnPackets(viewer, motion);
    }

    private void sendSpawnPackets(Player viewer, Vector motion) {
        //motX = motY = motZ = 0.0;

        //System.out.println("SPAWN " + this.syncAbsX + "/" + this.syncAbsY + "/" + this.syncAbsZ + " ID=" + this.entityUUID);

        // Ensure we spawn with a little bit of movement when we are a seat
        if (NEEDS_UNSTUCK_VECTOR && this.syncMode == SyncMode.SEAT) {
            double xzls = (motion.getX() * motion.getX()) + (motion.getZ() * motion.getZ());
            if (xzls < (0.002 * 0.002)) {
                double y = motion.getY();
                motion = this.getUnstuckVector();
                motion.setY(y);
            }
        }

        // Create a spawn packet appropriate for the type of entity being spawned
        if (isLivingEntity()) {
            // Spawn living entity
            //Vector us_vector = (this.syncMode == SyncMode.SEAT) ? getUnstuckVector() : new Vector();
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(this.entityUUID);
            spawnPacket.setEntityType(this.entityType);
            spawnPacket.setPosX(this.syncAbsX - motion.getX());
            spawnPacket.setPosY(this.syncAbsY - motion.getY());
            spawnPacket.setPosZ(this.syncAbsZ - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(this.syncYaw);
            spawnPacket.setPitch(this.syncPitch);
            spawnPacket.setHeadYaw((this.syncMode == SyncMode.ITEM) ? 0.0f : this.syncYaw);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, getUsedMeta());
        } else {
            // Spawn entity (generic)
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(this.entityUUID);
            spawnPacket.setEntityType(this.entityType);
            spawnPacket.setPosX(this.syncAbsX - motion.getX());
            spawnPacket.setPosY(this.syncAbsY - motion.getY());
            spawnPacket.setPosZ(this.syncAbsZ - motion.getZ());
            spawnPacket.setMotX(motion.getX());
            spawnPacket.setMotY(motion.getY());
            spawnPacket.setMotZ(motion.getZ());
            spawnPacket.setYaw(this.syncYaw);
            spawnPacket.setPitch(this.syncPitch);
            PacketUtil.sendPacket(viewer, spawnPacket);

            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, getUsedMeta(), true);
            PacketUtil.sendPacket(viewer, metaPacket.toCommonPacket());
        }

        if (this.syncMode == SyncMode.SEAT) {
            PacketPlayOutRelEntityMoveLookHandle movePacket = PacketPlayOutRelEntityMoveLookHandle.createNew(
                    this.entityId,
                    motion.getX(), motion.getY(), motion.getZ(),
                    this.syncYaw,
                    this.syncPitch,
                    false);
            PacketUtil.sendPacket(viewer, movePacket);
        } else if (motion.lengthSquared() > 0.001) {
            CommonPacket movePacket = PacketType.OUT_ENTITY_MOVE.newInstance(this.entityId, motion.getX(), motion.getY(), motion.getZ(), false);
            PacketUtil.sendPacket(viewer, movePacket);
        }

        // Resend velocity if one is set
        if (this.syncVel > 0.0) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityVelocityHandle.createNew(this.entityId, this.syncVel, 0.0, 0.0));
        }
    }

    public void syncMetadata() {
        DataWatcher metaData = getUsedMeta();
        if (metaData.isChanged()) {
            broadcast(PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metaData, false));
        }
    }

    public void syncPosition(boolean absolute) {
        if (this.viewers.isEmpty()) {
            // No viewers. Assign live to sync right away.
            refreshSyncPos();
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
        double dx = (this.liveAbsX - this.syncAbsX);
        double dy = (this.liveAbsY - this.syncAbsY);
        double dz = (this.liveAbsZ - this.syncAbsZ);
        double abs_delta = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        boolean largeChange = (abs_delta > EntityNetworkController.MAX_RELATIVE_DISTANCE);

        // Detect a glitched pitch rotation, and perform a respawn then
        if (this.syncMode == SyncMode.NORMAL && this.syncPitch != this.livePitch && Util.isProtocolRotationGlitched(this.syncPitch, this.livePitch)) {
            for (Player viewer : this.viewers) {
                this.sendDestroyPackets(viewer);
            }
            this.refreshSyncPos();
            for (Player viewer : this.viewers) {
                this.sendSpawnPackets(viewer, largeChange ? new Vector() : new Vector(dx, dy, dz));
            }
            return;
        }

        // When an absolute update is required, send a teleport packet and refresh the synchronized position instantly
        if (absolute || largeChange) {
            broadcast(PacketPlayOutEntityTeleportHandle.createNew(this.entityId, this.liveAbsX, this.liveAbsY, this.liveAbsZ, this.liveYaw, this.livePitch, false));
            refreshSyncPos();
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
            rotateCtr = 14;
            rotated = true;
        } else if (rotateCtr > 0) {
            rotateCtr--;
            rotated = true;
        }

        // Refresh head rotation first
        if (rotatedNow) {
            this.refreshHeadRotation();
        }

        if (moved && rotated) {
            // Position and rotation changed
            PacketPlayOutRelEntityMoveLookHandle packet = PacketPlayOutRelEntityMoveLookHandle.createNew(
                    this.entityId,
                    dx, dy, dz,
                    this.liveYaw,
                    this.livePitch,
                    false);

            this.syncYaw = this.liveYaw;
            this.syncPitch = this.livePitch;
            this.syncAbsX += packet.getDeltaX();
            this.syncAbsY += packet.getDeltaY();
            this.syncAbsZ += packet.getDeltaZ();
            broadcast(packet);
        } else if (moved) {
            // Only position changed
            PacketPlayOutRelEntityMoveHandle packet = PacketPlayOutRelEntityMoveHandle.createNew(
                    this.entityId,
                    dx, dy, dz,
                    false);

            this.syncAbsX += packet.getDeltaX();
            this.syncAbsY += packet.getDeltaY();
            this.syncAbsZ += packet.getDeltaZ();
            broadcast(packet);
        } else if (rotated) {
            if (NEEDS_UNSTUCK_VECTOR && this.syncMode == SyncMode.SEAT && rotatedNow) {
                // Send a very small movement change to correct rotation in a pulse
                Vector v = getUnstuckVector();
                PacketPlayOutRelEntityMoveLookHandle packet = PacketPlayOutRelEntityMoveLookHandle.createNew(
                        this.entityId,
                        v.getX(), 0.0, v.getZ(),
                        this.liveYaw,
                        this.livePitch,
                        false);
                this.syncYaw = this.liveYaw;
                this.syncPitch = this.livePitch;
                this.syncAbsX += packet.getDeltaX();
                this.syncAbsY += packet.getDeltaY();
                this.syncAbsZ += packet.getDeltaZ();
                broadcast(packet);
            } else {
                // Only rotation changed
                if (IS_LOOK_PACKET_BUGGED) {
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

                    this.syncYaw = this.liveYaw;
                    this.syncPitch = this.livePitch;
                    broadcast(packet);
                } else {
                    PacketPlayOutEntityLookHandle packet = PacketPlayOutEntityLookHandle.createNew(
                            this.entityId,
                            this.liveYaw,
                            this.livePitch,
                            false);

                    this.syncYaw = this.liveYaw;
                    this.syncPitch = this.livePitch;
                    broadcast(packet);
                }
            }
        }
    }

    private void refreshHeadRotation() {
        // Refresh head rotation first
        if (this.syncMode == SyncMode.NORMAL && isLivingEntity()) {
            CommonPacket packet = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this.entityId);
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, this.liveYaw);
            broadcast(packet);
        }
    }

    private boolean isLivingEntity() {
        Class<?> entityClass = this.entityType.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }

    // this vector is used to fix up the rotation of passengers in seats
    // by moving a very tiny amount (and back), the rotation is 'unstuck'
    private Vector getUnstuckVector() {
        double yawRad = Math.toRadians(this.liveYaw);
        double unstuck_dx = 0.002 * -Math.sin(yawRad);
        double unstuck_dz = 0.002 * Math.cos(yawRad);
        return new Vector(unstuck_dx, 0.0, unstuck_dz);
    }

    private void refreshSyncPos() {
        this.syncAbsX = this.liveAbsX;
        this.syncAbsY = this.liveAbsY;
        this.syncAbsZ = this.liveAbsZ;
        this.syncYaw = this.liveYaw;
        this.syncPitch = this.livePitch;
        this.syncVel = this.liveVel;
    }

    public void destroy(Player viewer) {
        this.viewers.remove(viewer);
        this.sendDestroyPackets(viewer);
        PlayerUtil.getVehicleMountController(viewer).remove(this.entityId);
    }

    private void sendDestroyPackets(Player viewer) {
        if (this.syncVel > 0.0) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, new Vector()));
        }
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);
    }

    public void broadcast(CommonPacket packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    public void broadcast(PacketHandle packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    private DataWatcher getUsedMeta() {
        if (this.useParentMetadata && this.manager instanceof MinecartMemberNetwork) {
            return ((MinecartMemberNetwork) this.manager).getEntity().getMetaData();
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
        ITEM,
        NORMAL,
        SEAT
    }
}
