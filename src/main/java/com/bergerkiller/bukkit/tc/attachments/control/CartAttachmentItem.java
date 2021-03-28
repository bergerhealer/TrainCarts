package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

public class CartAttachmentItem extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "ITEM";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            ItemStack item = config.get("item", new ItemStack(Material.MINECART));
            return TCConfig.resourcePack.getItemTexture(item, 16, 16);
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentItem();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("item", new ItemStack(getMaterial("LEGACY_WOOD")));
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            tab.addWidget(new MapWidgetItemSelector() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setSelectedItem(attachment.getConfig().get("item", new ItemStack(Material.PUMPKIN)));
                }

                @Override
                public void onSelectedItemChanged() {
                    attachment.getConfig().set("item", this.getSelectedItem());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                    attachment.resetIcon();
                }
            });
        }
    };

    private VirtualEntity entity;
    private ItemStack item;
    private ItemTransformType transformType;
    private Quaternion last_rot = null;

    @Override
    public void onAttached() {
        super.onAttached();

        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.ARMOR_STAND);
        this.entity.setSyncMode(SyncMode.ITEM);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_HAS_ARMS, true);
        this.transformType = ItemTransformType.HEAD;
    }

    @Override
    public void onDetached() {
        super.onDetached();

        this.entity = null;
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        super.onLoad(config);

        // New settings
        ItemStack newItem = config.get("item", ItemStack.class);
        ItemTransformType newTransformType;
        if (config.isNode("position")) {
            newTransformType = config.get("position.transform", ItemTransformType.HEAD);
        } else {
            newTransformType = ItemTransformType.HEAD;
        }

        // Check changed and resend packets if so
        if (!LogicUtil.bothNullOrEqual(newItem, this.item) || this.transformType != newTransformType) {
            if (this.item != null) {
                this.entity.broadcast(this.transformType.createEquipmentPacket(this.entity.getEntityId(), null));
            }
            this.transformType = newTransformType;
            this.item = newItem;
            if (this.item != null) {
                this.entity.broadcast(this.transformType.createEquipmentPacket(this.entity.getEntityId(), this.item));
            }
            this.entity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL, this.transformType.isSmall());
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return -1;
    }

    @Override
    public void makeVisible(Player viewer) {
        // Send entity spawn packet
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));

        // Set pose information before making equipment visible
        //PacketUtil.sendPacket(viewer, getPosePacket());

        // Set equipment
        if (this.item != null) {
            PacketUtil.sendPacket(viewer, this.transformType.createEquipmentPacket(this.entity.getEntityId(), this.item));
        }

        // Apply focus color
        if (this.isFocused()) {
            this.updateGlowColorFor(this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this), viewer);
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        this.entity.destroy(viewer);

        // Undo focus color
        this.updateGlowColorFor(this.entity.getEntityUUID(), null, viewer);
    }

    @Override
    public void onFocus() {
        this.entity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER, true);

        this.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING | EntityHandle.DATA_FLAG_ON_FIRE, true);
        this.entity.syncMetadata();
        this.updateGlowColor(this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        this.entity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER, false);

        this.entity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING | EntityHandle.DATA_FLAG_ON_FIRE, false);
        this.entity.syncMetadata();

        // Leave entity registered under the glow color to prevent flickering of white
        // this.updateGlowColor(this.entity.getEntityUUID(), null);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
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
                dir = p.getEyeLocation().toVector().subtract(transform.toVector());
                break;
            }
            dir = new Vector(1, 0, 0);
            q_rotation = Quaternion.fromLookDirection(dir, new Vector(0,1,0)); //entity_transform.getRotation();
            q_rotation = Quaternion.multiply(Quaternion.fromAxisAngles(dir, DebugUtil.getDoubleValue("roll", 0.0)), q_rotation);
        } else {
            q_rotation = transform.getRotation();
        }

        // Detect changes in yaw that we can apply to the entity directly
        // The remainder or 'error' is applied to the pose of the model
        double yaw_change;
        if (last_rot != null) {
            Quaternion changes = q_rotation.clone();
            changes.divide(last_rot);
            yaw_change = Util.fastGetRotationYaw(changes);
        } else {
            yaw_change = 0.0;
        }
        last_rot = q_rotation;

        // Apply when the yaw change isn't too extreme (does not cause a flip) and has a significant change
        Vector new_entity_ypr = this.entity.getYawPitchRoll().clone();
        new_entity_ypr.setY(Util.getNextEntityYaw((float) new_entity_ypr.getY(), yaw_change));

        // Subtract rotation of Entity (keep protocol error into account)
        Quaternion q = new Quaternion();
        q.rotateY(new_entity_ypr.getY());
        q_rotation = Quaternion.multiply(q, q_rotation);

        // Adjust relative offset of the armorstand entity to take shoulder angle into account
        // This doesn't apply for head, and only matters for the left/right hand
        // This ensures any further positioning is relative to the base of the shoulder controlled
        double hor_offset = this.transformType.getHorizontalOffset();
        double ver_offset = this.transformType.getVerticalOffset();
        if (hor_offset != 0.0) {
            this.entity.setRelativeOffset(
                    -hor_offset * Math.cos(Math.toRadians(new_entity_ypr.getY())),
                    -ver_offset,
                    -hor_offset * Math.sin(Math.toRadians(new_entity_ypr.getY())));
        } else {
            this.entity.setRelativeOffset(0.0, -ver_offset, 0.0);
        }

        // Apply the transform to the entity position and pose of the model
        this.entity.updatePosition(transform, new_entity_ypr);

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
    }

    public final void onTransformChanged_legacy(Matrix4x4 transform) {
        this.entity.setRelativeOffset(0.0, -1.2, 0.0);

        // Perform additional translation for certain attached pose positions
        // This correct model offsets
        if (this.transformType == ItemTransformType.LEFT_HAND) {
            Matrix4x4 tmp = transform.clone();
            tmp.translate(-0.4, 0.3, 0.9375);
            tmp.multiply(this.getConfiguredPosition().transform);
            Vector ypr = tmp.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(tmp, ypr);
            super.onTransformChanged(transform);
        } else if (this.transformType == ItemTransformType.RIGHT_HAND) {
            Matrix4x4 tmp = transform.clone();
            tmp.translate(-0.4, 0.3, -0.9375);
            tmp.multiply(this.getConfiguredPosition().transform);
            Vector ypr = tmp.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(tmp, ypr);
            super.onTransformChanged(transform);
        } else {
            super.onTransformChanged(transform);
            Vector ypr = transform.getYawPitchRoll();
            ypr.setY(MathUtil.round(ypr.getY() - 90.0, 8));
            this.entity.updatePosition(transform, ypr);
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
    }

    @Override
    public void onTick() {
        // Sync right now! Not only when moving!
        this.entity.syncMetadata();
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

}
