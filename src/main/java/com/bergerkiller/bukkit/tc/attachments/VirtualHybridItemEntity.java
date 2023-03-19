package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.transform.HybridItemTransformType;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Uses ViaVersion to automatically decide whether to spawn an armorstand or a
 * display entity. Tracks both types of entities for the different types of
 * clients that connect. Will not track it until at least one viewer
 * demands it, to avoid wasting resources.
 */
public class VirtualHybridItemEntity extends VirtualSpawnableObject {
    // This information is tracked when either type needs to be updated
    private HybridItemTransformType transformType = HybridItemTransformType.ARMORSTAND_HEAD;
    private ItemStack item = null;
    private Matrix4x4 transform = null;
    // Avoids creating a bunch of wasteful matrices all the time
    private final Matrix4x4 tmpArmorstandTransform = Matrix4x4.identity();
    private final Matrix4x4 tmpDisplayTransform = Matrix4x4.identity();
    // Initialized on demand
    private VirtualArmorStandItemEntity armorstand = null;
    private VirtualDisplayItemEntity display = null;

    public VirtualHybridItemEntity(AttachmentManager manager) {
        super(manager);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (display != null && display.containsEntityId(entityId)) {
            return true;
        }
        if (armorstand != null && armorstand.containsEntityId(entityId)) {
            return true;
        }
        return false;
    }

    public void setItem(HybridItemTransformType transformType, ItemStack item) {
        this.transformType = transformType;
        this.item = item;
        if (armorstand != null) {
            armorstand.setItem(transformType.armorStandTransform(), item);
        }
        if (display != null) {
            display.setScale(transformType.displayScale());
            display.setItem(transformType.displayMode(), item);
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        if (viewer.supportsDisplayEntities()) {
            if (display == null) {
                if (transform == null) {
                    throw new IllegalStateException("Spawn called before updatePosition");
                }
                display = new VirtualDisplayItemEntity(manager);
                display.setGlowColor(getGlowColor());
                display.setScale(transformType.displayScale());
                display.setItem(transformType.displayMode(), item);
                display.updatePosition(transformType.transformDisplay(tmpDisplayTransform, transform));
                display.syncPosition(true);
            }
            display.spawn(viewer, motion);
        } else {
            if (armorstand == null) {
                if (transform == null) {
                    throw new IllegalStateException("Spawn called before updatePosition");
                }
                armorstand = new VirtualArmorStandItemEntity(manager);
                armorstand.setGlowColor(getGlowColor());
                armorstand.setItem(transformType.armorStandTransform(), item);
                armorstand.updatePosition(transformType.transformArmorStand(tmpArmorstandTransform, transform));
                armorstand.syncPosition(true);
            }
            armorstand.spawn(viewer, motion);
        }
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        if (viewer.supportsDisplayEntities()) {
            if (display != null) {
                display.destroy(viewer);
            }
        } else {
            if (armorstand != null) {
                armorstand.destroy(viewer);
            }
        }
    }

    @Override
    protected void applyGlowing(ChatColor color) {
        if (display != null) {
            display.setGlowColor(color);
        }
        if (armorstand != null) {
            armorstand.setGlowColor(color);
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        this.transform = transform; // Keep for late init during spawn()

        if (display != null) {
            display.updatePosition(transformType.transformDisplay(tmpDisplayTransform, transform));
        }
        if (armorstand != null) {
            armorstand.updatePosition(transformType.transformArmorStand(tmpArmorstandTransform, transform));
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (display != null) {
            display.syncPosition(absolute);
        }
        if (armorstand != null) {
            armorstand.syncPosition(absolute);
        }
    }
}
