package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * An invisible entity that is spectated. The player sees nobody sitting in the
 * seat he is occupying.
 */
public class FirstPersonSpectatedEntityInvisible extends FirstPersonSpectatedEntity {
    private VirtualEntity entity;

    public FirstPersonSpectatedEntityInvisible(CartAttachmentSeat seat, FirstPersonViewSpectator view, Player player) {
        super(seat, view, player);
    }

    @Override
    public void start(Matrix4x4 baseTransform) {
        this.entity = new VirtualEntity(seat.getManager());
        this.entity.setEntityType(EntityType.ARMOR_STAND);
        this.entity.setSyncMode(SyncMode.NORMAL);
        this.entity.setPosition(new Vector(0.0, FirstPersonViewMode.INVISIBLE.getVirtualOffset(), 0.0));

        // We spectate an invisible armorstand that has MARKER set
        // This causes the spectator to view from 0/0/0, avoiding having to do any extra offsets
        this.entity.updatePosition(baseTransform);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        this.entity.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
        this.entity.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        this.entity.syncPosition(true);

        // Spawn it
        this.entity.spawn(player, seat.calcMotion());

        // Spectate
        spectate(this.entity.getEntityId());
    }

    @Override
    public void stop() {
        spectate(-1);
        this.entity.destroy(player);
    }

    @Override
    public void updatePosition(Matrix4x4 baseTransform) {
        entity.updatePosition(baseTransform);
    }

    @Override
    public void syncPosition(boolean absolute) {
        entity.syncPosition(absolute);
    }
}
