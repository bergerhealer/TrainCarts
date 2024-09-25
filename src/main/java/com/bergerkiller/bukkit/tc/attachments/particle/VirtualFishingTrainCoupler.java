package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;

/**
 * Displays a train coupler using fishing lines
 */
public class VirtualFishingTrainCoupler extends VirtualTrainCoupler {
    private final VirtualFishingLine line = new VirtualFishingLine();
    private Vector pos1, pos2;

    public VirtualFishingTrainCoupler(AttachmentManager manager) {
        super(manager);
    }

    @Override
    public void update(Matrix4x4 transform, double length) {
        this.pos2 = transform.toVector();
        Matrix4x4 tmp = transform.clone();
        tmp.translate(0.0, 0.0, length);
        this.pos1 = tmp.toVector();
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        throw new UnsupportedOperationException("Must specify a transform with length");
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        this.line.spawn(viewer, this.pos1, this.pos2);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        this.line.destroy(viewer);
    }

    @Override
    public void syncPosition(boolean absolute) {
        this.line.updateViewers(this.getViewers(), this.pos1, this.pos2);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return false; // Collision is disabled
    }
}
