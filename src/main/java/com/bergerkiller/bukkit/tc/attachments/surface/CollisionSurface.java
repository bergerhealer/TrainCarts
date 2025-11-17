package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;

/**
 * A single collision surface. When first created the surface is cleared.
 * Call {@link #setShape(OrientedBoundingBox)} to set the position, orientation
 * and size of the 2D surface. Then it becomes part of the collision surface
 * simulation, and players will be able to walk on it.
 */
public interface CollisionSurface {
    /** Surface used for disconnected players or when the plugin is disabled */
    CollisionSurface DISABLED = new CollisionSurface() {
        @Override
        public int getUpdateCounter() {
            return 0;
        }

        @Override
        public void setShape(OrientedBoundingBox surface) {
        }

        @Override
        public void remove() {
        }
    };
    /** Default distance from which shulkers spawn in when the parameter is omitted */
    int DEFAULT_SHULKER_VIEW_DISTANCE = 8;

    /**
     * Gets the update counter state. This is incremented every time
     * {@link #remove()} or {@link #setShape(OrientedBoundingBox)} is called.
     *
     * @return Update counter state value
     */
    int getUpdateCounter();

    /**
     * Sets the surface shape, represented as an oriented bounding box.
     * The surface is automatically transformed and quantized into suitable
     * wall/ceiling/floor tiles. If the player is below a flat surface, regardless
     * of orientation, it turns into ceiling tiles.<br>
     * <br>
     * Call this every tick to make a moving surface. A moving surface is simulated
     * separately using the {@link AttachmentViewer.MovementController}
     *
     * @param shape New surface shape
     */
    void setShape(OrientedBoundingBox shape);

    /**
     * Removes this surface. If any shape has been added before it will be removed
     * in the next tick, unless the shape is updated again right after clearing.<br>
     * <br>
     * It is allowed to call {@link #setShape(OrientedBoundingBox)} afterwards, which
     * will re-add this surface.
     */
    void remove();
}
