package com.bergerkiller.bukkit.tc.attachments.particle;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

/**
 * Helper class for spawning and updating a virtual arrow item.
 * This is an item held by an invisible armor stand, pointing
 * in a given orientation.
 */
public class VirtualArrowItem {
    private int entityId;
    private boolean glowing = false;
    private double posX, posY, posZ;
    private Vector rotation;
    private ItemStack item;

    private VirtualArrowItem(int entityId) {
        this.entityId = entityId;
    }

    /**
     * Initializes a new VirtualArrowItem state
     * 
     * @param entityId The entity id of the item, -1 if a new one is being spawned
     * @return VirtualArrowItem
     */
    public static VirtualArrowItem create(int entityId) {
        return new VirtualArrowItem(entityId);
    }

    public boolean hasEntityId() {
        return this.entityId != -1;
    }

    public VirtualArrowItem glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualArrowItem item(ItemStack item) {
        this.item = item;
        return this;
    }

    public VirtualArrowItem position(DoubleOctree.Entry<?> position, Quaternion orientation) {
        // Use direction for rotX/rotZ, and up vector for rotY rotation around it
        // This creates an arrow that smoothly rotates around its center point using rotY
        this.rotation = Util.getArmorStandPose(orientation);
        this.rotation.setX(this.rotation.getX() - 90.0);

        // Absolute position
        this.posX = position.getX() + 0.315;
        this.posY = position.getY() - 1.35;
        this.posZ = position.getZ();

        // Cancel relative positioning of the item itself
        Vector upVector =  new Vector(0.05, -0.05, -0.56);
        orientation.transformPoint(upVector);
        this.posX += upVector.getX();
        this.posY += upVector.getY();
        this.posZ += upVector.getZ();
        return this;
    }

    public VirtualArrowItem position(Vector position, Quaternion orientation) {
        // Use direction for rotX/rotZ, and up vector for rotY rotation around it
        // This creates an arrow that smoothly rotates around its center point using rotY
        this.rotation = Util.getArmorStandPose(orientation);
        this.rotation.setX(this.rotation.getX() - 90.0);

        // Absolute position
        this.posX = position.getX() + 0.315;
        this.posY = position.getY() - 1.35;
        this.posZ = position.getZ();

        // Cancel relative positioning of the item itself
        Vector upVector =  new Vector(0.05, -0.05, -0.56);
        orientation.transformPoint(upVector);
        this.posX += upVector.getX();
        this.posY += upVector.getY();
        this.posZ += upVector.getZ();
        return this;
    }

    /**
     * Refreshes the position and orientation of this virtual arrow item.
     * Only the position property has to be set.
     * 
     * @param viewers Players to which to send the move and pose packets
     * @return this
     */
    public VirtualArrowItem move(Iterable<Player> viewers) {
        if (this.entityId != -1) {
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.posX,  this.posY,  this.posZ,
                    0.0f, 0.0f, false);

            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, this.rotation);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);

            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, tpPacket);
                PacketUtil.sendPacket(viewer, metaPacket);
            }
        }
        return this;
    }

    /**
     * Refreshes the item of this virtual arrow item.
     * Only the item property has to be set.
     * 
     * @param viewer Player to which to send the update packets
     * @return this
     */
    public VirtualArrowItem updateItem(Player viewer) {
        if (this.entityId != -1) {
            PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                    this.entityId, EquipmentSlot.HAND, this.item);
            PacketUtil.sendPacket(viewer, equipPacket);
        }
        return this;
    }

    /**
     * Refreshes the glowing state of this virtual arrow item.
     * Only the glowing property has to be set.
     * 
     * @param viewer Player to which to send the update packets
     * @return this
     */
    public VirtualArrowItem updateGlowing(Player viewer) {
        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
            metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_HAS_ARMS | EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
            if (this.glowing) {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
            }
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        }
        return this;
    }

    /**
     * Spawns the entity.
     * All properties must be set.
     * 
     * @param viewer Player to which to send the spawn packets
     * @return entity id of the spawned entity (generated if -1 in {@link #create(entityId)})
     */
    public int spawn(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }
        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.ARMOR_STAND);
        spawnPacket.setPosX(posX);
        spawnPacket.setPosY(posY);
        spawnPacket.setPosZ(posZ);
        PacketUtil.sendPacket(viewer, spawnPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
        metadata.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE);
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
        metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_HAS_ARMS | EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
        if (glowing) {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        }
        metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, rotation);
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.item);
        PacketUtil.sendPacket(viewer, equipPacket);

        return this.entityId;
    }

    /**
     * Destroys the virtual arrow item (if spawned and entity id was not set to -1).
     * No properties have to be set.
     * 
     * @param viewer Player to which to send destroy packets
     */
    public void destroy(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
    }
}