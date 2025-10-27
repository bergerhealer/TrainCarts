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

    protected void loadParts(List<Part> parts) {
        // Bottom plane
        // Along X
        parts.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 1.0)
                .applyScaleX(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                .applyScaleX(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                .applyScaleX(1.0)));
        parts.add( Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleX(1.0)));

        // Along Z
        parts.add(Line.transform(t -> t.applyPosition(1.0, 1.0, 0.0)
                .applyScaleZ(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                .applyScaleZ(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 1.0, 0.0)
                .applyScaleZ(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleZ(1.0)));

        // Along Y
        parts.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 1.0)
                .applyScaleY(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(1.0, 0.0, 0.0)
                .applyScaleY(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 1.0)
                .applyScaleY(1.0)));
        parts.add(Line.transform(t -> t.applyPosition(0.0, 0.0, 0.0)
                .applyScaleY(1.0)));
    }
}
