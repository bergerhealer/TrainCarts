package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

/**
 * Dynamically switches between showing the fishing rods for <= 1.19.3 clients (viaversion)
 * and display entity bounding boxes (>= 1.19.4)
 */
public class VirtualHybridBoundingBox extends VirtualBoundingBox {
    private OrientedBoundingBox lastBB;
    private VirtualFishingBoundingBox fishBox;
    private VirtualDisplayBoundingBox displayBox;

    public VirtualHybridBoundingBox(AttachmentManager manager) {
        super(manager);
    }

    @Override
    public void update(OrientedBoundingBox boundingBox) {
        lastBB = boundingBox;
        if (displayBox != null) {
            displayBox.update(boundingBox);
        }
        if (fishBox != null) {
            fishBox.update(boundingBox);
        }
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        if (displayBox != null) {
            displayBox.setGlowColor(color);
        }
        if (fishBox != null) {
            fishBox.setGlowColor(color);
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        if (viewer.supportsDisplayEntities()) {
            if (displayBox == null) {
                displayBox = new VirtualDisplayBoundingBox(manager);
                displayBox.setGlowColor(getGlowColor());
                displayBox.update(lastBB);
            }
            displayBox.spawn(viewer, motion);
        } else {
            if (fishBox == null) {
                fishBox = new VirtualFishingBoundingBox(manager);
                fishBox.setGlowColor(getGlowColor());
                fishBox.update(lastBB);
            }
            fishBox.spawn(viewer, motion);
        }
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        if (viewer.supportsDisplayEntities()) {
            if (displayBox != null) {
                displayBox.destroy(viewer);
            }
        } else {
            if (fishBox != null) {
                fishBox.destroy(viewer);
            }
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (displayBox != null) {
            displayBox.syncPosition(absolute);
        }
        if (fishBox != null) {
            fishBox.syncPosition(absolute);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (displayBox != null && displayBox.containsEntityId(entityId)) {
            return true;
        }
        if (fishBox != null && fishBox.containsEntityId(entityId)) {
            return true;
        }
        return false;
    }
}
