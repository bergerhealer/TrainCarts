package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.entity.MapWidgetEntityTypeList;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

/**
 * A cart attachment that is a standard Entity.
 * This is also used for Vanilla style minecarts.
 */
public class CartAttachmentEntity extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "ENTITY";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            EntityType type = config.get("entityType", EntityType.MINECART);
            if (type == EntityType.MINECART) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_CHEST) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_STORAGE_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_COMMAND) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_COMMAND_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_FURNACE) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_POWERED_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_HOPPER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_HOPPER_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_MOB_SPAWNER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MOB_SPAWNER")), 16, 16);
            } else if (type == EntityType.MINECART_TNT) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_EXPLOSIVE_MINECART")), 16, 16);
            } else {
                return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/mob.png");
            }
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentEntity();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("entityType", EntityType.MINECART);
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            tab.addWidget(new MapWidgetEntityTypeList() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setEntityType(attachment.getConfig().get("entityType", EntityType.MINECART));
                }

                @Override
                public void onEntityTypeChanged() {
                    attachment.getConfig().set("entityType", this.getEntityType());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                }
            }).setBounds(0, 0, 100, 11);
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
        // this.updateGlowColor(this.entity.getEntityUUID(), null);
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
            PlayerUtil.getVehicleMountController(viewer).mount(entity.getEntityId(), actual.getEntityId());
        }

        // Apply focus color
        if (this.isFocused()) {
            this.updateGlowColorFor(this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this), viewer);
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        // Send entity destroy packet
        if (actual != null) {
            actual.destroy(viewer);
        }
        entity.destroy(viewer);

        // Undo focus color
        this.updateGlowColorFor(this.entity.getEntityUUID(), null, viewer);
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
