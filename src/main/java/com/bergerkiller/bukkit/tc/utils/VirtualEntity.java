package com.bergerkiller.bukkit.tc.utils;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol
 */
public class VirtualEntity {
    private final int entityId;
    private final DataWatcher metaData;
    private double posX, posY, posZ;
    private int[] passengers = new int[0];

    public VirtualEntity() {
        this(EntityUtil.getUniqueEntityId());
    }

    public VirtualEntity(int entityId) {
        this.entityId = entityId;
        this.metaData = new DataWatcher();
    }

    public DataWatcher getMetaData() {
        return this.metaData;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setPassengers(int... passengerEntityIds) {
        this.passengers = passengerEntityIds;
    }

    public void spawn(Player viewer) {
        CommonPacket packet = PacketType.OUT_ENTITY_SPAWN_LIVING.newInstance();
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityId, this.entityId);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityUUID, UUID.randomUUID());
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityType, (int) EntityType.CHICKEN.getTypeId());
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posX, this.posX);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posY, this.posY);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posZ, this.posZ);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.dataWatcher, this.metaData);
        PacketUtil.sendPacket(viewer, packet);

        PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.entityId, this.passengers);
        PacketUtil.sendPacket(viewer, mount);
    }

    public void destroy(Player viewer) {
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);
    }
}
