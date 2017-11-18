package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;

public class CartAttachmentItem extends CartAttachment {
    private VirtualEntity entity;
    private ItemStack item;

    @Override
    public void onAttached() {
        super.onAttached();

        this.item = this.config.get("item", ItemStack.class);

        this.entity = new VirtualEntity(this.controller);
        this.entity.setEntityType(EntityType.ARMOR_STAND);
        this.entity.setHasRotation(false);
        this.entity.setRelativeOffset(0.0, -1.2, 0.0);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
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

        // Set pose information before making equipment visible
        //PacketUtil.sendPacket(viewer, getPosePacket());

        // Set equipment
        if (this.item != null) {
            PacketPlayOutEntityEquipmentHandle equipment = PacketPlayOutEntityEquipmentHandle.createNew(
                    this.entity.getEntityId(), EquipmentSlot.HEAD, this.item);
            PacketUtil.sendPacket(viewer, equipment);
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        entity.destroy(viewer);
    }

    @Override
    public void onPositionUpdate() {
        super.onPositionUpdate();
        this.entity.updatePosition(this.transform);
        this.entity.getMetaData().set(EntityArmorStandHandle.DATA_POSE_HEAD, this.transform.getYawPitchRoll());
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void onTick() {
    }

}
