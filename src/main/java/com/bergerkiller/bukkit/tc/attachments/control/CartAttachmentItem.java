package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

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

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(1, menu -> new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    for (ItemTransformType type : ItemTransformType.values()) {
                        this.addItem(type.toString());
                    }
                    this.setSelectedItem(menu.getPositionConfigValue("transform", ItemTransformType.HEAD).toString());
                }

                @Override
                public void onSelectedItemChanged() {
                    menu.updatePositionConfigValue("transform", ItemTransformType.get(getSelectedItem()).name());
                }
            }.setBounds(25, 0, menu.getSliderWidth(), 11))
                    .addLabel(0, 3, "Mode");
        }
    };

    private VirtualArmorStandItemEntity entity;

    @Override
    public void onAttached() {
        super.onAttached();
        this.entity = new VirtualArmorStandItemEntity(this.getManager());
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
        this.entity.setItem(newTransformType, newItem);
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
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));

        // Apply focus color
        if (this.isFocused()) {
            this.updateGlowColorFor(this.entity.getEntityUUID(), HelperMethods.getFocusGlowColor(this), viewer.getPlayer());
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        // Send entity destroy packet
        this.entity.destroy(viewer);

        // Undo focus color
        this.updateGlowColorFor(this.entity.getEntityUUID(), null, viewer.getPlayer());
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
        this.entity.updatePosition(transform);

        // Sync right now! Not only when moving!
        // This is a slow method due to packet constructor, so send the packet async
        this.entity.syncMetadata();
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

}
