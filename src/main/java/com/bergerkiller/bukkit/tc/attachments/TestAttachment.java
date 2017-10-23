package com.bergerkiller.bukkit.tc.attachments;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

public class TestAttachment implements CartAttachment {
    private CartAttachmentOwner owner;
    private final Set<Player> _viewers = new HashSet<Player>();
    private VirtualEntity entity = null;

    public TestAttachment(CartAttachmentOwner owner) {
        this.owner = owner;
    }

    @Override
    public void onTick() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSyncAtt(boolean absolute) {
        // TODO Auto-generated method stub
        if (entity != null) {
            Matrix4x4 m = owner.getTransform(true);

            entity.updatePosition(m);
            entity.syncPosition(this._viewers, absolute);

            //m.transformPoint(p);
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityArmorStandHandle.DATA_POSE_HEAD, m.getYawPitchRoll());
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(entity.getEntityId(), metaTmp, true);
            for (Player viewer : this._viewers) {
                PacketUtil.sendPacket(viewer, metaPacket);
            }
        }
    }

    @Override
    public boolean addViewer(Player viewer) {
        if (!this._viewers.add(viewer)) {
            return false;
        }
        
        if (entity == null) {
            entity = new VirtualEntity();
            entity.setEntityType(EntityType.ARMOR_STAND);
            entity.setRelativeOffset(0.0, -1.2, 0.0);
            entity.setPosition(new Vector(0.35, 0.1, 0.0));
            entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            entity.updatePosition(owner.getTransform(false));
            entity.syncPosition(Collections.emptyList(), true);
        }
        
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
        
        PacketPlayOutEntityEquipmentHandle equipment = PacketPlayOutEntityEquipmentHandle.createNew(
                entity.getEntityId(),
                EquipmentSlot.HEAD,
                new ItemStack(Material.DIAMOND_SWORD, 1, (short) 1));
        PacketUtil.sendPacket(viewer, equipment);

        return true;
    }

    @Override
    public boolean removeViewer(Player viewer) {
        if (!this._viewers.remove(viewer)) {
            return false;
        }

        entity.destroy(viewer);
        return true;
    }

}
