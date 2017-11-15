package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;

/**
 * A cart attachment that is a standard Entity.
 * This is also used for Vanilla style minecarts.
 */
public class CartAttachmentEntity extends CartAttachment {
    private VirtualEntity entity;

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
        }
        this.entity.setEntityType(entityType);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return this.entity.getEntityId();
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        entity.destroy(viewer);
    }

    @Override
    public void onDetached() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onPositionUpdate() {
        super.onPositionUpdate();
        this.entity.updatePosition(this.transform);
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void onTick() {
    }

}
