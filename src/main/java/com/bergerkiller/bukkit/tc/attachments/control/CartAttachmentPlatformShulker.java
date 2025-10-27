package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntityShulkerHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

/**
 * Used when no size is configured ("Shulker mode")
 */
public class CartAttachmentPlatformShulker extends CartAttachmentPlatform {
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
        this.actual.getMetaData().setClientByteDefault(EntityShulkerHandle.DATA_COLOR, Color.DEFAULT.ordinal());

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.CHICKEN);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.setRelativeOffset(0.0, -0.32, 0.0);
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        Color color = config.getOrDefault("shulkerColor", Color.DEFAULT);
        this.actual.getMetaData().set(EntityShulkerHandle.DATA_COLOR, (byte) color.ordinal());
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        // Switches between attachment implementation class
        if (readPlatformMode(config) != PlatformMode.SHULKER) {
            return false;
        }

        return true;
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity.getEntityId() == entityId ||
                this.actual.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return this.actual.getEntityId();
    }

    @Override
    public void applyPassengerSeatTransform(Matrix4x4 transform) {
        Matrix4x4 relativeMatrix = new Matrix4x4();
        relativeMatrix.translate(0.0, 1.0, 0.0);
        Matrix4x4.multiply(relativeMatrix, transform, transform);
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        // Send entity spawn packet
        actual.spawn(viewer, new Vector());
        entity.spawn(viewer, new Vector());
        viewer.getVehicleMountController().mount(entity.getEntityId(), actual.getEntityId());
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        // Send entity destroy packet
        actual.destroy(viewer);
        entity.destroy(viewer);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
        this.actual.updatePosition(transform);
        this.actual.syncMetadata();
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);

        // Must not send move packets of the mounted shulker. This causes glitches since MC 1.17
        this.actual.syncPositionSilent();
    }
}
