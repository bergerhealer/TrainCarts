package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
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

        EntityType entityType = this.getConfig().get("entityType", EntityType.MINECART);

        // Some entity types cannot be spawned, use placeholder
        if (!isEntityTypeSupported(entityType)) {
            entityType = EntityType.MINECART;
        }

        if (this.getParent() != null || !VirtualEntity.isMinecart(entityType)) {
            // Generate entity (UU)ID
            this.entity = new VirtualEntity(this.getManager());
        } else {
            // Root Minecart node - allow the same Entity Id as the minecart to be used
            this.entity = new VirtualEntity(this.getManager(), this.getController().getEntity().getEntityId(), this.getController().getEntity().getUniqueId());
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
            this.entity = new VirtualEntity(this.getManager());
            this.entity.setEntityType(EntityType.CHICKEN);
            this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.entity.setRelativeOffset(0.0, -0.32, 0.0);
        }
    }

    @Override
    public void onFocus() {
        this.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        this.updateGlowColor(this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        this.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, false);

        // Leave entity registered under the glow color to prevent flickering of white
        // this.resetGlowColor(this.entity.getEntityUUID());
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
        transform.translate(0.0, this.entity.getMountOffset(), 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        if (actual != null) {
            actual.spawn(viewer, new Vector());
        }
        entity.spawn(viewer, new Vector());
        if (actual != null) {
            this.getManager().getPassengerController(viewer).mount(entity.getEntityId(), actual.getEntityId());
        }

        // Apply focus color
        if (this.isFocused()) {
            TrainCarts.plugin.getGlowColorTeamProvider().update(viewer,
                    this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this));
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        if (actual != null) {
            this.getManager().getPassengerController(viewer).unmount(entity.getEntityId(), actual.getEntityId());
            actual.destroy(viewer);
        }
        entity.destroy(viewer);

        // Undo focus color
        if (this.isFocused()) {
            TrainCarts.plugin.getGlowColorTeamProvider().reset(viewer, this.entity.getEntityUUID());
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
        if (this.actual != null) {
            this.actual.updatePosition(transform);
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

    /**
     * Whether a particular entity type can be used at all in an entity attachment
     * 
     * @param entityType
     * @return True if supported
     */
    public static boolean isEntityTypeSupported(EntityType entityType) {
        String name = entityType.name();
        if (name.equals("WEATHER") || name.equals("COMPLEX_PART")) {
            return false;
        }

        switch (entityType) {
        case PAINTING:
        case FISHING_HOOK:
        case LIGHTNING:
        case PLAYER:
        case EXPERIENCE_ORB:
        case UNKNOWN:
            return false;
        default:
            return true;
        }
    }
}
