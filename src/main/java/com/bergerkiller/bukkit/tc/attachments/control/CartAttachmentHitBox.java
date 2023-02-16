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
    };

    private Box box = null; // Null if not spawned

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
            box = new Box();
            box.updateTransform(this.getTransform());
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
        if (box != null) {
            box.updateTransform(transform);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        if (box != null) {
            box.move(this.getViewers());
        }
    }

    private static class Box {
        public final VirtualFishingBoundingBox entity = new VirtualFishingBoundingBox();
        public final OrientedBoundingBox bbox = new OrientedBoundingBox();
        private int tickLastHidden = 0;

        public void updateTransform(Matrix4x4 transform) {
            bbox.setPosition(transform.toVector());
            bbox.setOrientation(transform.getRotation());
            bbox.setSize(2.0, 2.0, 2.0);
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
