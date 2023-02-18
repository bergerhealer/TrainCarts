package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualFishingBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import org.bukkit.entity.Player;

/**
 * Is invisible, unless focused. Represents a box that players can interact with
 * to enter or destroy the cart.
 */
public class CartAttachmentHitBox extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "HITBOX";
        }

        @Override
        public double getSortPriority() {
            return 1.0;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/hitbox.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentHitBox();
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> new MapWidgetSizeBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    ConfigurationNode size = menu.getAttachment().getConfig().getNode("size");
                    setSize(size.get("x", 1.0), size.get("y", 1.0), size.get("z", 1.0));
                }

                @Override
                public void onSizeChanged() {
                    menu.updateConfig(config -> {
                        ConfigurationNode size = config.getNode("size");
                        size.set("x", x.getValue());
                        size.set("y", y.getValue());
                        size.set("z", z.getValue());
                    });
                }
            }.setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }
    };

    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
    private Box box = null; // Null if not spawned

    @Override
    public void onLoad(ConfigurationNode config) {
        ConfigurationNode size = config.getNode("size");
        bbox.setSize(size.get("x", 1.0), size.get("y", 1.0), size.get("z", 1.0));
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
        if (box != null) {
            if (this.isFocused()) {
                box.makeVisible(viewer);
            } else {
                box.makeVisibleWithoutLines(viewer);
            }
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (box != null) {
            box.makeHidden(viewer);
        }
    }

    @Override
    public void onFocus() {
        if (box == null) {
            box = new Box(bbox);
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                box.makeVisible(viewer);
            }
        } else {
            box.showLines(this.getAttachmentViewers());
        }
    }

    @Override
    public void onBlur() {
        if (box != null) {
            box.hideLines(this.getAttachmentViewers());
            box.tickLastHidden = CommonUtil.getServerTicks();
        }
    }

    @Override
    public void onTick() {
        if (box != null && !this.isFocused() &&
                (CommonUtil.getServerTicks() - box.tickLastHidden) > 40
        ) {
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                box.makeHidden(viewer);
            }
            box = null;
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        bbox.setPosition(transform.toVector());
        bbox.setOrientation(transform.getRotation());
    }

    @Override
    public void onMove(boolean absolute) {
        if (box != null) {
            box.move(this.getViewers());
        }
    }

    private static class Box {
        public final VirtualFishingBoundingBox entity = new VirtualFishingBoundingBox();
        public final OrientedBoundingBox bbox;
        private int tickLastHidden = 0;

        public Box(OrientedBoundingBox bbox) {
            this.bbox = bbox;
        }

        public void move(Iterable<Player> players) {
            entity.update(players, bbox);
        }

        public void makeVisible(AttachmentViewer viewer) {
            entity.spawn(viewer, bbox);
        }

        public void makeVisibleWithoutLines(AttachmentViewer viewer) {
            entity.spawnWithoutLines(viewer, bbox);
        }

        public void makeHidden(AttachmentViewer viewer) {
            entity.destroy(viewer);
        }

        public void showLines(Iterable<AttachmentViewer> viewers) {
            viewers.forEach(v -> entity.spawnLines(v, bbox));
        }

        public void hideLines(Iterable<AttachmentViewer> viewers) {
            viewers.forEach(entity::destroyLines);
        }
    }
}
