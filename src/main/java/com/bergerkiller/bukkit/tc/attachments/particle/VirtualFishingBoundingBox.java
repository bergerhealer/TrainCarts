package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;

import java.util.Arrays;

/**
 * Uses 12 {@link VirtualFishingLine} instances to display a bounding box
 */
public class VirtualFishingBoundingBox extends VirtualFishingBoundingPlane {
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

    public VirtualFishingBoundingBox(AttachmentManager manager) {
        super(manager);

        lines = Arrays.asList(
                line_btm_nx, line_btm_nz, line_btm_px, line_btm_pz,
                line_top_nx, line_top_nz, line_top_px, line_top_pz,
                line_vrt_nxnz, line_vrt_pxnz, line_vrt_pxpz, line_vrt_nxpz
        );
    }
}
