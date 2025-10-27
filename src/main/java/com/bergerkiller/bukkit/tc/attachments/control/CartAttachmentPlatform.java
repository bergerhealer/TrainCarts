package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import org.bukkit.entity.Player;

public abstract class CartAttachmentPlatform extends CartAttachment {
    protected static final Vector3 DEFAULT_SIZE = new Vector3(1.0, 1.0, 1.0);
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
            if (readPlatformMode(config) == PlatformMode.PLANE) {
                return new CartAttachmentPlatformPlane();
            } else {
                return new CartAttachmentPlatformShulker();
            }
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            // Shulker box color selector
            tab.addWidget(new MapWidgetText())
                    .setText("Shulker Color")
                    .setFont(MapFont.MINECRAFT)
                    .setColor(MapColorPalette.COLOR_RED)
                    .setBounds(15, 6, 50, 11);
            final MapWidget boatTypeSelector = tab.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.addItem(Color.DEFAULT.name());
                    for (Color color : Color.values()) {
                        if (color != Color.DEFAULT) {
                            this.addItem(color.name());
                        }
                    }
                    this.setSelectedItem(attachment.getConfig().getOrDefault("shulkerColor", Color.DEFAULT).name());
                }

                @Override
                public void onSelectedItemChanged() {
                    attachment.getConfig().set("shulkerColor", this.getSelectedItem());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(0, 15, 100, 12);
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> new MapWidgetSizeBox() {
                        @Override
                        public void onAttached() {
                            super.onAttached();

                            ConfigurationNode positionConfig = menu.getPositionConfig();
                            setInitialSize(
                                    positionConfig.getOrDefault("sizeX", DEFAULT_SIZE.x),
                                    positionConfig.getOrDefault("sizeY", DEFAULT_SIZE.y),
                                    positionConfig.getOrDefault("sizeZ", DEFAULT_SIZE.z));

                            if (readPlatformMode(menu.getConfig()) != PlatformMode.SHULKER) {
                                setTextOverride(null);
                            } else {
                                setTextOverride("Shulker");
                            }
                        }

                        @Override
                        public void onSizeChanged() {
                            setTextOverride(null);
                            menu.updateConfig(config -> {
                                config.set("platformMode", PlatformMode.PLANE);
                            });
                            menu.updatePositionConfig(config -> {
                                config.set("sizeX", x.getValue());
                                config.set("sizeY", y.getValue());
                                config.set("sizeZ", z.getValue());
                            });
                        }

                        @Override
                        public void onUniformResetValue() {
                            setTextOverride("Shulker");
                            menu.updateConfig(config -> {
                                config.remove("platformMode");
                            });
                            menu.updatePositionConfig(config -> {
                                config.remove("sizeX");
                                config.remove("sizeY");
                                config.remove("sizeZ");
                            });
                        }
                    }.setYAxisEnabled(false)
                     .setBounds(25, 0, menu.getSliderWidth(), 24))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Z")
                    .setSpacingAbove(3);
        }
    };

    protected static PlatformMode readPlatformMode(ConfigurationNode config) {
        return config.getOrDefault("platformMode", PlatformMode.SHULKER);
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
    public void onTick() {
    }

    /**
     * Shulker color, taken from nms EnumColor
     */
    public enum Color {
        WHITE,
        ORANGE,
        MAGENTA,
        LIGHT_BLUE,
        YELLOW,
        LIME,
        PINK,
        GRAY,
        LIGHT_GRAY,
        CYAN,
        PURPLE,
        BLUE,
        BROWN,
        GREEN,
        RED,
        BLACK,
        DEFAULT
    }

    public enum PlatformMode {
        SHULKER,
        PLANE
    }
}
