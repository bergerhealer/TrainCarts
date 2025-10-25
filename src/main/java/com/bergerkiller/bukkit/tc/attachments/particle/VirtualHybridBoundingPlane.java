package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

/**
 * Shows the bottom plane of a bounding box<br>
 * <br>
 * Dynamically switches between showing the fishing rods for &lt;= 1.19.3 clients (viaversion)
 * and display entity bounding boxes (>= 1.19.4)
 */
public class VirtualHybridBoundingPlane extends VirtualBoundingBox {
    private OrientedBoundingBox lastBB;
    private VirtualFishingBoundingPlane fishPlane;
    private VirtualDisplayBoundingPlane displayPlane;

    public VirtualHybridBoundingPlane(AttachmentManager manager) {
        super(manager);
    }

    @Override
    public void update(OrientedBoundingBox boundingBox) {
        lastBB = boundingBox;
        if (displayPlane != null) {
            displayPlane.update(boundingBox);
        }
        if (fishPlane != null) {
            fishPlane.update(boundingBox);
        }
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        if (displayPlane != null) {
            displayPlane.setGlowColor(color);
        }
        if (fishPlane != null) {
            fishPlane.setGlowColor(color);
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        if (viewer.supportsDisplayEntities()) {
            if (displayPlane == null) {
                displayPlane = new VirtualDisplayBoundingPlane(manager);
                displayPlane.setGlowColor(getGlowColor());
                displayPlane.update(lastBB);
            }
            displayPlane.spawn(viewer, motion);
        } else {
            if (fishPlane == null) {
                fishPlane = new VirtualFishingBoundingPlane(manager);
                fishPlane.setGlowColor(getGlowColor());
                fishPlane.update(lastBB);
            }
            fishPlane.spawn(viewer, motion);
        }
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        if (viewer.supportsDisplayEntities()) {
            if (displayPlane != null) {
                displayPlane.destroy(viewer);
            }
        } else {
            if (fishPlane != null) {
                fishPlane.destroy(viewer);
            }
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (displayPlane != null) {
            displayPlane.syncPosition(absolute);
        }
        if (fishPlane != null) {
            fishPlane.syncPosition(absolute);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (displayPlane != null && displayPlane.containsEntityId(entityId)) {
            return true;
        }
        if (fishPlane != null && fishPlane.containsEntityId(entityId)) {
            return true;
        }
        return false;
    }
}
