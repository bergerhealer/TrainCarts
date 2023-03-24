package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayBlockEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.block.BlockDataTextureCache;
import com.bergerkiller.bukkit.tc.attachments.ui.block.MapWidgetBlockDataSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shows a Block display entity, or multiple blocks using a WorldEdit schematic
 * as a source.
 */
public class CartAttachmentBlock extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "BLOCK_DISPLAY";
        }

        @Override
        public String getName() {
            return "BLOCK";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            BlockData blockData = deserializeBlockData(config);
            if (blockData != null) {
                return BlockDataTextureCache.get(16, 16).get(blockData);
            }

            // Failed to load BlockData - wrong version maybe?
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/unknown_block.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentBlock();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("blockData", BlockData.fromMaterial(
                    MaterialUtil.getFirst("COBBLESTONE", "LEGACY_COBBLESTONE"))
                    .serializeToString());
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            tab.addWidget(new MapWidgetBlockDataSelector() {
                @Override
                public void onAttached() {
                    this.setSelectedBlockData(deserializeBlockData(attachment.getConfig()));
                }

                @Override
                public void onSelectedBlockDataChanged(BlockData blockData) {
                    attachment.getConfig().set("blockData", (blockData == null) ? null : blockData.serializeToString());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                    attachment.resetIcon();
                }
            });
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> (new MapWidgetSizeBox() {
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
            }).setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }
    };

    private static BlockData deserializeBlockData(ConfigurationNode config) {
        String blockDataStr = config.get("blockData", String.class);
        if (blockDataStr != null) {
            return BlockData.fromString(blockDataStr);
        }
        return null;
    }

    private VirtualDisplayBlockEntity entity;

    @Override
    public void onAttached() {
        entity = new VirtualDisplayBlockEntity(getManager());
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        entity.setBlockData(deserializeBlockData(config));
        entity.setScale(getConfiguredPosition().size);
    }

    @Override
    public void onDetached() {
        entity = null;
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return entity != null && entity.containsEntityId(entityId);
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
