package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;

/**
 * Particle that represents a bounding box rotated in 3D space.
 */
public abstract class VirtualBoundingBox extends VirtualSpawnableObject {

    public VirtualBoundingBox(AttachmentManager manager) {
        super(manager);
    }

    /**
     * Updates the bounding box displayed. Should be called at least once initially
     * before {@link #spawn(AttachmentViewer, Vector)} can be called.
     *
     * @param boundingBox Updated bounding box
     */
    public abstract void update(OrientedBoundingBox boundingBox);

    @Deprecated
    @Override
    public final void updatePosition(Matrix4x4 transform) {
        throw new UnsupportedOperationException("Must specify a bounding box");
    }
}
