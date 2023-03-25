package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.control.schematic.MovingSchematic;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

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
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/empty.png");
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

                    setSize(menu.getPositionConfigValue("spacingX", 0.0),
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

    private String currSchematicName = "";
    private MovingSchematic schematic;

    @Override
    public void onAttached() {
        schematic = new MovingSchematic(getManager());
        currSchematicName = getConfig().get("schematic", "");
        if (TCConfig.allowSchematicAttachment && !currSchematicName.isEmpty()) {
            loadSchematic();
        }
    }

    private void loadSchematic() {
        try {
            Clipboard c;
            File schemDir = WorldEdit.getInstance().getWorkingDirectoryFile("schematics"); // Get the schematics directory from WorldEdit API
            File file = new File(schemDir, currSchematicName);
            if (!file.exists()) {
                return; //TODO: Show something?
            }

            ClipboardFormat format = ClipboardFormats.findByFile(file);
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) { // Create a reader for the file
                clipboard = reader.read(); // Read the clipboard from the file
            }

            BlockVector3 min = clipboard.getMinimumPoint();
            BlockVector3 max = clipboard.getMaximumPoint();
            double originX = 0.5 * (double) (min.getX() + max.getX());
            double originY = (double) min.getY();
            double originZ = 0.5 * (double) (min.getZ() + max.getZ());

            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        BlockData blockData = BlockData.fromBukkit(BukkitAdapter.adapt(
                                clipboard.getBlock(BlockVector3.at(x, y, z))));
                        schematic.addBlock((double) x - originX,
                                (double) y - originY,
                                (double) z - originZ,
                                blockData);
                    }
                }
            }
        } catch (Throwable t) {
            getPlugin().getLogger().log(Level.SEVERE, "Failed to load schematic " + currSchematicName + " for attachment", t);
        }
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }
        if (!currSchematicName.equals(config.get("schematic", ""))) {
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

    @Override
    public void makeVisible(Player viewer) {
        schematic.spawn(viewer, new Vector(0.0, 0.0, 0.0));
    }

    @Override
    public void makeHidden(Player viewer) {
        schematic.destroy(viewer);
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        schematic.updatePosition(transform);
    }

    @Override
    public void onMove(boolean absolute) {
        schematic.syncPosition(absolute);
    }
}
