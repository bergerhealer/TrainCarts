package com.bergerkiller.bukkit.tc.attachments;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Wraps the logic for updating a virtual item held inside an ArmorStand.
 * The yaw rotation is automatically smoothed.
 */
public class VirtualArmorStandItemEntity extends VirtualEntity {
    private ItemTransformType transformType;
    private ItemStack item;
    private Quaternion last_rot;

    public VirtualArmorStandItemEntity(AttachmentManager manager) {
        super(manager);
        this.setEntityType(EntityType.ARMOR_STAND);

        // By default. NORMAL and NORMAL_MINECART_FIX are also supported!
        // Then it also sends correct head yaw information, so that spectating the
        // armorstand works as expected.
        this.setSyncMode(SyncMode.ITEM);

        this.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_HAS_ARMS, true);

        this.transformType = ItemTransformType.HEAD;
        this.item = null;
        this.last_rot = null;
    }

    public ItemStack getItem() {
        return item;
    }

    public ItemTransformType getTransformType() {
        return transformType;
    }

    public void setItem(ItemTransformType transformType, ItemStack item) {
        if (!LogicUtil.bothNullOrEqual(item, this.item) || this.transformType != transformType) {
            if (this.item != null) {
                this.broadcast(this.transformType.createEquipmentPacket(this.getEntityId(), null));
            }
            this.transformType = transformType;
            this.item = item;
            if (this.item != null) {
                this.broadcast(this.transformType.createEquipmentPacket(this.getEntityId(), this.item));
            }
            this.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL, this.transformType.isSmall());
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        updatePosition(transform, transform.getRotation());
    }

    public void updatePosition(Matrix4x4 transform, Quaternion rotation) {
        Vector new_entity_ypr;

        // Detect changes in yaw that we can apply to the entity directly
        // The remainder or 'error' is applied to the pose of the model
        double yaw_change;
        if (last_rot != null) {
            Quaternion changes = rotation.clone();
            changes.divide(last_rot);
            yaw_change = Util.fastGetRotationYaw(changes);
        } else {
            yaw_change = 0.0;
        }
        last_rot = rotation;

        // Apply when the yaw change isn't too extreme (does not cause a flip) and has a significant change
        new_entity_ypr = this.getYawPitchRoll().clone();
        new_entity_ypr.setY(Util.getNextEntityYaw((float) new_entity_ypr.getY(), yaw_change));

        updateArmorStandPosition(transform, new_entity_ypr, rotation);
    }

    @Override
    public void updatePosition(Matrix4x4 transform, Vector yawPitchRoll) {
        updateArmorStandPosition(transform, yawPitchRoll, transform.getRotation());
    }

    private void updateArmorStandPosition(Matrix4x4 transform, Vector entityYawPitchRoll, Quaternion poseOrientation) {
        // Subtract rotation of Entity (keep protocol error into account)
        Quaternion q = new Quaternion();
        q.rotateY(entityYawPitchRoll.getY());
        poseOrientation = Quaternion.multiply(q, poseOrientation);

        // Adjust relative offset of the armorstand entity to take shoulder angle into account
        // This doesn't apply for head, and only matters for the left/right hand
        // This ensures any further positioning is relative to the base of the shoulder controlled
        double hor_offset = this.transformType.getHorizontalOffset();
        double ver_offset = this.transformType.getVerticalOffset();
        Vector original_offset = super.getRelativeOffset().clone();
        if (hor_offset != 0.0) {
            this.addRelativeOffset(
                    -hor_offset * Math.cos(Math.toRadians(entityYawPitchRoll.getY())),
                    -ver_offset,
                    -hor_offset * Math.sin(Math.toRadians(entityYawPitchRoll.getY())));
        } else {
            this.addRelativeOffset(0.0, -ver_offset, 0.0);
        }

        // Apply the transform to the entity position and pose of the model
        super.updatePosition(transform, entityYawPitchRoll);

        // Restore this
        this.setRelativeOffset(original_offset);

        Vector rotation = Util.getArmorStandPose(poseOrientation);
        DataWatcher meta = this.getMetaData();
        if (this.transformType.isHead()) {
            meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, rotation);
        } else if (this.transformType == ItemTransformType.CHEST) {
            meta.set(EntityArmorStandHandle.DATA_POSE_BODY, rotation);
        } else if (this.transformType.isLeftHand()) {
            rotation.setX(rotation.getX() - 90.0);
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_LEFT, rotation);
        } else if (this.transformType.isRightHand()) {
            rotation.setX(rotation.getX() - 90.0);
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, rotation);
        } else if (this.transformType.isLeg()) {
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_LEFT, rotation);
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_RIGHT, rotation);
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        super.sendSpawnPackets(viewer, motion);

        // Set equipment
        if (this.item != null) {
            viewer.send(this.transformType.createEquipmentPacket(this.getEntityId(), this.item));
        }
    }
}
