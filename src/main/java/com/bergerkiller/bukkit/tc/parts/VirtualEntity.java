package com.bergerkiller.bukkit.tc.parts;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.map.util.Matrix4f;
import com.bergerkiller.bukkit.common.map.util.Vector3f;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;

/**
 * Represents a single Virtual entity, that only exists for clients using packet protocol
 */
public class VirtualEntity implements DisplayedPart {
    private final int entityId;
    private final DataWatcher metaData;
    private double posX, posY, posZ;
    private double liveAbsX, liveAbsY, liveAbsZ;
    private double syncAbsX, syncAbsY, syncAbsZ;
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

    /**
     * Sets the relative position of this Entity
     * 
     * @param x
     * @param y
     * @param z
     */
    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    @Override
    public void updatePosition(Matrix4f transform) {
        Vector3f v = new Vector3f((float) this.posX, (float) this.posY, (float) this.posZ);
        transform.transformPoint(v);

        liveAbsX = v.x;
        liveAbsY = v.y - 1.32;
        liveAbsZ = v.z;

        //TODO: Also transform children of this part
    }

    public void setPassengers(int... passengerEntityIds) {
        this.passengers = passengerEntityIds;
    }

    public void spawn(Player viewer) {
        CommonPacket packet = PacketType.OUT_ENTITY_SPAWN_LIVING.newInstance();
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityId, this.entityId);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityUUID, UUID.randomUUID());
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityType, (int) EntityType.CHICKEN.getTypeId());
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posX, this.syncAbsX);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posY, this.syncAbsY);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posZ, this.syncAbsZ);
        packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.dataWatcher, this.metaData);
        PacketUtil.sendPacket(viewer, packet);

        PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.entityId, this.passengers);
        PacketUtil.sendPacket(viewer, mount);
    }

    public void syncPosition(Collection<Player> viewers, boolean absolute) {
        if (!viewers.isEmpty()) {
            CommonPacket packet;
            if (absolute) {
                packet = PacketType.OUT_ENTITY_TELEPORT.newInstance(this.entityId, this.liveAbsX, this.liveAbsY, this.liveAbsZ, 0.0f, 0.0f, false);
            } else {
                packet = PacketType.OUT_ENTITY_MOVE.newInstance(this.entityId, 
                        (this.liveAbsX - this.syncAbsX),
                        (this.liveAbsY - this.syncAbsY),
                        (this.liveAbsZ - this.syncAbsZ), false);
            }
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }

        this.syncAbsX = this.liveAbsX;
        this.syncAbsY = this.liveAbsY;
        this.syncAbsZ = this.liveAbsZ;
    }

    public void destroy(Player viewer) {
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);
    }

}
