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
        public boolean isSimulated() {
            return false;
        }

        @Override
        public boolean isShulkerGrid() {
            return false;
        }

        @Override
        public void setSimulated(boolean simulated) {
        }

        @Override
        public void setUseShulkerGrid(boolean useShulkers) {
        }

        @Override
        public OrientedBoundingBox getShape() {
            return null;
        }

        @Override
        public String getDebugName() {
            return "";
        }

        @Override
        public void setDebugName(String name) {
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
     * Gets whether this surface is simulated using the {@link PlayerCollisionSolver}. If this is the case, the player movement
     * controller is used to simulate player walking server-side. If false, then this surface is either no longer present, or
     * it is simulated using static shulkers instead.
     *
     * @return True if simulated
     */
    boolean isSimulated();

    /**
     * Gets whether this surface is presently rendered onto the the "shulker grid". This is a 2D grid on each axis of the player
     * where shulker boxes are spawned to restrict movement and provide a walking surface. This only occurs while this
     * surface is stationary and {@link #setUseShulkerGrid(boolean)} is set to true.
     *
     * @return True if this surface is rendered onto a shulker grid
     */
    boolean isShulkerGrid();

    /**
     * Sets whether this surface is simulated server-side. When set to true and the surface is not stationary or
     * using shulkers is disabled, the player movement will be server-side controlled and simulated.
     *
     * @param simulated True if simulated
     */
    void setSimulated(boolean simulated);

    /**
     * Sets whether this surface should be represented as shulker boxes on a grid when not in motion
     *
     * @param useShulkers True to spawn shulkers when stationary
     */
    void setUseShulkerGrid(boolean useShulkers);

    /**
     * Gets the surface shape this surface is set to. Returns null when this surface is removed, such
     * as {@link #DISABLED}.
     * Do not modify this returned shape.
     *
     * @return OrientedBoundingBox
     */
    OrientedBoundingBox getShape();

    /**
     * Gets the name set using {@link #setDebugName(String)}. This is used for debugging purposes only.
     * Returns null if none was set.
     *
     * @return Debug name, or null if not set
     */
    String getDebugName();

    /**
     * Assigns this surface a unique name, so that it can more easily be debugged.
     *
     * @param name Debug name to set
     */
    void setDebugName(String name);

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
