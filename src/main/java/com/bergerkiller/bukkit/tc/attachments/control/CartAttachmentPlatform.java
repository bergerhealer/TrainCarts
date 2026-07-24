package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
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

import java.util.function.Supplier;

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
            return readPlatformMode(config).getConstructor().get();
        }

        @Override
        public void migrateConfiguration(ConfigurationNode config) {
            // Migrate older constant that existed during testing.
            if ("PLANE".equals(config.getOrDefault("platformMode", "SHULKER"))) {
                config.set("platformMode", PlatformMode.SIMULATED_WITH_SHULKER_GRID);
            }
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            // Toggles whether shulkers spawn when the surface is not moving, and what kind of mode is used
            // This mode can also be switched when changing the size of the platform
            tab.addWidget(new MapWidgetText())
                    .setText("Operating Mode")
                    .setFont(MapFont.MINECRAFT)
                    .setColor(MapColorPalette.COLOR_RED)
                    .setBounds(12, 6, 50, 11);
            tab.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    for (PlatformMode mode : PlatformMode.values()) {
                        this.addItem(mode.getDisplayName());
                    }
                    this.setSelectedItem(readPlatformMode(attachment.getConfig()).getDisplayName());
                }

                @Override
                public void onActivate() {
                    int nextIndex = this.getSelectedIndex() + 1;
                    if (nextIndex >= PlatformMode.values().length) {
                        nextIndex = 0;
                    }
                    this.setSelectedIndex(nextIndex);
                    if (this.display != null) {
                        this.display.playSound(SoundEffect.CLICK);
                    }
                }

                @Override
                public void onSelectedItemChanged() {
                    attachment.getConfig().set("platformMode", PlatformMode.values()[getSelectedIndex()]);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(0, 15, 100, 12);

            // Shulker box color selector
            tab.addWidget(new MapWidgetText())
                    .setText("Shulker Color")
                    .setFont(MapFont.MINECRAFT)
                    .setColor(MapColorPalette.COLOR_RED)
                    .setBounds(15, 31, 50, 11);
            tab.addWidget(new MapWidgetSelectionBox() {
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
            }).setBounds(0, 40, 100, 12);
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

                            // If single-shulker mode, then only show Shulker instead of a particular size
                            if (readPlatformMode(menu.getConfig()) == PlatformMode.SINGLE_SHULKER) {
                                setTextOverride("Shulker");
                            } else {
                                setTextOverride(null);
                            }
                        }

                        @Override
                        public void onSizeChanged() {
                            setTextOverride(null);

                            // Once size is changed, switch to the simulation mode. Retain a different simulation mode configuration.
                            menu.updateConfig(config -> {
                                if (config.getOrDefault("platformMode", PlatformMode.SINGLE_SHULKER) == PlatformMode.SINGLE_SHULKER) {
                                    config.set("platformMode", PlatformMode.SIMULATED_WITH_SHULKER_GRID);
                                }
                            });

                            menu.updatePositionConfig(config -> {
                                config.set("sizeX", x.getValue());
                                config.set("sizeY", y.getValue());
                                config.set("sizeZ", z.getValue());
                            });
                        }

                        @Override
                        public void onUniformResetValue() {
                            // When holding space, reset back to single-shulker mode from this menu
                            // This is done by removing platformMode from the config entirely.
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
        return config.getOrDefault("platformMode", PlatformMode.SINGLE_SHULKER);
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        // Switches between attachment implementation class, then we can't reload
        if (readPlatformMode(config).getImplementationType() != this.getClass()) {
            return false;
        }

        return true;
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
        /** A single shulker box being moved around. This is the legacy mode for the platform attachment. */
        SINGLE_SHULKER("Single Shulker", false, false, CartAttachmentPlatformSingleShulker.class, CartAttachmentPlatformSingleShulker::new),
        /** A 2D plane that spawns shulkers when stationary, and does server-side player simulation when in motion */
        SIMULATED_WITH_SHULKER_GRID("Sim. & Shulkers", true, true, CartAttachmentPlatformSurfacePlane.class, CartAttachmentPlatformSurfacePlane::new),
        /** A 2D plane that does server-side player simulation all of the time, also while stationary */
        SIMULATED("Simulation", true, false, CartAttachmentPlatformSurfacePlane.class, CartAttachmentPlatformSurfacePlane::new),
        /** A 2D plane that spawns shulkers when stationary, but is inactive while in motion */
        SHULKER_GRID("Shulker Grid",false, true, CartAttachmentPlatformSurfacePlane.class, CartAttachmentPlatformSurfacePlane::new);

        // Technically there is also a mode where the shulker grid is kept updated also while the surface is in motion
        // However, aside from looking pretty, it has no functional purpose as players will fall through anyway when the slighted vertical component exists

        private final String displayName;
        private final boolean simulated;
        private final boolean shulkerGrid;
        private final Class<? extends CartAttachmentPlatform> implementationType;
        private final Supplier<? extends CartAttachmentPlatform> constructor;

        PlatformMode(
                final String displayName,
                final boolean simulated,
                final boolean shulkerGrid,
                final Class<? extends CartAttachmentPlatform> implementationType,
                final Supplier<? extends CartAttachmentPlatform> constructor
        ) {
            this.displayName = displayName;
            this.simulated = simulated;
            this.shulkerGrid = shulkerGrid;
            this.implementationType = implementationType;
            this.constructor = constructor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isSimulated() {
            return simulated;
        }

        public boolean isSpawningShulkerGrid() {
            return shulkerGrid;
        }

        public Class<? extends CartAttachmentPlatform> getImplementationType() {
            return implementationType;
        }

        public Supplier<? extends CartAttachmentPlatform> getConstructor() {
            return constructor;
        }
    }
}
