package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import org.bukkit.block.BlockFace;

/**
 * A single collision surface. This surface can be cleared and then
 * surface elements can be added to it, without affecting other collision
 * surfaces that have been created.
 */
public interface CollisionSurface {
    CollisionSurface DISABLED = new CollisionSurface() {
        @Override
        public int getUpdateCounter() {
            return 0;
        }

        @Override
        public void addSurface(OrientedBoundingBox surface) {
        }

        @Override
        public void addFloorTile(int x, int z, CollisionFloorTileShape shape) {
        }

        @Override
        public void removeFloorTile(int x, int z) {
        }

        @Override
        public void addWallTile(BlockFace face, int x, int y, double value) {
        }

        @Override
        public void removeWallTile(BlockFace face, int x, int y) {
        }

        @Override
        public void clear() {
        }
    };

    /**
     * Gets the update counter state. This is incremented every time
     * {@link #clear()} is called.
     *
     * @return Update counter state value
     */
    int getUpdateCounter();

    /**
     * Adds a surface represented as an oriented bounding box. The surface
     * is automatically transformed and quantized into suitable
     * wall/ceiling/floor tiles. If the player is below a flat surface, regardless
     * of orientation, it turns into ceiling tiles.
     *
     * @param surface Surface bounding box - the bottom face is used to represent
     *                the surface.
     */
    void addSurface(OrientedBoundingBox surface);

    /**
     * Adds or updates a single shaped floor tile that is a part of this surface.
     * Only a single tile can exist per x/z coordinate.
     *
     * @param x X-block coordinate
     * @param z Z-block coordinate
     * @param shape CollisionFloorTileShape
     */
    void addFloorTile(int x, int z, CollisionFloorTileShape shape);

    /**
     * Removes any shaped floor tile that was added at a particular x/z block
     * coordinate previously. Can be called to undo a previous added shape.
     * Practically not used, since you can just call clear() and add all shapes
     * after, which is a lot more reliable.
     *
     * @param x X-block coordinate
     * @param z Z-block coordinate
     */
    void removeFloorTile(int x, int z);

    /**
     * Adds a full-block wall tile, facing into a particular direction.
     * The x/y are relative to the facing direction (horizontally y==y and x is the
     * other axis, vertically the x==x and y is z).
     *
     * @param face Face direction of the player looking at the wall (opposite of normal)
     * @param x Face-relative X tile
     * @param y Face-relative Y tile
     * @param value Axis value of the wall, depending on facing (the other axis)
     */
    void addWallTile(BlockFace face, int x, int y, double value);

    /**
     * Removes a previously added full-block wall tile, facing into a particular direction.
     * The x/y are relative to the facing direction (horizontally y==y and x is the
     * other axis, vertically the x==x and y is z).
     *
     * @param face Face direction of the player looking at the wall (opposite of normal)
     * @param x Face-relative X tile
     * @param y Face-relative Y tile
     */
    void removeWallTile(BlockFace face, int x, int y);

    /**
     * Clears this surface. All shapes that have been added so far will be removed
     * in the next tick, unless the shape is updated again right after clearing.<br>
     * <br>
     * A typical update loop involves calling this clear() method and then
     * calling methods like {@link #addFloorTile(int, int, CollisionFloorTileShape)}
     * for all the tiles to render.<br>
     * <br>
     * This method also acts as the remove method. After calling clear, this
     * surface instance can be safely discarded. It will automatically get
     * cleaned up internally.
     */
    void clear();
}
