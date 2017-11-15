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
        this.entity = this.generateVirtualEntity();
        this.entity.setEntityType(config.get("entityType", EntityType.MINECART));
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
