package com.bergerkiller.bukkit.tc.attachments;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
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
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol
 */
public class VirtualEntity {
    private final MinecartMemberNetwork controller;
    private final int entityId;
    private final UUID entityUUID;
    private final DataWatcher metaData;
    private double posX, posY, posZ;
    private double liveAbsX, liveAbsY, liveAbsZ;
    private double syncAbsX, syncAbsY, syncAbsZ;
    private float liveYaw, livePitch;
    private float syncYaw, syncPitch;
    private double liveVel;
    private double syncVel;
    private double relDx, relDy, relDz;
    private EntityType entityType = EntityType.CHICKEN;
    private int rotateCtr = 0;
    private boolean hasRotation = true;
    private boolean cancelUnmountLogic = false;

    public VirtualEntity(MinecartMemberNetwork controller) {
        this(controller, EntityUtil.getUniqueEntityId(), UUID.randomUUID());
    }

    public VirtualEntity(MinecartMemberNetwork controller, int entityId, UUID entityUUID) {
        this.controller = controller;
        this.entityId = entityId;
        this.entityUUID = entityUUID;
        this.metaData = new DataWatcher();
        this.syncAbsX = this.syncAbsY = this.syncAbsZ = Double.NaN;
        this.syncVel = 0.0;
    }

    public DataWatcher getMetaData() {
        return this.metaData;
    }

    public int getEntityId() {
        return this.entityId;
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
    
    public void setHasRotation(boolean rotation) {
        this.hasRotation = rotation;
        if (!rotation) {
            this.livePitch = this.syncPitch = 0.0f;
            this.liveYaw = this.syncYaw = 0.0f;
        }
    }
    
    /**
     * Updates the position of the displayed part
     * 
     * @param transform relative to which the part should be positioned
     */
    public void updatePosition(Matrix4x4 transform) {
        Vector3 v = new Vector3(this.posX, this.posY, this.posZ);
        transform.transformPoint(v);

        liveAbsX = v.x + this.relDx;
        liveAbsY = v.y + this.relDy;
        liveAbsZ = v.z + this.relDz;

        if (this.hasRotation) {
            Vector rotation = transform.getYawPitchRoll();
            liveYaw = (float) rotation.getY();
            if (hasPitch(this.entityType)) {
                livePitch = (float) rotation.getX();
            } else {
                livePitch = 0.0f;
            }
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
            if (member.getGroup().getProperties().isSoundEnabled() && !member.isDerailed()) {
                liveVel = MathUtil.distance(liveAbsX, liveAbsY, liveAbsZ, syncAbsX, syncAbsY, syncAbsZ);

                // Limit to a maximum of 1.0, above this it's kind of pointless
                if (liveVel > 1.0) liveVel = 1.0;
            }
        }
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public void spawn(Player viewer, Vector motion) {
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
        if (this.entityType == EntityType.MINECART) {
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
        
        // Create a spawn packet appropriate for the type of entity being spawned
        CommonPacket packet;
        if (LivingEntity.class.isAssignableFrom(this.entityType.getEntityClass())) {
            // Spawn living entity
            packet = PacketType.OUT_ENTITY_SPAWN_LIVING.newInstance();
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityId, this.entityId);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityUUID, this.entityUUID);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityType, entitySpawnId);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posX, this.syncAbsX - motion.getX());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posY, this.syncAbsY - motion.getY());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posZ, this.syncAbsZ - motion.getZ());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.motX, motion.getX());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.motY, motion.getY());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.motZ, motion.getZ());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.dataWatcher, this.metaData);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.yaw, this.syncYaw);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.pitch, this.syncPitch);
        } else {
            // Spawn entity (generic)
            packet = PacketType.OUT_ENTITY_SPAWN.newInstance();
            packet.write(PacketType.OUT_ENTITY_SPAWN.entityId, this.entityId);
            packet.write(PacketType.OUT_ENTITY_SPAWN.UUID, this.entityUUID);
            packet.write(PacketType.OUT_ENTITY_SPAWN.entityType, entitySpawnId);
            packet.write(PacketType.OUT_ENTITY_SPAWN.posX, this.syncAbsX - motion.getX());
            packet.write(PacketType.OUT_ENTITY_SPAWN.posY, this.syncAbsY - motion.getY());
            packet.write(PacketType.OUT_ENTITY_SPAWN.posZ, this.syncAbsZ - motion.getZ());
            packet.write(PacketType.OUT_ENTITY_SPAWN.motX, motion.getX());
            packet.write(PacketType.OUT_ENTITY_SPAWN.motY, motion.getY());
            packet.write(PacketType.OUT_ENTITY_SPAWN.motZ, motion.getZ());
            packet.write(PacketType.OUT_ENTITY_SPAWN.yaw, this.syncYaw);
            packet.write(PacketType.OUT_ENTITY_SPAWN.pitch, this.syncPitch);
            packet.write(PacketType.OUT_ENTITY_SPAWN.extraData, entitySpawnExtraData);
        }

        PacketUtil.sendPacket(viewer, packet);

        this.controller.getPassengerController(viewer).resend(this.entityId);

        packet = PacketType.OUT_ENTITY_MOVE.newInstance(this.entityId, motion.getX(), motion.getY(), motion.getZ(), false);
        PacketUtil.sendPacket(viewer, packet);

        // Resend velocity if one is set
        if (this.syncVel > 0.0) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, this.syncVel, 0.0, 0.0));
        }
    }

    public void syncPosition(boolean absolute) {
        Collection<Player> viewers = this.controller.getViewers();
        if (viewers.isEmpty()) {
            // No viewers. Assign live to sync right away.
            refreshSyncPos();
            return;
        }

        // Synchronize velocity
        // Minecraft does not play minecart audio for the Y-axis. To make sound on vertical rails,
        // we instead apply the vector length to just the X-axis so that this works.
        // Velocity packets are only relevant when minecarts are used (with audio enabled)
        if (Math.abs(this.liveVel - this.syncVel) > 0.01) {
            this.syncVel = this.liveVel;
            CommonPacket packet = PacketType.OUT_ENTITY_VELOCITY.newInstance(this.entityId, this.syncVel, 0.0, 0.0);
            controller.broadcast(packet);
        }

        // Live motion. Check if the distance change is too large.
        double dx = (this.liveAbsX - this.syncAbsX);
        double dy = (this.liveAbsY - this.syncAbsY);
        double dz = (this.liveAbsZ - this.syncAbsZ);
        boolean largeChange = (Math.abs(dx) > EntityNetworkController.MAX_RELATIVE_DISTANCE) ||
                              (Math.abs(dy) > EntityNetworkController.MAX_RELATIVE_DISTANCE) ||
                              (Math.abs(dz) > EntityNetworkController.MAX_RELATIVE_DISTANCE);

        // Detect a glitched pitch rotation, and perform a respawn then
        if (hasPitch(this.entityType) && Util.isProtocolRotationGlitched(this.syncPitch, this.livePitch)) {
            this.cancelUnmountLogic = true;
            for (Player viewer : viewers) {
                this.destroy(viewer);
            }
            this.cancelUnmountLogic = false;
            this.refreshSyncPos();
            for (Player viewer : viewers) {
                this.spawn(viewer, largeChange ? new Vector() : new Vector(dx, dy, dz));
            }
            return;
        }

        // When an absolute update is required, send a teleport packet and refresh the synchronized position instantly
        if (absolute || largeChange) {
            controller.broadcast(PacketType.OUT_ENTITY_TELEPORT.newInstance(this.entityId, this.liveAbsX, this.liveAbsY, this.liveAbsZ, this.liveYaw, this.livePitch, false));
            refreshSyncPos();
            return;
        }

        // Check for changes in position
        boolean moved = (Math.abs(dx) > EntityNetworkController.MIN_RELATIVE_POS_CHANGE) ||
                        (Math.abs(dy) > EntityNetworkController.MIN_RELATIVE_POS_CHANGE) ||
                        (Math.abs(dz) > EntityNetworkController.MIN_RELATIVE_POS_CHANGE);
        
        // Check for changes in rotation
        boolean rotated = (EntityTrackerEntryHandle.getProtocolRotation(this.liveYaw) != EntityTrackerEntryHandle.getProtocolRotation(this.syncYaw)) ||
                          (EntityTrackerEntryHandle.getProtocolRotation(this.livePitch) != EntityTrackerEntryHandle.getProtocolRotation(this.syncPitch));
        // Remember the rotation change for X more ticks. This prevents partial rotation on the client.
        if (rotated) {
            rotateCtr = 14;
        } else if (rotateCtr > 0) {
            rotateCtr--;
            rotated = true;
        }

        if (moved && rotated) {
            // Position and rotation changed
            CommonPacket packet = PacketType.OUT_ENTITY_MOVE_LOOK.newInstance(this.entityId, 
                    (this.liveAbsX - this.syncAbsX),
                    (this.liveAbsY - this.syncAbsY),
                    (this.liveAbsZ - this.syncAbsZ),
                    this.liveYaw,
                    this.livePitch, false);
            this.syncYaw = this.liveYaw;
            this.syncPitch = this.livePitch;
            this.syncAbsX += packet.read(PacketType.OUT_ENTITY_MOVE_LOOK.dx);
            this.syncAbsY += packet.read(PacketType.OUT_ENTITY_MOVE_LOOK.dy);
            this.syncAbsZ += packet.read(PacketType.OUT_ENTITY_MOVE_LOOK.dz);
            controller.broadcast(packet);
        } else if (moved) {
            // Only position changed
            CommonPacket packet = PacketType.OUT_ENTITY_MOVE.newInstance(this.entityId,
                    (this.liveAbsX - this.syncAbsX),
                    (this.liveAbsY - this.syncAbsY),
                    (this.liveAbsZ - this.syncAbsZ),
                    false);
            this.syncAbsX += packet.read(PacketType.OUT_ENTITY_MOVE.dx);
            this.syncAbsY += packet.read(PacketType.OUT_ENTITY_MOVE.dy);
            this.syncAbsZ += packet.read(PacketType.OUT_ENTITY_MOVE.dz);
            controller.broadcast(packet);
        } else if (rotated) {
            // Only rotation changed
            CommonPacket packet = PacketType.OUT_ENTITY_LOOK.newInstance(this.entityId,
                    this.liveYaw, this.livePitch, false);
            this.syncYaw = this.liveYaw;
            this.syncPitch = this.livePitch;
            controller.broadcast(packet);
        }
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
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);
        if (!this.cancelUnmountLogic) {
            this.controller.getPassengerController(viewer).remove(this.entityId, false);
        }
    }

    private static boolean hasVelocityPacket(EntityType entityType) {
        return isMinecart(entityType);
    }
    
    private static boolean hasPitch(EntityType entityType) {
        return isMinecart(entityType);
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
}
