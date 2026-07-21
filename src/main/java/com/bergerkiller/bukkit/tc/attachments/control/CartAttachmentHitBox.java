package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualHitBoxEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Is invisible, unless focused. Represents a box that players can interact with
 * to enter or destroy the cart. Can be overrided for custom attachments to
 * handle your own click actions for a configurable box in space.
 */
public class CartAttachmentHitBox extends CartAttachment {
    private static final Vector3 DEFAULT_SCALE = new Vector3(1.0, 1.0, 1.0);

    public static final AttachmentType TYPE = new BaseHitBoxType() {
        @Override
        public String getID() {
            return "HITBOX";
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentHitBox();
        }
    };

    protected final VirtualHitBoxEntity hitbox = new VirtualHitBoxEntity();

    @Override
    public void onLoad(ConfigurationNode config) {
        hitbox.setSize(LogicUtil.fixNull(this.getConfiguredPosition().size, DEFAULT_SCALE));
    }

    @Override
    public void makeVisible(Player viewer) {
        makeVisible(AttachmentViewer.fallback(viewer));
    }

    @Override
    public void makeHidden(Player viewer) {
        makeHidden(AttachmentViewer.fallback(viewer));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        hitbox.spawn(viewer, new Vector());
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        hitbox.destroy(viewer);
    }

    @Override
    public boolean containsEntityId(int id) {
        return hitbox.containsEntityId(id);
    }

    /**
     * Gets the oriented bounding box that defines the shape of this hitbox attachment
     * at the current time. Is kept updated.
     *
     * @return OrientedBoundingBox bbox
     */
    public OrientedBoundingBox getBoundingBox() {
        return hitbox.getBoundingBox();
    }

    @Override
    public void onFocus() {
        hitbox.spawnWireframe(HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        hitbox.spawnWireframe();
    }

    @Override
    public void onTick() {
        if (!this.isFocused()) {
            hitbox.destroyWireframeAfter(40);
        }

        // Do every tick, not just in onMove so it's more accurate
        hitbox.syncPosition(true);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        hitbox.updatePosition(transform);
    }

    @Override
    public void onMove(boolean absolute) {
    }

    protected static abstract class BaseHitBoxType implements AttachmentType {
        @Override
        public double getSortPriority() {
            return 1.0;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/hitbox.png");
        }

        @Override
        public void migrateConfiguration(ConfigurationNode config) {
            if (config.isNode("size")) {
                ConfigurationNode size = config.getNode("size");
                config.set("position.sizeX", size.get("x", 1.0));
                config.set("position.sizeY", size.get("y", 1.0));
                config.set("position.sizeZ", size.get("z", 1.0));
                size.remove();
            }
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> new MapWidgetSizeBox() {
                        @Override
                        public void onAttached() {
                            super.onAttached();

                            setSize(menu.getPositionConfigValue("sizeX", DEFAULT_SCALE.x),
                                    menu.getPositionConfigValue("sizeY", DEFAULT_SCALE.y),
                                    menu.getPositionConfigValue("sizeZ", DEFAULT_SCALE.z));
                        }

                        @Override
                        public void onSizeChanged() {
                            menu.updatePositionConfig(config -> {
                                config.set("sizeX", x.getValue());
                                config.set("sizeY", y.getValue());
                                config.set("sizeZ", z.getValue());
                            });
                        }
                    }.setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }
    }
}
