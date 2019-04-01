package com.bergerkiller.bukkit.tc.attachments;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutEntityLookHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle.PacketPlayOutRelEntityMoveLookHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol.
 * The entity can be spawned or destroyed for individual players.
 */
public class VirtualEntity {
    private final MinecartMemberNetwork controller;
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metaData;
    private double posX, posY, posZ;
    private double liveAbsX, liveAbsY, liveAbsZ;
    private double syncAbsX, syncAbsY, syncAbsZ;
    private double velSyncAbsX, velSyncAbsY, velSyncAbsZ;
    private float liveYaw, livePitch;
    private float syncYaw, syncPitch;
    private double liveVel;
    private double syncVel;
    private double relDx, relDy, relDz;
    private EntityType entityType = EntityType.CHICKEN;
    private int rotateCtr = 0;
    private SyncMode syncMode = SyncMode.NORMAL;
    private boolean cancelUnmountLogic = false;
    private boolean useParentMetadata = false;
    private final ArrayList<Player> viewers = new ArrayList<Player>();
    private Vector yawPitchRoll = new Vector(0.0, 0.0, 0.0);

    public VirtualEntity(MinecartMemberNetwork controller) {
        this(controller, EntityUtil.getUniqueEntityId(), UUID.randomUUID());
    }

    public VirtualEntity(MinecartMemberNetwork controller, int entityId, UUID entityUUID) {
        this.controller = controller;
        this.entityId = entityId;
        this.entityUUID = entityUUID;
        this.metaData = new DataWatcher();
        this.syncAbsX = this.syncAbsY = this.syncAbsZ = Double.NaN;
        this.velSyncAbsX = this.velSyncAbsY = this.velSyncAbsZ = Double.NaN;
        this.syncVel = 0.0;
    }

    public DataWatcher getMetaData() {
        return this.metaData;
    }

    public int getEntityId() {
        return this.entityId;
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
        Vector3 v = new Vector3(this.posX, this.posY, this.posZ);
        transform.transformPoint(v);

        liveAbsX = v.x + this.relDx;
        liveAbsY = v.y + this.relDy;
        liveAbsZ = v.z + this.relDz;

        this.yawPitchRoll = yawPitchRoll;
        this.liveYaw = (float) this.yawPitchRoll.getY();
        if (this.syncMode != SyncMode.SEAT && this.hasPitch()) {
            livePitch = (float) this.yawPitchRoll.getX();
        } else {
            livePitch = 0.0f;
        }
        if (isMinecart(this.entityType)) {
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
        liveVel = 0.0;
        if (hasVelocityPacket(this.entityType)) {
            MinecartMember<?> member = controller.getMember();
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

        //motX = motY = motZ = 0.0;

        //System.out.println("SPAWN " + this.syncAbsX + "/" + this.syncAbsY + "/" + this.syncAbsZ + " ID=" + this.entityUUID);

        // Figure out what the Id is of the entity we spawn
        // Extradata is depending on the entity type
        // For minecarts, it defines the type of minecart spawned:
        //     RIDEABLE(0, "MinecartRideable"),
        //     CHEST(1, "MinecartChest"), 
        //     FURNACE(2, "MinecartFurnace"), 
        //     TNT(3, "MinecartTNT"), 
        //     SPAWNER(4, "MinecartSpawner"), 
        //     HOPPER(5, "MinecartHopper"), 
        //     COMMAND_BLOCK(6, "MinecartCommandBlock");
        //TODO: Make this less cancerous.
        int entitySpawnId = (int) this.entityType.getTypeId();
        int entitySpawnExtraData = 0;
        if (this.entityType == EntityType.BOAT) {
            entitySpawnId = 1;
        } else if (this.entityType == EntityType.MINECART) {
            entitySpawnId = 10; entitySpawnExtraData = 0;
        } else if (this.entityType == EntityType.MINECART_CHEST) {
            entitySpawnId = 10; entitySpawnExtraData = 1;
        } else if (this.entityType == EntityType.MINECART_FURNACE) {
            entitySpawnId = 10; entitySpawnExtraData = 2;
        } else if (this.entityType == EntityType.MINECART_TNT) {
            entitySpawnId = 10; entitySpawnExtraData = 3;
        } else if (this.entityType == EntityType.MINECART_MOB_SPAWNER) {
            entitySpawnId = 10; entitySpawnExtraData = 4;
        } else if (this.entityType == EntityType.MINECART_HOPPER) {
            entitySpawnId = 10; entitySpawnExtraData = 5;
        } else if (this.entityType == EntityType.MINECART_COMMAND) {
            entitySpawnId = 10; entitySpawnExtraData = 6;
        }

        // Ensure we spawn with a little bit of movement when we are a seat
        if (this.syncMode == SyncMode.SEAT) {
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
            spawnPacket.setDataWatcher(this.metaData);
            spawnPacket.setYaw(this.syncYaw);
            spawnPacket.setPitch(this.syncPitch);
            spawnPacket.setHeadYaw((this.syncMode == SyncMode.ITEM) ? 0.0f : this.syncYaw);
            PacketUtil.sendPacket(viewer, spawnPacket);
        } else {
            // Spawn entity (generic)
            CommonPacket spawnPacket = PacketType.OUT_ENTITY_SPAWN.newInstance();
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.entityId, this.entityId);
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.UUID, this.entityUUID);
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.entityType, entitySpawnId);
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.posX, this.syncAbsX - motion.getX());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.posY, this.syncAbsY - motion.getY());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.posZ, this.syncAbsZ - motion.getZ());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.motX, motion.getX());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.motY, motion.getY());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.motZ, motion.getZ());
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.yaw, this.syncYaw);
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.pitch, this.syncPitch);
            spawnPacket.write(PacketType.OUT_ENTITY_SPAWN.extraData, entitySpawnExtraData);
            PacketUtil.sendPacket(viewer, spawnPacket);
        }

        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, getUsedMeta(), true);
        PacketUtil.sendPacket(viewer, metaPacket.toCommonPacket());
        
        this.controller.getPassengerController(viewer).resend(this.entityId);

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
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, this.syncVel, 0.0, 0.0));
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

            CommonPacket packet = PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, this.syncVel, 0.0, 0.0);
            broadcast(packet);
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
            this.cancelUnmountLogic = true;
            ArrayList<Player> old_viewers = new ArrayList<Player>(this.viewers);
            for (Player viewer : old_viewers) {
                this.destroy(viewer);
            }
            this.cancelUnmountLogic = false;
            this.refreshSyncPos();
            for (Player viewer : old_viewers) {
                this.spawn(viewer, largeChange ? new Vector() : new Vector(dx, dy, dz));
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

        // Check for changes in position
        moved = (abs_delta > EntityNetworkController.MIN_RELATIVE_POS_CHANGE);

        // Check for changes in rotation
        rotatedNow = EntityTrackerEntryHandle.hasProtocolRotationChanged(this.liveYaw, this.syncYaw) ||
                     EntityTrackerEntryHandle.hasProtocolRotationChanged(this.livePitch, this.syncPitch);

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
            if (this.syncMode == SyncMode.SEAT && rotatedNow) {
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

    private void refreshHeadRotation() {
        // Refresh head rotation first
        if (this.syncMode == SyncMode.NORMAL && isLivingEntity()) {
            CommonPacket packet = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this.entityId);
            packet.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) EntityTrackerEntryHandle.getProtocolRotation(this.liveYaw));
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
        if (this.syncVel > 0.0) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, new Vector()));
        }
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);
        if (!this.cancelUnmountLogic) {
            this.controller.getPassengerController(viewer).remove(this.entityId, false);
        }
    }

    private void broadcast(CommonPacket packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    private void broadcast(PacketHandle packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    private DataWatcher getUsedMeta() {
        return this.useParentMetadata ? this.controller.getEntity().getMetaData() : this.metaData;
    }

    private static boolean hasVelocityPacket(EntityType entityType) {
        return isMinecart(entityType);
    }

    private boolean hasPitch() {
        return isLivingEntity() || isMinecart(this.entityType);
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
