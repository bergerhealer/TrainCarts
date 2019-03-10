package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;

public class CartAttachmentItem extends CartAttachment {
    private VirtualEntity entity;
    private ItemStack item;
    private ItemTransformType transformType;
    private Quaternion last_rot = null;

    @Override
    public void onAttached() {
        super.onAttached();

        this.item = this.config.get("item", ItemStack.class);

        if (this.config.isNode("position")) {
            this.transformType = this.config.get("position.transform", ItemTransformType.HEAD);
        } else {
            this.transformType = ItemTransformType.HEAD;
        }

        this.entity = new VirtualEntity(this.controller);
        this.entity.setEntityType(EntityType.ARMOR_STAND);
        this.entity.setSyncMode(SyncMode.ITEM);

        if (this.transformType.isSmall()) {
            this.entity.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) EntityArmorStandHandle.DATA_FLAG_IS_SMALL);
        }

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
                    this.entity.getEntityId(), this.transformType.getSlot(), this.item);
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
        // Switch to old logic for debugging the pivot point changes in 1.12.2-v3
        /*
        if (this.getController().getMember().getProperties().getTags().contains("old")) {
            this.onPositionUpdate_legacy();
            return;
        }
        */

        final boolean DEBUG_POSE = false;

        // Debug mode makes models look at the viewer to test orientation
        Quaternion q_rotation;
        if (DEBUG_POSE) {
            Vector dir = new Vector(0, 0, 1);
            for (Player p : Bukkit.getOnlinePlayers()) {
                dir = p.getEyeLocation().toVector().subtract(this.transform.toVector());
                break;
            }
            dir = new Vector(1, 0, 0);
            q_rotation = Quaternion.fromLookDirection(dir, new Vector(0,1,0)); //entity_transform.getRotation();
            q_rotation = Quaternion.multiply(Quaternion.fromAxisAngles(dir, DebugUtil.getDoubleValue("roll", 0.0)), q_rotation);
        } else {
            q_rotation = this.transform.getRotation();
        }

        // Detect changes in yaw that we can apply to the entity directly
        // The remainder or 'error' is applied to the pose of the model
        double yaw_change;
        if (last_rot != null) {
            Quaternion changes = last_rot.clone();
            changes.invert();
            changes.multiply(q_rotation);
            yaw_change = Util.fastGetRotationYaw(changes);
        } else {
            yaw_change = 0.0;
        }
        last_rot = q_rotation;

        // Apply when the yaw change isn't too extreme (does not cause a flip) and has a significant change
        Vector new_entity_ypr = this.entity.getYawPitchRoll().clone();
        int prot_yaw_rot_old = EntityTrackerEntryHandle.getProtocolRotation((float) new_entity_ypr.getY());
        int prot_yaw_rot_new = prot_yaw_rot_old;
        if (yaw_change >= -90.0 && yaw_change <= 90.0) {
            prot_yaw_rot_new = EntityTrackerEntryHandle.getProtocolRotation((float) (new_entity_ypr.getY() + yaw_change));
            if (prot_yaw_rot_new != prot_yaw_rot_old) {

                // Do not change entity yaw to beyond the angle requested
                // This causes the pose yaw angle to compensate, which looks very twitchy
                double new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
                double new_yaw_change = (new_yaw - new_entity_ypr.getY());
                if (yaw_change < 0.0) {
                    if (new_yaw_change < yaw_change) {
                        prot_yaw_rot_new++;
                        new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
                    }
                } else {
                    if (new_yaw_change > yaw_change) {
                        prot_yaw_rot_new--;
                        new_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
                    }
                }

                // Has a change in protocol yaw value, accept the changes
                new_entity_ypr.setY(new_yaw);
            }
        }

        // Subtract rotation of Entity (keep protocol error into account)
        double entity_yaw = EntityTrackerEntryHandle.getRotationFromProtocol(prot_yaw_rot_new);
        Quaternion q = new Quaternion();
        q.rotateY(entity_yaw);
        q_rotation = Quaternion.multiply(q, q_rotation);

        // Adjust relative offset of the armorstand entity to take shoulder angle into account
        // This doesn't apply for head, and only matters for the left/right hand
        // This ensures any further positioning is relative to the base of the shoulder controlled
        double hor_offset = this.transformType.getHorizontalOffset();
        double ver_offset = this.transformType.getVerticalOffset();
        if (hor_offset != 0.0) {
            this.entity.setRelativeOffset(
                    -hor_offset * Math.cos(Math.toRadians(entity_yaw)),
                    -ver_offset,
                    -hor_offset * Math.sin(Math.toRadians(entity_yaw)));
        } else {
            this.entity.setRelativeOffset(0.0, -ver_offset, 0.0);
        }

        // Apply the transform to the entity position and pose of the model
        this.entity.updatePosition(this.transform, new_entity_ypr);

        Vector rotation = Util.getArmorStandPose(q_rotation);
        DataWatcher meta = this.entity.getMetaData();
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

        // Sync right now! Not only when moving!
        this.entity.syncMetadata();
    }

    public final void onPositionUpdate_legacy() {
        this.entity.setRelativeOffset(0.0, -1.2, 0.0);

        // Perform additional translation for certain attached pose positions
        // This correct model offsets
        if (this.transformType == ItemTransformType.LEFT_HAND) {
            Matrix4x4 tmp = this.transform.clone();
            tmp.translate(-0.4, 0.3, 0.9375);
            tmp.multiply(this.position.transform);
            Vector ypr = tmp.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(tmp, ypr);
            super.onPositionUpdate();
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            Matrix4x4 tmp = this.transform.clone();
            tmp.translate(-0.4, 0.3, -0.9375);
            tmp.multiply(this.position.transform);
            Vector ypr = tmp.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(tmp, ypr);
            super.onPositionUpdate();
        } else {
            super.onPositionUpdate();
            Vector ypr = this.transform.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(this.transform, ypr);
        }

        // Convert the pitch/roll into an appropriate pose
        Vector in_ypr = this.entity.getYawPitchRoll();

        Vector rotation;
        if (this.transformType == ItemTransformType.LEFT_HAND) {
            rotation = new Vector(180.0, -90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            rotation = new Vector(0.0, 90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        } else {
            rotation = new Vector(90.0, 90.0 + in_ypr.getZ(), 90.0 - in_ypr.getX());
        }

        DataWatcher meta = this.entity.getMetaData();
        if (this.transformType == ItemTransformType.HEAD) {
            meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, rotation);
        } else if (this.transformType == ItemTransformType.CHEST) {
            meta.set(EntityArmorStandHandle.DATA_POSE_BODY, rotation);
        } else if (this.transformType == ItemTransformType.LEFT_HAND) {
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_LEFT, rotation);
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            meta.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, rotation);
        } else if (this.transformType == ItemTransformType.LEGS || this.transformType == ItemTransformType.FEET) {
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_LEFT, rotation);
            meta.set(EntityArmorStandHandle.DATA_POSE_LEG_RIGHT, rotation);
        }

        // Sync right now! Not only when moving!
        this.entity.syncMetadata();
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void onTick() {
    }

}
