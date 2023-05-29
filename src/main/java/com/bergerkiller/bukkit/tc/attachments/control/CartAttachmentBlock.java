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
import com.bergerkiller.bukkit.tc.attachments.ui.block.BlockDataTextureCache;
import com.bergerkiller.bukkit.tc.attachments.ui.block.MapWidgetBlockDataSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetBrightnessDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shows a Block display entity
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
            MapWidgetBlockDataSelector selector = new MapWidgetBlockDataSelector() {
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

                @Override
                public void onBrightnessClicked() {
                    tab.addWidget(new MapWidgetBrightnessDialog.AttachmentBrightnessDialog(attachment))
                            .setPosition(13, 3)
                            .activate();
                }
            };
            selector.showBrightnessButton();
            tab.addWidget(selector);
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addSizeBox();
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
        if (config.isNode("brightness")) {
            entity.setBrightness(config.get("brightness.block", 0),
                                 config.get("brightness.sky", 0));
        } else {
            entity.setBrightness(-1, -1);
        }
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
