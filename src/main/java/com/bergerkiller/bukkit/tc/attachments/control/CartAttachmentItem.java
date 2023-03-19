package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.config.transform.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetItemTransformTypeSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
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
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

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
            PositionMenu.Row transformRow = builder.addRow(1, menu -> new MapWidgetItemTransformTypeSelector() {
                @Override
                public void onAttached() {
                    this.setSelectedType(ItemTransformType.deserialize(menu.getPositionConfig(), "transform"));
                    super.onAttached();
                }

                @Override
                public void onSelectedTypeChanged(ItemTransformType type) {
                    menu.updatePositionConfig(config -> config.set("transform", type.serializedName()));
                    for (MapWidget widget : getParent().getWidgets()) {
                        if (widget instanceof ScaleWidget) {
                            widget.setEnabled(type.category() == ItemTransformType.Category.DISPLAY);
                        }
                    }
                }
            }.setBounds(25, 0, menu.getSliderWidth(), MapWidgetItemTransformTypeSelector.defaultHeight()));
            transformRow.addLabel(0, 3, "Mode");
            if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
                transformRow.addLabel(0, 15, "Tr.form");
            }

            // On 1.19.4+ show the full size x/y/z axis.
            if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
                builder.addRow(menu -> (new ScaleWidget(menu))
                                .setBounds(25, 0, menu.getSliderWidth(), 35)
                        )
                        .addLabel(0, 3, "Size X")
                        .addLabel(0, 15, "Size Y")
                        .addLabel(0, 27, "Size Z")
                        .setSpacingAbove(3);
            }
        }
    };

    private static class ScaleWidget extends MapWidgetSizeBox {
        private final PositionMenu menu;

        public ScaleWidget(PositionMenu menu) {
            this.menu = menu;
        }

        @Override
        public void onAttached() {
            super.onAttached();

            setSize(menu.getPositionConfigValue("sizeX", 1.0),
                    menu.getPositionConfigValue("sizeY", 1.0),
                    menu.getPositionConfigValue("sizeZ", 1.0));

            setEnabled(ItemTransformType.deserialize(menu.getPositionConfig(), "transform")
                    .category() == ItemTransformType.Category.DISPLAY);
        }

        @Override
        public void onSizeChanged() {
            menu.updatePositionConfig(config -> {
                if (x.getValue() == 1.0 && y.getValue() == 1.0 && z.getValue() == 1.0) {
                    config.remove("sizeX");
                    config.remove("sizeY");
                    config.remove("sizeZ");
                } else {
                    config.set("sizeX", x.getValue());
                    config.set("sizeY", y.getValue());
                    config.set("sizeZ", z.getValue());
                }
            });
        }
    }

    private VirtualSpawnableObject entity;

    @Override
    public void onAttached() {
        super.onAttached();

        ItemTransformType type = ItemTransformType.deserialize(getConfig(), "position.transform");
        this.entity = type.create(this.getManager(), null);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.entity = null;
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        // If display entities are used, check whether the use of it changes
        // Some modes can't be displayed using the display entity, like armor slots
        ItemTransformType type = ItemTransformType.deserialize(config, "position.transform");
        if (!type.canUpdate(entity)) {
            return false;
        }

        return true;
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        super.onLoad(config);

        // New settings
        ItemStack newItem = config.get("item", ItemStack.class);
        ItemTransformType type = ItemTransformType.deserialize(config, "position.transform");
        type.update(entity, newItem);
        if (entity instanceof VirtualDisplayItemEntity) {
            ((VirtualDisplayItemEntity) entity).setScale(getConfiguredPosition().size);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.containsEntityId(entityId);
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
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        entity.destroy(viewer);
    }

    @Override
    public void onFocus() {
        entity.setGlowColor(HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        entity.setGlowColor(null);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        entity.updatePosition(transform);
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
        entity.syncPosition(absolute);
    }
}
