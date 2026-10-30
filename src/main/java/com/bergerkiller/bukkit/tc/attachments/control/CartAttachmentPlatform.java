package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceAnimation;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceMaterialSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfacePlatformModeSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceShulkerColorSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.surface.MapWidgetSurfaceViewLockSelector;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class CartAttachmentPlatform extends CartAttachment {
    protected static final BlockData DEFAULT_SURFACE_MATERIAL = BlockData.fromMaterial(MaterialUtil.getFirst("IRON_BLOCK", "LEGACY_IRON_BLOCK"));
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
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            final MapWidgetSurfaceAnimation surfaceAnimation = new MapWidgetSurfaceAnimation();
            surfaceAnimation.setBounds(-5, 16, 110, 70);
            tab.addWidget(surfaceAnimation);

            final int MENU_X_BASE = 15;
            final int MENU_Y_BASE = 5;
            final int MENU_X_STEP = 19;

            // Shulker box color selector
            final MapWidget shulkerColorSelector = tab.addWidget(new MapWidgetSurfaceShulkerColorSelector() {
                boolean loading = true;

                @Override
                public void onAttached() {
                    setSelectedColor(attachment.getConfig().getOrDefault("shulkerColor", Color.DEFAULT));
                    super.onAttached();
                    loading = false;
                }

                @Override
                public void onSelectedItemChanged() {
                    surfaceAnimation.setShulkerColor(getSelectedColor());
                    if (isUserInteractive()) {
                        attachment.getConfig().set("shulkerColor", this.getSelectedColor());
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }
            }.setPosition(MENU_X_BASE + MENU_X_STEP, MENU_Y_BASE));

            // Whether the player view rotates when the surface the player is on rotates (simulated mode only)
            final MapWidget viewLockSelector = tab.addWidget(new MapWidgetSurfaceViewLockSelector() {
                @Override
                public void onAttached() {
                    setViewLocked(attachment.getConfig().getOrDefault("viewLock", true));
                    super.onAttached();
                }

                @Override
                public void onViewLockedChanged() {
                    surfaceAnimation.setViewLock(isViewLocked());
                    if (isUserInteractive()) {
                        attachment.getConfig().set("viewLock", isViewLocked());
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }
            }.setPosition(MENU_X_BASE + 2 * MENU_X_STEP, MENU_Y_BASE));

            // Surface material. Controls the slipperiness and step sound of a simulated surface
            final MapWidget surfaceMaterialSelector = tab.addWidget(new MapWidgetSurfaceMaterialSelector() {

                @Override
                public void onAttached() {
                    setSelectedBlockData(Util.getConfigBlockDataOptional(attachment.getConfig(), "surfaceMaterial").orElse(DEFAULT_SURFACE_MATERIAL));
                    super.onAttached();
                }

                @Override
                public void onSelectedBlockDataChanged(BlockData blockData) {
                    surfaceAnimation.setSurfaceBlockData(blockData);
                    if (isUserInteractive()) {
                        Util.setConfigBlockDataOptional(attachment.getConfig(), "surfaceMaterial", Optional.ofNullable(blockData));
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }
            }.setPosition(MENU_X_BASE + MENU_X_STEP * 3, MENU_Y_BASE));

            // Toggles whether shulkers spawn when the surface is not moving, and what kind of mode is used
            // This mode can also be switched when changing the size of the platform
            tab.addWidget(new MapWidgetSurfacePlatformModeSelector() {
                @Override
                public void onAttached() {
                    setSelectedMode(readPlatformMode(attachment.getConfig()));
                    super.onAttached();
                }

                @Override
                public void onSelectedItemChanged() {
                    final PlatformMode mode = getSelectedMode();
                    surfaceAnimation.setPlatformMode(mode);
                    shulkerColorSelector.setEnabled(mode.isSpawningShulkerGrid() || mode == PlatformMode.SINGLE_SHULKER);
                    viewLockSelector.setEnabled(mode.isSimulated());
                    surfaceMaterialSelector.setEnabled(mode.isSimulated());
                    if (display != null) {
                        attachment.getConfig().set("platformMode", mode);
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }
            }.setPosition(MENU_X_BASE, MENU_Y_BASE));
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
        WHITE(MapColorPalette.getColor(249, 255, 254)),
        ORANGE(MapColorPalette.getColor(249, 128, 29)),
        MAGENTA(MapColorPalette.getColor(199, 78, 189)),
        LIGHT_BLUE(MapColorPalette.getColor(58, 179, 218)),
        YELLOW(MapColorPalette.getColor(254, 216, 61)),
        LIME(MapColorPalette.getColor(128, 199, 31)),
        PINK(MapColorPalette.getColor(243, 139, 170)),
        GRAY(MapColorPalette.getColor(71, 79, 82)),
        LIGHT_GRAY(MapColorPalette.getColor(157, 157, 151)),
        CYAN(MapColorPalette.getColor(22, 156, 156)),
        PURPLE(MapColorPalette.getColor(137, 50, 184)),
        BLUE(MapColorPalette.getColor(60, 68, 170)),
        BROWN(MapColorPalette.getColor(131, 84, 50)),
        GREEN(MapColorPalette.getColor(94, 124, 22)),
        RED(MapColorPalette.getColor(176, 46, 38)),
        BLACK(MapColorPalette.getColor(29, 29, 33)),
        DEFAULT(MapColorPalette.getColor(137, 50, 184));

        private final byte mapColor;

        Color(byte mapColor) {
            this.mapColor = mapColor;
        }

        public byte mapColor() {
            return this.mapColor;
        }
    }

    public enum PlatformMode {
        /** A single shulker box being moved around. This is the legacy mode for the platform attachment. */
        SINGLE_SHULKER("Single Shulker", false, false, CartAttachmentPlatformSingleShulker.class, CartAttachmentPlatformSingleShulker::new),
        /** A 2D plane that spawns shulkers when stationary, and does server-side player simulation when in motion */
        SIMULATED_WITH_SHULKER_GRID("Sim. & Shulkers", true, true, CartAttachmentPlatformSurfacePlane.class, CartAttachmentPlatformSurfacePlane::new),
        /** A 2D plane that does server-side player simulation all of the time, also while stationary */
        SIMULATED("Simulated", true, false, CartAttachmentPlatformSurfacePlane.class, CartAttachmentPlatformSurfacePlane::new),
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
