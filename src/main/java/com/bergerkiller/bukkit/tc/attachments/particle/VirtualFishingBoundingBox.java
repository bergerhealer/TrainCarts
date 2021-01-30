package com.bergerkiller.bukkit.tc.attachments.particle;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Uses 12 {@link VirtualFishingLine} instances to display a bounding box
 */
public class VirtualFishingBoundingBox {
    // Bottom 4 lines
    private final VirtualFishingLine line_btm_nx = new VirtualFishingLine();
    private final VirtualFishingLine line_btm_px = new VirtualFishingLine();
    private final VirtualFishingLine line_btm_nz = new VirtualFishingLine();
    private final VirtualFishingLine line_btm_pz = new VirtualFishingLine();

    // Top 4 lines
    private final VirtualFishingLine line_top_nx = new VirtualFishingLine();
    private final VirtualFishingLine line_top_px = new VirtualFishingLine();
    private final VirtualFishingLine line_top_nz = new VirtualFishingLine();
    private final VirtualFishingLine line_top_pz = new VirtualFishingLine();

    // Vertical 4 lines
    private final VirtualFishingLine line_vrt_nxnz = new VirtualFishingLine();
    private final VirtualFishingLine line_vrt_pxnz = new VirtualFishingLine();
    private final VirtualFishingLine line_vrt_pxpz = new VirtualFishingLine();
    private final VirtualFishingLine line_vrt_nxpz = new VirtualFishingLine();

    public void spawn(Player viewer, OrientedBoundingBox boundingBox) {
        ComputedCorners corners = new ComputedCorners(boundingBox);

        line_btm_nx.spawn(viewer, corners.btm_nx_nz, corners.btm_nx_pz);
        line_btm_nz.spawn(viewer, corners.btm_px_nz, corners.btm_nx_nz);
        line_btm_px.spawn(viewer, corners.btm_px_pz, corners.btm_px_nz);
        line_btm_pz.spawn(viewer, corners.btm_nx_pz, corners.btm_px_pz);

        line_top_nx.spawn(viewer, corners.top_nx_nz, corners.top_nx_pz);
        line_top_nz.spawn(viewer, corners.top_px_nz, corners.top_nx_nz);
        line_top_px.spawn(viewer, corners.top_px_pz, corners.top_px_nz);
        line_top_pz.spawn(viewer, corners.top_nx_pz, corners.top_px_pz);

        line_vrt_nxnz.spawn(viewer, corners.top_nx_nz, corners.btm_nx_nz);
        line_vrt_pxnz.spawn(viewer, corners.top_px_nz, corners.btm_px_nz);
        line_vrt_pxpz.spawn(viewer, corners.top_px_pz, corners.btm_px_pz);
        line_vrt_nxpz.spawn(viewer, corners.top_nx_pz, corners.btm_nx_pz);
    }

    public void update(Iterable<Player> viewers, OrientedBoundingBox boundingBox) {
        ComputedCorners corners = new ComputedCorners(boundingBox);

        line_btm_nx.update(viewers, corners.btm_nx_nz, corners.btm_nx_pz);
        line_btm_nz.update(viewers, corners.btm_px_nz, corners.btm_nx_nz);
        line_btm_px.update(viewers, corners.btm_px_pz, corners.btm_px_nz);
        line_btm_pz.update(viewers, corners.btm_nx_pz, corners.btm_px_pz);

        line_top_nx.update(viewers, corners.top_nx_nz, corners.top_nx_pz);
        line_top_nz.update(viewers, corners.top_px_nz, corners.top_nx_nz);
        line_top_px.update(viewers, corners.top_px_pz, corners.top_px_nz);
        line_top_pz.update(viewers, corners.top_nx_pz, corners.top_px_pz);

        line_vrt_nxnz.update(viewers, corners.top_nx_nz, corners.btm_nx_nz);
        line_vrt_pxnz.update(viewers, corners.top_px_nz, corners.btm_px_nz);
        line_vrt_pxpz.update(viewers, corners.top_px_pz, corners.btm_px_pz);
        line_vrt_nxpz.update(viewers, corners.top_nx_pz, corners.btm_nx_pz);
    }

    public void destroy(Player viewer) {
        line_btm_nx.destroy(viewer);
        line_btm_nz.destroy(viewer);
        line_btm_px.destroy(viewer);
        line_btm_pz.destroy(viewer);

        line_top_nx.destroy(viewer);
        line_top_nz.destroy(viewer);
        line_top_px.destroy(viewer);
        line_top_pz.destroy(viewer);

        line_vrt_nxnz.destroy(viewer);
        line_vrt_pxnz.destroy(viewer);
        line_vrt_pxpz.destroy(viewer);
        line_vrt_nxpz.destroy(viewer);
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

            // Add postion offset to all
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
}
