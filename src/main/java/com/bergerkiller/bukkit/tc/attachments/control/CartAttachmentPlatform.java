package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

public class CartAttachmentPlatform extends CartAttachment {
    private VirtualEntity actual;
    private VirtualEntity entity;

    @Override
    public void onDetached() {
        super.onDetached();
        this.entity = null;
        this.actual = null;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.actual = new VirtualEntity(this.getManager());
        this.actual.setEntityType(EntityType.SHULKER);
        this.actual.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        updateMeta();

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.CHICKEN);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.setRelativeOffset(0.0, -0.32, 0.0);
    }

    public void updateMeta() {
        //this.actual.getMetaData().set(EntityShulkerHandle.DATA_FACE_DIRECTION, DebugUtil.getVariable("a", BlockFace.NORTH).value);
        //this.actual.getMetaData().set(EntityShulkerHandle.DATA_AP, DebugUtil.getIntVectorValue("b", IntVector3.ZERO));
        //this.actual.getMetaData().set(EntityShulkerHandle.DATA_PEEK, DebugUtil.getVariable("c", 0).value.byteValue());
        //this.actual.getMetaData().set(EntityShulkerHandle.DATA_COLOR, DebugUtil.getVariable("d", 0).value.byteValue());
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        if (this.entity.isMountable()) {
            return this.entity.getEntityId();
        } else {
            return -1;
        }
    }

    @Override
    public void applyDefaultSeatTransform(Matrix4x4 transform) {
        transform.translate(0.0, 1.0, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        actual.spawn(viewer, new Vector());
        entity.spawn(viewer, new Vector());
        this.getManager().getPassengerController(viewer).mount(entity.getEntityId(), actual.getEntityId());
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        this.getManager().getPassengerController(viewer).unmount(entity.getEntityId(), actual.getEntityId());
        actual.destroy(viewer);
        entity.destroy(viewer);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        // Vector old_p = (this.last_transform == null) ? this.transform.toVector() : this.last_transform.toVector();

        this.entity.updatePosition(transform);
        this.actual.updatePosition(transform);

        // This attempts to move players along as a test
        /*
        Vector new_p = this.transform.toVector();
        double change = DebugUtil.getDoubleValue("a", 1.405) * (new_p.getY() - old_p.getY());
        if (change != 0.0) {
            for (Player player : this.controller.getViewers()) {
                Vector vel = player.getVelocity();
                if (vel.getY() < change) {
                    double sf = DebugUtil.getDoubleValue("b", 1.405);
                    vel.setX(vel.getX() * sf);
                    vel.setZ(vel.getZ() * sf);
                    vel.setY(change);
                    player.setVelocity(vel);
                }
            }
        }
        */
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
        this.actual.syncPosition(absolute);
    }

    @Override
    public void onTick() {
        /*
        updateMeta();
        PacketPlayOutEntityMetadataHandle p = PacketPlayOutEntityMetadataHandle.createNew(this.actual.getEntityId(), this.actual.getMetaData(), false);
        for (Player v : this.controller.getViewers()) {
            PacketUtil.sendPacket(v, p);
        }
        */
    }

}
