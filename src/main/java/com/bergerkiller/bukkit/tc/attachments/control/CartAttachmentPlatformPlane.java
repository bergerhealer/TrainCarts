package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurface;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2D plane (can tilt) above which players are kept positioned. The
 * size of this plane can be adjusted. When players are detected falling
 * through this plane, they are teleported upwards.
 */
public class CartAttachmentPlatformPlane extends CartAttachmentPlatform {
    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
    private final List<PlayerSurface> playerSurfaces = new ArrayList<>();
    private Plane plane = null; // Null if not spawned

    @Override
    public void onLoad(ConfigurationNode config) {
        Vector3 size = LogicUtil.fixNull(this.getConfiguredPosition().size, DEFAULT_SIZE);
        bbox.setSize(new Vector(size.x, 0.0, size.z));
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        // Switches between attachment implementation class
        if (readPlatformMode(config) != PlatformMode.PLANE) {
            return false;
        }

        return true;
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        if (plane != null) {
            plane.makeVisible(viewer);
        }

        PlayerSurface ps = new PlayerSurface(viewer, viewer.createCollisionSurface());
        ps.surface.setShape(bbox);
        playerSurfaces.add(ps);
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (plane != null) {
            plane.makeHidden(viewer);
        }

        playerSurfaces.removeIf(ps -> {
            if (ps.viewer.equals(viewer)) {
                ps.surface.remove();
                return true;
            } else {
                return false;
            }
        });
    }

    public void setPlaneColor(ChatColor color) {
        if (color != null) {
            if (plane == null) {
                plane = new Plane(getManager(), bbox);
                plane.entity.setGlowColor(color);
                for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                    plane.makeVisible(viewer);
                }
            } else {
                plane.entity.setGlowColor(color);
            }
        } else {
            if (plane != null) {
                plane.entity.setGlowColor(null);
                plane.tickLastHidden = CommonUtil.getServerTicks();
            }
        }
    }

    @Override
    public void onFocus() {
        setPlaneColor(HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        setPlaneColor(null);
    }

    @Override
    public void onTick() {
        if (plane != null && !this.isFocused() &&
                (CommonUtil.getServerTicks() - plane.tickLastHidden) > 40
        ) {
            plane.entity.destroyForAll();
            plane = null;
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        Quaternion orientation = transform.getRotation();
        bbox.setPosition(transform.toVector().add(orientation.upVector().multiply(0.5 * bbox.getSize().getY())));
        bbox.setOrientation(orientation);
        if (plane != null) {
            plane.update(bbox);
        }
        playerSurfaces.forEach(ps -> ps.surface.setShape(bbox));
    }

    @Override
    public void onMove(boolean absolute) {
        if (plane != null) {
            plane.sync();
        }
    }

    /**
     * This bbox tracked using the attachments collision surface API.
     * Must be kept updated when the shape changes.
     */
    private static class PlayerSurface {
        public final AttachmentViewer viewer;
        public final CollisionSurface surface;

        public PlayerSurface(AttachmentViewer viewer, CollisionSurface surface) {
            this.viewer = viewer;
            this.surface = surface;
        }
    }

    private static class Plane {
        public final VirtualBoundingBox entity;
        private int tickLastHidden = 0;

        public Plane(AttachmentManager manager, OrientedBoundingBox bbox) {
            this.entity = VirtualBoundingBox.createPlane(manager, MaterialUtil.getFirst("ICE", "LEGACY_ICE"));
            this.entity.update(bbox);
        }

        public void update(OrientedBoundingBox bbox) {
            this.entity.update(bbox);
        }

        public void sync() {
            this.entity.syncPosition(true);
        }

        public void makeVisible(AttachmentViewer viewer) {
            entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
        }

        public void makeHidden(AttachmentViewer viewer) {
            entity.destroy(viewer);
        }
    }
}
