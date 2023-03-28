package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Uses 12 {@link VirtualFishingLine} instances to display a bounding box
 */
public class VirtualFishingBoundingBox extends VirtualBoundingBox {
    // Bottom 4 lines
    private final BBOXLine line_btm_nx = new BBOXLine(c -> c.btm_nx_nz, c -> c.btm_nx_pz);
    private final BBOXLine line_btm_px = new BBOXLine(c -> c.btm_px_nz, c -> c.btm_nx_nz);
    private final BBOXLine line_btm_nz = new BBOXLine(c -> c.btm_px_pz, c -> c.btm_px_nz);
    private final BBOXLine line_btm_pz = new BBOXLine(c -> c.btm_nx_pz, c -> c.btm_px_pz);

    // Top 4 lines
    private final BBOXLine line_top_nx = new BBOXLine(c -> c.top_nx_nz, c -> c.top_nx_pz);
    private final BBOXLine line_top_px = new BBOXLine(c -> c.top_px_nz, c -> c.top_nx_nz);
    private final BBOXLine line_top_nz = new BBOXLine(c -> c.top_px_pz, c -> c.top_px_nz);
    private final BBOXLine line_top_pz = new BBOXLine(c -> c.top_nx_pz, c -> c.top_px_pz);

    // Vertical 4 lines
    private final BBOXLine line_vrt_nxnz = new BBOXLine(c -> c.top_nx_nz, c -> c.btm_nx_nz);
    private final BBOXLine line_vrt_pxnz = new BBOXLine(c -> c.top_px_nz, c -> c.btm_px_nz);
    private final BBOXLine line_vrt_pxpz = new BBOXLine(c -> c.top_px_pz, c -> c.btm_px_pz);
    private final BBOXLine line_vrt_nxpz = new BBOXLine(c -> c.top_nx_pz, c -> c.btm_nx_pz);

    private final List<BBOXLine> lines = Arrays.asList(
            line_btm_nx, line_btm_nz, line_btm_px, line_btm_pz,
            line_top_nx, line_top_nz, line_top_px, line_top_pz,
            line_vrt_nxnz, line_vrt_pxnz, line_vrt_pxpz, line_vrt_nxpz
    );

    private ComputedCorners corners = null;
    private boolean linesSpawned = false;

    public VirtualFishingBoundingBox(AttachmentManager manager) {
        super(manager);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return false; // Collision is disabled
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        ArrayList<UUID> uuids = new ArrayList<>(24);
        if (linesSpawned) {
            lines.forEach(bboxline -> bboxline.spawn(viewer, corners, uuids));
        } else {
            lines.forEach(bboxline -> bboxline.spawnWithoutLine(viewer, corners, uuids));
        }
        viewer.sendDisableCollision(uuids);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        lines.forEach(line -> line.destroy(viewer));
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        if (linesSpawned != (color != null)) {
            linesSpawned = !linesSpawned;
            if (hasViewers()) {
                if (linesSpawned) {
                    // Spawn the lines for everyone
                    forAllViewers(v -> lines.forEach(line -> line.spawnLine(v, corners)));
                } else {
                    // Destroy the lines for everyone
                    forAllViewers(v -> lines.forEach(line -> line.destroyLine(v)));
                }
            }
        }
    }

    @Override
    public void update(OrientedBoundingBox boundingBox) {
        this.corners = new ComputedCorners(boundingBox);
    }

    @Override
    public void syncPosition(boolean absolute) {
        lines.forEach(bboxline -> bboxline.updateViewers(getViewers(), corners));
    }

    private static class ComputedCorners {
        // Bottom 4 points
        public final Vector btm_nx_nz;
        public final Vector btm_px_nz;
        public final Vector btm_px_pz;
        public final Vector btm_nx_pz;
        // Top 4 points
        public final Vector top_nx_nz;
        public final Vector top_px_nz;
        public final Vector top_px_pz;
        public final Vector top_nx_pz;

        public ComputedCorners(OrientedBoundingBox boundingBox) {
            // Get the 8 corners of the bounding box without the position offset
            Vector hsize = boundingBox.getSize().clone().multiply(0.5);
            this.btm_nx_nz = new Vector(-hsize.getX(), -hsize.getY(), -hsize.getZ());
            this.btm_px_nz = new Vector(hsize.getX(), -hsize.getY(), -hsize.getZ());
            this.btm_px_pz = new Vector(hsize.getX(), -hsize.getY(), hsize.getZ());
            this.btm_nx_pz = new Vector(-hsize.getX(), -hsize.getY(), hsize.getZ());
            this.top_nx_nz = new Vector(-hsize.getX(), hsize.getY(), -hsize.getZ());
            this.top_px_nz = new Vector(hsize.getX(), hsize.getY(), -hsize.getZ());
            this.top_px_pz = new Vector(hsize.getX(), hsize.getY(), hsize.getZ());
            this.top_nx_pz = new Vector(-hsize.getX(), hsize.getY(), hsize.getZ());

            // Transform all by the orientation
            Quaternion orientation = boundingBox.getOrientation();
            orientation.transformPoint(this.btm_nx_nz);
            orientation.transformPoint(this.btm_px_nz);
            orientation.transformPoint(this.btm_px_pz);
            orientation.transformPoint(this.btm_nx_pz);
            orientation.transformPoint(this.top_nx_nz);
            orientation.transformPoint(this.top_px_nz);
            orientation.transformPoint(this.top_px_pz);
            orientation.transformPoint(this.top_nx_pz);

            // Add position offset to all
            Vector offset = boundingBox.getPosition();
            this.btm_nx_nz.add(offset);
            this.btm_px_nz.add(offset);
            this.btm_px_pz.add(offset);
            this.btm_nx_pz.add(offset);
            this.top_nx_nz.add(offset);
            this.top_px_nz.add(offset);
            this.top_px_pz.add(offset);
            this.top_nx_pz.add(offset);
        }
    }

    private static class BBOXLine extends VirtualFishingLine {
        private final Function<ComputedCorners, Vector> pos1func;
        private final Function<ComputedCorners, Vector> pos2func;

        public BBOXLine(Function<ComputedCorners, Vector> pos1func,
                        Function<ComputedCorners, Vector> pos2func)
        {
            this.pos1func = pos1func;
            this.pos2func = pos2func;
        }

        public void spawn(AttachmentViewer viewer, ComputedCorners corners, List<UUID> uuids) {
            Vector p1 = pos1func.apply(corners);
            Vector p2 = pos2func.apply(corners);
            this.spawnWithoutLineCollectUUIDs(viewer, p1, p2, uuids);
            this.spawnLine(viewer, p1, p2);
        }

        public void spawnWithoutLine(AttachmentViewer viewer, ComputedCorners corners, List<UUID> uuids) {
            this.spawnWithoutLineCollectUUIDs(viewer, pos1func.apply(corners), pos2func.apply(corners), uuids);
        }

        public void spawnLine(AttachmentViewer viewer, ComputedCorners corners) {
            this.spawnLine(viewer, pos1func.apply(corners), pos2func.apply(corners));
        }

        public void updateViewers(Iterable<AttachmentViewer> viewers, ComputedCorners corners) {
            this.updateViewers(viewers, pos1func.apply(corners), pos2func.apply(corners));
        }
    }
}
