package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurface;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurfaceTracker;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 2D plane (can tilt) above which players are kept positioned. The
 * size of this plane can be adjusted. When players are detected falling
 * through this plane, they are teleported upwards.
 */
public class CartAttachmentPlatformPlane extends CartAttachmentPlatform {
    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
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
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (plane != null) {
            plane.makeHidden(viewer);
        }

        CollisionSurface surface = surfacesOfViewers.remove(viewer);
        if (surface != null) {
            surface.clear();
        }
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

    private final List<LockedPlayer> lockedPlayers = new ArrayList<>();

    private boolean isLocked(AttachmentViewer viewer) {
        for (LockedPlayer player : lockedPlayers) {
            if (player.viewer.getPlayer() == viewer.getPlayer()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (plane != null && !this.isFocused() &&
                (CommonUtil.getServerTicks() - plane.tickLastHidden) > 40
        ) {
            plane.entity.destroyForAll();
            plane = null;
        }

        if (!HelperMethods.hasInactiveParent(this) && isActive()) {
            spawnSurfaces();
        }
    }

    private Map<AttachmentViewer, CollisionSurface> surfacesOfViewers = new HashMap<>();

    private void spawnSurfaces() {
        for (AttachmentViewer viewer : getAttachmentViewers()) {
            CollisionSurface surface = surfacesOfViewers.computeIfAbsent(viewer, AttachmentViewer::createCollisionSurface);
            surface.clear();
            surface.addSurface(bbox);
        }
    }

    private void lockPlayers() {
        Vector halfSize = bbox.getSize().clone().multiply(0.5);
        for (AttachmentViewer viewer : getAttachmentViewers()) {
            if (isLocked(viewer)) {
                continue;
            }

            Quaternion q = bbox.getOrientation();
            Vector pos = viewer.getPlayer().getLocation().toVector();
            pos.subtract(bbox.getPosition());
            q.invTransformPoint(pos);
            if (
                    Math.abs(pos.getX()) < halfSize.getX()
                            && Math.abs(pos.getZ()) < halfSize.getZ()
                            && pos.getY() >= 0.0 && pos.getY() <= 3.0
            ) {
                System.out.println("LOCK: " + pos);
                lockedPlayers.add(new LockedPlayer(viewer, pos.getX(), pos.getZ()));
            }
        }

        lockedPlayers.removeIf(p -> {
            if (p.isUnlocked()) {
                p.unlock();
                return true;
            } else {
                return false;
            }
        });

        // lock em
        for (LockedPlayer player : lockedPlayers) {
            player.updatePlayerMovement(bbox);
            player.lock(bbox);
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
    }

    @Override
    public void onMove(boolean absolute) {
        if (plane != null) {
            plane.sync();
        }
    }

    private static class LockedPlayer {
        public final AttachmentViewer viewer;
        public double rx, rz;
        public AttachmentViewer.MovementController movementController;

        public LockedPlayer(AttachmentViewer viewer, double rx, double rz) {
            this.viewer = viewer;
            this.rx = rx;
            this.rz = rz;
        }

        public boolean isUnlocked() {
            return !viewer.getPlayer().isValid() || viewer.getPlayer().isSneaking();
        }

        public void lock(OrientedBoundingBox bbox) {
            if (movementController == null) {
                movementController = viewer.controlMovement();
            }

            Vector pos = new Vector(rx, 0.0, rz);
            bbox.getOrientation().transformPoint(pos);
            pos.add(bbox.getPosition());

            movementController.update(pos);
        }

        public void unlock() {
            if (movementController != null) {
                movementController.stop();
            }
        }

        public void updatePlayerMovement(OrientedBoundingBox bbox) {
            if (movementController == null) {
                return;
            }

            AttachmentViewer.Input input = movementController.getInput();
            double speed = 0.15;
            if (input.sprinting()) {
                speed *= 1.5;
            }

            if (input.hasWalkInput()) {
                Location eyeLoc = viewer.getPlayer().getEyeLocation();
                Quaternion orientation = Quaternion.fromYawPitchRoll(0.0, eyeLoc.getYaw(), 0.0);

                Quaternion relative = orientation.clone();
                relative.divide(bbox.getOrientation());

                Vector fwd = relative.forwardVector();
                fwd.setY(0.0);
                fwd.normalize();
                fwd.multiply(speed);
                if (input.hasDiagonalWalkInput()) {
                    fwd.multiply(MathUtil.HALFROOTOFTWO);
                }
                fwd.multiply(input.forwardsSigNum());

                Vector side = relative.rightVector();
                side.setY(0.0);
                side.normalize();
                side.multiply(speed);
                if (input.hasDiagonalWalkInput()) {
                    side.multiply(MathUtil.HALFROOTOFTWO);
                }
                side.multiply(input.sidewaysSigNum());

                rx += fwd.getX() + side.getX();
                rz += fwd.getZ() + side.getZ();
            }
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
