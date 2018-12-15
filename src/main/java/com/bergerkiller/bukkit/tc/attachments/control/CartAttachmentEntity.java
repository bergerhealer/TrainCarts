package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

/**
 * A cart attachment that is a standard Entity.
 * This is also used for Vanilla style minecarts.
 */
public class CartAttachmentEntity extends CartAttachment {
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

        EntityType entityType = config.get("entityType", EntityType.MINECART);
        if (this.parent != null || !VirtualEntity.isMinecart(entityType)) {
            // Generate entity (UU)ID
            this.entity = new VirtualEntity(this.controller);
        } else {
            // Root Minecart node - allow the same Entity Id as the minecart to be used
            this.entity = new VirtualEntity(this.controller, this.controller.getEntity().getEntityId(), this.controller.getEntity().getUniqueId());
            this.entity.setUseParentMetadata(true);
        }
        this.entity.setEntityType(entityType);

        // Minecarts have a 'strange' rotation point - fix it!
        if (VirtualEntity.isMinecart(entityType)) {
            final double MINECART_CENTER_Y = 0.3765;
            this.entity.setPosition(new Vector(0.0, MINECART_CENTER_Y, 0.0));
            this.entity.setRelativeOffset(0.0, -MINECART_CENTER_Y, 0.0);
        }

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        if (entityType.name().equals("SHULKER")) {
            this.actual = this.entity;
            this.entity = new VirtualEntity(this.controller);
            this.entity.setEntityType(EntityType.CHICKEN);
            this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.entity.setRelativeOffset(0.0, -0.32, 0.0);
        }
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
    public Vector getMountEntityOffset() {
        return this.entity.getMountOffset();
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        if (actual != null) {
            actual.spawn(viewer, new Vector());
        }
        entity.spawn(viewer, new Vector());
        if (actual != null) {
            this.controller.getPassengerController(viewer).mount(entity.getEntityId(), actual.getEntityId());
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        if (actual != null) {
            this.controller.getPassengerController(viewer).unmount(entity.getEntityId(), actual.getEntityId());
            actual.destroy(viewer);
        }
        entity.destroy(viewer);
    }

    @Override
    public void onPositionUpdate() {
        super.onPositionUpdate();
        this.entity.updatePosition(this.transform);
        if (this.actual != null) {
            this.actual.updatePosition(this.transform);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
        if (this.actual != null) {
            this.actual.syncPosition(absolute);
        }
    }

    @Override
    public void onTick() {
    }

}
