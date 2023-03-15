package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
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
    private static final double SMALL_ITEM_SCALE = 0.5;
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
        public void migrateConfiguration(ConfigurationNode config) {
            ConfigurationNode position = config.getNode("position");
            Object old_transform = position.get("transform");
            if (old_transform != null) {
                String s = old_transform.toString();
                if (s.startsWith("SMALL_")) {
                    // Migrate to without SMALL_ prefix, apply size instead
                    position.set("sizeX", SMALL_ITEM_SCALE);
                    position.set("sizeY", SMALL_ITEM_SCALE);
                    position.set("sizeZ", SMALL_ITEM_SCALE);
                    position.set("transform", s.substring(6));
                }
            }
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

            if (Common.evaluateMCVersion(">=", "1.19.4")) {
                // On 1.19.4+ show the full size x/y/z coordinates.
                builder.addRow(menu -> new MapWidgetSizeBox() {
                    @Override
                    public void onAttached() {
                        super.onAttached();

                        setSize(menu.getPositionConfigValue("sizeX", 1.0),
                                menu.getPositionConfigValue("sizeY", 1.0),
                                menu.getPositionConfigValue("sizeZ", 1.0));
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
                }.setBounds(25, 0, menu.getSliderWidth(), 35))
                        .addLabel(0, 3, "Size X")
                        .addLabel(0, 15, "Size Y")
                        .addLabel(0, 27, "Size Z")
                        .setSpacingAbove(3);
            } else {
                // On older versions, only "BABY" and "NORMAL" modes are available. They're still
                // saved using the same size field though.
                builder.addRow(menu -> new MapWidgetSelectionBox() {
                    @Override
                    public void onAttached() {
                        addItem("normal");
                        addItem("small");
                        double sizeavg = ( menu.getPositionConfigValue("sizeX", 1.0) +
                                           menu.getPositionConfigValue("sizeY", 1.0) +
                                           menu.getPositionConfigValue("sizeZ", 1.0) ) / 3.0;
                        System.out.println("SIZE: " + sizeavg);
                        setSelectedIndex(sizeavg >= 0.75 ? 0 : 1);
                        super.onAttached();
                    }

                    @Override
                    public void onSelectedItemChanged() {
                        if ("small".equals(this.getSelectedItem())) {
                            menu.updatePositionConfig(config -> {
                                config.set("sizeX", SMALL_ITEM_SCALE);
                                config.set("sizeY", SMALL_ITEM_SCALE);
                                config.set("sizeZ", SMALL_ITEM_SCALE);
                            });
                        } else {
                            menu.updatePositionConfig(config -> {
                                config.remove("sizeX");
                                config.remove("sizeY");
                                config.remove("sizeZ");
                            });
                        }
                    }
                }.setBounds(25, 0, menu.getSliderWidth(), 11))
                        .addLabel(0, 3, "Scale");
            }
        }
    };

    private VirtualArmorStandItemEntity armorStandEntity;

    @Override
    public void onAttached() {
        super.onAttached();
        this.armorStandEntity = new VirtualArmorStandItemEntity(this.getManager());
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.armorStandEntity = null;
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
        Vector3 size = getConfiguredPosition().size;
        boolean small = ((size.x + size.y + size.z) / 3.0) < 0.75;

        this.armorStandEntity.setItem(newTransformType, small, newItem);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.armorStandEntity != null && this.armorStandEntity.getEntityId() == entityId;
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
        armorStandEntity.spawn(viewer, new Vector(0.0, 0.0, 0.0));

        // Apply focus color
        if (this.isFocused()) {
            this.updateGlowColorFor(this.armorStandEntity.getEntityUUID(), HelperMethods.getFocusGlowColor(this), viewer.getPlayer());
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        // Send entity destroy packet
        this.armorStandEntity.destroy(viewer);

        // Undo focus color
        this.updateGlowColorFor(this.armorStandEntity.getEntityUUID(), null, viewer.getPlayer());
    }

    @Override
    public void onFocus() {
        this.armorStandEntity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER, true);

        this.armorStandEntity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING | EntityHandle.DATA_FLAG_ON_FIRE, true);
        this.armorStandEntity.syncMetadata();
        this.updateGlowColor(this.armorStandEntity.getEntityUUID(), HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        this.armorStandEntity.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                EntityArmorStandHandle.DATA_FLAG_SET_MARKER, false);

        this.armorStandEntity.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING | EntityHandle.DATA_FLAG_ON_FIRE, false);
        this.armorStandEntity.syncMetadata();

        // Leave entity registered under the glow color to prevent flickering of white
        // this.updateGlowColor(this.entity.getEntityUUID(), null);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.armorStandEntity.updatePosition(transform);

        // Sync right now! Not only when moving!
        // This is a slow method due to packet constructor, so send the packet async
        this.armorStandEntity.syncMetadata();
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
        this.armorStandEntity.syncPosition(absolute);
    }

}
