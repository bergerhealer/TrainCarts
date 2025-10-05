package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayBlockEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.schematic.MovingSchematic;
import com.bergerkiller.bukkit.tc.attachments.control.schematic.WorldEditSchematicLoader;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualDisplayBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Loads a WorldEdit schematic by name and shows its block contents centered
 * on this attachment. Only works on 1.19.4+ and when WorldEdit is installed.
 */
public class CartAttachmentSchematic extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "WE_SCHEMATIC";
        }

        @Override
        public String getName() {
            return "SCHEMATIC";
        }

        @Override
        public boolean isListed(Player player) {
            return TrainCarts.plugin.getWorldEditSchematicLoader().isEnabled() && hasPermission(player);
        }

        @Override
        public boolean hasPermission(Player player) {
            return Permission.USE_SCHEMATIC_ATTACHMENTS.has(player);
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/schematic.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentSchematic();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            class SchematicButton extends MapWidgetButton {
                @Override
                public void onAttached() {
                    updateText();
                }

                public void updateText() {
                    String schematicName = attachment.getConfig().get("schematic", "");
                    if (schematicName.isEmpty()) {
                        setText("<No Schematic>");
                    } else {
                        setText(schematicName);
                    }
                }
            }

            MapWidgetSubmitText textBox = new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    this.setDescription("Enter schematic");
                }

                @Override
                public void onAccept(String text) {
                    attachment.getConfig().set("schematic", text.trim());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                    attachment.resetIcon();
                    for (MapWidget widget : tab.getWidgets()) {
                        if (widget instanceof SchematicButton) {
                            ((SchematicButton) widget).updateText();
                            break;
                        }
                    }
                }
            };

            tab.addWidget(textBox);

            tab.addWidget(new SchematicButton() {
                @Override
                public void onActivate() {
                    textBox.activate();
                }
            }).setBounds(0, 5, 100, 13);
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> (new MapWidgetButton() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    updateText(menu.getPositionConfigValue("clipEnabled", true));
                }

                @Override
                public void onActivate() {
                    boolean enabled = !menu.getPositionConfigValue("clipEnabled", true);
                    menu.updatePositionConfig(config -> {
                        if (enabled) {
                            config.remove("clipEnabled");
                        } else {
                            config.set("clipEnabled", false);
                        }
                    });
                    updateText(enabled);
                }

                private void updateText(boolean enabled) {
                    this.setText(enabled ? "Enabled" : "Disabled");
                }
            }).setBounds(32, 0, 72, 11))
                    .addLabel(0, 3, "Clipping")
                    .setSpacingAbove(3);

            builder.addPositionSlider("originX", "Origin X", "Schematic Origin X-Coordinate", 0.0)
                   .setSpacingAbove(3);
            builder.addPositionSlider("originY", "Origin Y", "Schematic Origin Y-Coordinate", 0.0);
            builder.addPositionSlider("originZ", "Origin Z", "Schematic Origin Z-Coordinate", 0.0);

            builder.addRow(menu -> (new MapWidgetSizeBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    setInitialSize(menu.getPositionConfigValue("spacingX", 0.0),
                                   menu.getPositionConfigValue("spacingY", 0.0),
                                   menu.getPositionConfigValue("spacingZ", 0.0));
                }

                @Override
                public void onSizeChanged() {
                    menu.updatePositionConfig(config -> {
                        if (x.getValue() == 0.0 && y.getValue() == 0.0 && z.getValue() == 0.0) {
                            config.remove("spacingX");
                            config.remove("spacingY");
                            config.remove("spacingZ");
                        } else {
                            config.set("spacingX", x.getValue());
                            config.set("spacingY", y.getValue());
                            config.set("spacingZ", z.getValue());
                        }
                    });
                }
            }).setRangeAndDefault(true, 0.0)
              .setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Gap X")
                    .addLabel(0, 15, "Gap Y")
                    .addLabel(0, 27, "Gap Z")
                    .setSpacingAbove(3);

            builder.addSizeBox();
        }
    };

    private WorldEditSchematicLoader.SchematicReader schematicReader;
    private MovingSchematic schematic;
    private DebugDisplay debug;

    @Override
    public void onAttached() {
        schematic = new MovingSchematic(getManager());
        schematicReader = TrainCarts.plugin.getWorldEditSchematicLoader().startReading(
                getConfig().get("schematic", ""));
        loadNextBlocks();
    }

    @Override
    public void onDetached() {
        schematic = null;
        schematicReader.abort();
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }
        if (!schematicReader.fileName().equals(config.get("schematic", ""))) {
            return false;
        }

        return true;
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        schematic.setScale(getConfiguredPosition().size);
        schematic.setHasClipping(config.getOrDefault("position.clipEnabled", true));

        schematic.setSpacing(new Vector(
                config.getOrDefault("position.spacingX", 0.0),
                config.getOrDefault("position.spacingY", 0.0),
                config.getOrDefault("position.spacingZ", 0.0)));
        schematic.setOrigin(new Vector(
                config.getOrDefault("position.originX", 0.0),
                config.getOrDefault("position.originY", 0.0),
                config.getOrDefault("position.originZ", 0.0)));
    }

    private void loadNextBlocks() {
        if (schematicReader.isDone()) {
            return;
        }

        WorldEditSchematicLoader.SchematicBlock block = schematicReader.next();
        if (block != null) {
            // Required for correct clipping bounding box calculations
            schematic.setBlockBounds(block.schematic.dimensions);

            // Center the entire schematic at the bottom-middle
            double originX = 0.5 * block.schematic.dimensions.x;
            double originY = 0.0;
            double originZ = 0.5 * block.schematic.dimensions.z;

            do {
                schematic.addBlock((double) block.x - originX,
                                   (double) block.y - originY,
                                   (double) block.z - originZ,
                        block.blockData);
            } while ((block = schematicReader.next()) != null);

            // This spawned new blocks, now mount them into the armorstand again
            schematic.resendMounts();
        }
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
        schematic.spawn(viewer, new Vector(0.0, 0.0, 0.0));
        if (debug != null) {
            debug.makeVisible(viewer);
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        schematic.destroy(viewer);
        if (debug != null) {
            debug.makeHidden(viewer);
        }
    }

    @Override
    public void onFocus() {
        if (debug == null) {
            debug = new DebugDisplay();
            for (AttachmentViewer viewer : getAttachmentViewers()) {
                debug.makeVisible(viewer);
            }
        } else {
            debug.focus();
        }
    }

    @Override
    public void onBlur() {
        if (debug != null) {
            debug.blur();
        }
    }

    @Override
    public void onTick() {
        loadNextBlocks();
        if (debug != null) {
            debug.ticksShown++;
            if (debug.ticksShown == 2) {
                debug.setGlowColor(HelperMethods.getFocusGlowColor(this));
            } else if (debug.ticksShown >= 40) {
                debug.hideForAll();
                debug = null;
            }
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        schematic.updatePosition(transform);
        if (debug != null) {
            debug.updatePosition();
        }
    }

    @Override
    public void onMove(boolean absolute) {
        schematic.syncPosition(absolute);
        if (debug != null) {
            debug.syncPosition(absolute);
        }
    }

    private class DebugDisplay {
        private VirtualDisplayBoundingBox bbox;
        private VirtualDisplayBlockEntity originPoint;
        private int ticksShown = 0;

        public DebugDisplay() {
            bbox = new VirtualDisplayBoundingBox(getManager());
            bbox.update(schematic.createBBOX());
            bbox.setGlowColor(null);
            if (schematic.hasOrigin()) {
                initOriginPoint();
            }
        }

        public void makeVisible(AttachmentViewer viewer) {
            bbox.spawn(viewer, new Vector(0.0, 0.0, 0.0));
            if (originPoint != null) {
                originPoint.spawn(viewer, new Vector(0.0, 0.0, 0.0));
            }
        }

        public void makeHidden(AttachmentViewer viewer) {
            bbox.destroy(viewer);
            if (originPoint != null) {
                originPoint.destroy(viewer);
            }
        }

        public void hideForAll() {
            bbox.destroyForAll();
            if (originPoint != null) {
                originPoint.destroyForAll();
            }
        }

        public void updatePosition() {
            bbox.update(schematic.createBBOX());
            if (schematic.hasOrigin()) {
                if (originPoint == null) {
                    initOriginPoint();
                    for (AttachmentViewer viewer : getAttachmentViewers()) {
                        originPoint.spawn(viewer, new Vector(0.0, 0.0, 0.0));
                    }
                } else {
                    originPoint.updatePosition(schematic.createOriginPointTransform());
                }
            } else if (originPoint != null) {
                originPoint.destroyForAll();
                originPoint = null;
            }
        }

        private void initOriginPoint() {
            originPoint = new VirtualDisplayBlockEntity(getManager());
            originPoint.setBlockData(BlockData.fromMaterial(MaterialUtil.getMaterial("REDSTONE_BLOCK")));
            originPoint.setScale(new Vector(0.1, 0.1, 0.1));
            originPoint.setGlowColor(ChatColor.RED);
            originPoint.updatePosition(schematic.createOriginPointTransform());
            originPoint.syncPosition(true);
        }

        public void syncPosition(boolean absolute) {
            bbox.syncPosition(absolute);
            if (originPoint != null) {
                originPoint.syncPosition(absolute);
            }
        }

        public void focus() {
            setGlowColor(null); // Don't glow at t=0 so that resizing doesn't look ugly
            ticksShown = 0;
        }

        public void blur() {
            setGlowColor(null);
            ticksShown = 20;
        }

        public void setGlowColor(ChatColor color) {
            bbox.setGlowColor(color);
        }
    }
}
