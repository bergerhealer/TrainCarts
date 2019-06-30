package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class CartAttachmentText extends CartAttachment {
    private VirtualEntity entity;

    @Override
    public void onAttached() {
        super.onAttached();
        String text = this.getConfig().get("text", " ");
        if (text.length() == 0) {
            text = " ";
        }

        this.entity = new VirtualEntity(this.getManager());

        this.entity.setEntityType(EntityType.ARMOR_STAND);
        // this.entity.setSyncMode(VirtualEntity.SyncMode.SEAT);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
        this.entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, ChatText.fromMessage(text));
        this.entity.setRelativeOffset(0, -1.6, 0);
    }

    @Override
    public void onTick() {

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
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
    }

    @Override
    public void makeVisible(Player viewer) {
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
    }

    @Override
    public void makeHidden(Player viewer) {
        entity.destroy(viewer);
    }

}
