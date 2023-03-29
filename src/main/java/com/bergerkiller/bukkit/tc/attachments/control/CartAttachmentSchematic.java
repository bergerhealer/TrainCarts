package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
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
    private VirtualDisplayBoundingBox bbox;
    private int bboxShowTicksShown = 0;

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
        schematic.setSpacing(new Vector(
                config.getOrDefault("position.spacingX", 0.0),
                config.getOrDefault("position.spacingY", 0.0),
                config.getOrDefault("position.spacingZ", 0.0)));
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
        if (bbox != null) {
            bbox.spawn(viewer, new Vector(0.0, 0.0, 0.0));
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        schematic.destroy(viewer);
        if (bbox != null) {
            bbox.destroy(viewer);
        }
    }

    @Override
    public void onFocus() {
        if (bbox == null) {
            bbox = new VirtualDisplayBoundingBox(getManager());
            bbox.update(schematic.createBBOX());
            bbox.setGlowColor(null);
            for (AttachmentViewer viewer : getAttachmentViewers()) {
                bbox.spawn(viewer, new Vector(0.0, 0.0, 0.0));
            }
        } else {
            bbox.setGlowColor(null); // Don't glow at t=0 so that resizing doesn't look ugly
        }
        bboxShowTicksShown = 0;
    }

    @Override
    public void onBlur() {
        if (bbox != null) {
            bbox.setGlowColor(null);
            bboxShowTicksShown = 20;
        }
    }

    @Override
    public void onTick() {
        loadNextBlocks();
        if (bbox != null) {
            ++bboxShowTicksShown;
            if (bboxShowTicksShown == 2) {
                bbox.setGlowColor(HelperMethods.getFocusGlowColor(this));
            } else if (bboxShowTicksShown >= 40) {
                bbox.destroyForAll();
                bbox = null;
            }
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        schematic.updatePosition(transform);
        if (bbox != null) {
            bbox.update(schematic.createBBOX());
        }
    }

    @Override
    public void onMove(boolean absolute) {
        schematic.syncPosition(absolute);
        if (bbox != null) {
            bbox.syncPosition(absolute);
        }
    }
}
