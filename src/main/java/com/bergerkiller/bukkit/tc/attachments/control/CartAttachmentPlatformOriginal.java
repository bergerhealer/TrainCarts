package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

public class CartAttachmentPlatformOriginal extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "PLATFORM";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/platform.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentPlatformOriginal();
        }
    };

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

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.CHICKEN);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.setRelativeOffset(0.0, -0.32, 0.0);
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
    @Deprecated
    public void makeVisible(Player player) {
        makeVisible(getManager().asAttachmentViewer(player));
    }

    @Override
    @Deprecated
    public void makeHidden(Player player) {
        makeHidden(getManager().asAttachmentViewer(player));
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
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);

        // Must not send move packets of the mounted shulker. This causes glitches since MC 1.17
        this.actual.syncPositionSilent();
    }

    @Override
    public void onTick() {
    }

}