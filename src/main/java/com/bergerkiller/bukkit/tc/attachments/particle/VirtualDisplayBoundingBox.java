package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;

import java.util.List;

/**
 * Uses 12 block display entities to create "lines" making up the bounding box
 */
public class VirtualDisplayBoundingBox extends VirtualDisplayBoundingPlane {

    public VirtualDisplayBoundingBox(AttachmentManager manager) {
        super(manager);
    }

    protected void loadLines(List<Line> lines) {
        // Bottom plane
        // Along X
        lines.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 1.0)
                .applyScaleX(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                .applyScaleX(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                .applyScaleX(1.0)));
        lines.add( Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleX(1.0)));

        // Along Z
        lines.add(Line.transform(t -> t.applyPosition(1.0, 1.0, 0.0)
                .applyScaleZ(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                .applyScaleZ(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                .applyScaleZ(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleZ(1.0)));

        // Along Y
        lines.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 1.0)
                .applyScaleY(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                .applyScaleY(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                .applyScaleY(1.0)));
        lines.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleY(1.0)));
    }
}
