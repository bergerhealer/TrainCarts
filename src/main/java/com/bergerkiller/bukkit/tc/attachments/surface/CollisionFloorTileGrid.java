package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.collections.FastTrackedUpdateSet;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stores a 2D block grid of the floor below a Player, made up of
 * {@link CollisionFloorTileShape} instances stored there. Includes
 * a merge algorithm to find the 'top' floor that the player actually
 * uses.<br>
 * <br>
 * One or more surfaces can be created and then individually kept updated
 * as the surface shapes change.
 */
class CollisionFloorTileGrid {
    private final ShulkerTracker shulkerCache;
    private final LongHashMap<TileColumn> columns = new LongHashMap<>();
    private final FastTrackedUpdateSet<TileColumn> changedColumns = new FastTrackedUpdateSet<>();

    public CollisionFloorTileGrid(ShulkerTracker shulkerCache) {
        this.shulkerCache = shulkerCache;
    }

    /**
     * Called every tick to update the display of shulkers for the tiles that have been added.
     * Also cleans up tiles from cleared surfaces (that have not received further updates).
     */
    public void update() {
        // Delete shapes that were set to be cleared
        for (TileColumn column : columns.values()) {
            column.cleanupClearedTiles();
        }

        // Process all the tiles that have changed
        changedColumns.forEachAndClear(column -> {
            if (column.shapes == TileColumnShapes.EMPTY) {
                // Despawn any shulkers and remove the tile
                column.despawnShulkers(shulkerCache);
                columns.remove(column.key);
            } else {
                // Spawn / move the shulkers for the tile
                column.updateShulkers(shulkerCache);
            }
        });
    }

    public void addFloorTile(CollisionSurface surface, int x, int z, CollisionFloorTileShape shape) {
        final long key = MathUtil.longHashToLong(x, z);
        columns.computeIfAbsent(key, k -> new TileColumn(x, z, key, changedColumns))
                .add(surface, shape);
    }

    public void removeFloorTile(CollisionSurface surface, int x, int z) {
        long key = MathUtil.longHashToLong(x, z);
        TileColumn column = columns.get(key);
        if (column != null) {
            column.remove(surface);
        }
    }

    /**
     * All information tracked for a single x/z block column.
     */
    private static final class TileColumn {
        public final int tile_x, tile_z;
        public final long key;
        public final FastTrackedUpdateSet.Tracker<TileColumn> tracker;
        public TileColumnShapes shapes = TileColumnShapes.EMPTY;
        private List<Shulker> shulkers = Collections.emptyList();

        public TileColumn(int x, int z, long key, FastTrackedUpdateSet<TileColumn> changedColumns) {
            this.tile_x = x;
            this.tile_z = z;
            this.key = key;
            this.tracker = changedColumns.track(this);
        }

        public void notifyChanged() {
            tracker.set(true);
        }

        public void add(CollisionSurface surface, CollisionFloorTileShape shape) {
            this.shapes = this.shapes.add(this, surface, shape);
        }

        public void remove(CollisionSurface surface) {
            this.shapes = this.shapes.remove(this, surface);
        }

        public void cleanupClearedTiles() {
            this.shapes = this.shapes.cleanupClearedTiles(this);
        }

        public void despawnShulkers(ShulkerTracker shulkerCache) {
            if (!shulkers.isEmpty()) {
                for (Shulker shulker : shulkers) {
                    shulkerCache.destroy(shulker);
                }
                shulkers = Collections.emptyList();
            }
        }

        public void updateShulkers(ShulkerTracker shulkerCache) {
            CollisionFloorTileShape shape = shapes.getShape();
            int requestedCount = shape.shulkerCount();
            int currentCount = shulkers.size();

            if (shulkers.isEmpty()) {
                shulkers = new ArrayList<>(4);
            }

            // If not enough shulkers, add more
            // Give them an initial position that makes them unfavored for selecting
            while (requestedCount > currentCount) {
                Shulker shulker = shulkerCache.spawn(BlockFace.UP);
                shulker.x = Double.NaN;
                shulker.y = -Double.MAX_VALUE;
                shulker.z = Double.NaN;
                shulkers.add(shulker);
                ++currentCount;
            }

            // Go by all positions that must be represented by a shulker and find the best-fit shulker,
            // and update it.
            shape.forEachShulker(tile_x, tile_z, (x, y, z) -> {
                Shulker bestShulker = null;
                for (Shulker shulker : shulkers) {
                    if (shulker.picked) {
                        continue;
                    }

                    // Right away pick a shulker that has the same x/z
                    if (shulker.x == x && shulker.z == z) {
                        shulker.y = y;
                        shulker.picked = true;
                        return;
                    }

                    if (bestShulker == null || (shulker.y <= y && shulker.y > bestShulker.y)) {
                        bestShulker = shulker;
                    }
                }
                if (bestShulker == null) {
                    throw new IllegalStateException("Not enough shulkers");
                }

                bestShulker.picked = true;
                bestShulker.x = x;
                bestShulker.y = y;
                bestShulker.z = z;
            });

            // If there are more shulkers than we need, despawn the non-picked ones
            // Reset picked back to false for all shulkers for next update
            for (Iterator<Shulker> iter = this.shulkers.iterator(); iter.hasNext();) {
                Shulker shulker = iter.next();
                if (shulker.picked) {
                    shulker.picked = false; // Reset for next time
                    shulker.scheduleMovement(); // Sync position later (if not spawned)
                } else {
                    shulkerCache.destroy(shulker);
                    iter.remove();
                }
            }
        }
    }

    /**
     * A collection of {@link CollisionFloorTileShape} values tracked for a single vertical column.
     * Automatically handles the merging of these shapes into a single "top" shape.
     */
    private interface TileColumnShapes {
        TileColumnShapes EMPTY = new TileColumnShapes() {
            @Override
            public CollisionFloorTileShape getShape() {
                throw new IllegalStateException("There are no tiles at this column");
            }

            @Override
            public TileColumnShapes cleanupClearedTiles(TileColumn column) {
                return this;
            }

            @Override
            public TileColumnShapes remove(TileColumn column, CollisionSurface surface) {
                return this;
            }

            @Override
            public TileColumnShapes add(TileColumn column, CollisionSurface surface, CollisionFloorTileShape shape) {
                column.notifyChanged();
                return new SingleTileColumn(surface, shape);
            }
        };

        /**
         * Gets the top-most (merged) shape active for this column.
         * This is what the player will actually see / walk on.
         *
         * @return top merged CollisionFloorTileShape
         */
        CollisionFloorTileShape getShape();

        /**
         * Removes tile shapes tied to surfaces that have been cleared,
         * but no new shape was added.
         *
         * @param column The owning TileColumn (to notify of changes)
         * @return Updated tile column, or same instance if this one
         *         was mutated / no shape for this view existed.
         */
        TileColumnShapes cleanupClearedTiles(TileColumn column);

        /**
         * Removes the shape that was set using a particular view
         *
         * @param column The owning TileColumn (to notify of changes)
         * @param surface CollisionSurface owner
         * @return Updated tile column, or same instance if this one
         *         was mutated / no shape for this view existed.
         */
        TileColumnShapes remove(TileColumn column, CollisionSurface surface);

        /**
         * Adds or sets a shape for a particular view
         *
         * @param column The owning TileColumn (to notify of changes)
         * @param surface CollisionSurface owner
         * @param shape Shape to set (not null)
         * @return Updated tile column, or same instance if this shape
         *         was already stored and was simply updated.
         */
        TileColumnShapes add(TileColumn column, CollisionSurface surface, CollisionFloorTileShape shape);
    }

    private static final class SingleTileColumn extends ShapeSlot implements TileColumnShapes {

        public SingleTileColumn(ShapeSlot slot) {
            super(slot);
        }

        public SingleTileColumn(CollisionSurface surface, CollisionFloorTileShape shape) {
            super(surface, shape);
        }

        @Override
        public CollisionFloorTileShape getShape() {
            return shape;
        }

        @Override
        public TileColumnShapes cleanupClearedTiles(TileColumn column) {
            if (this.isClearedFromSurface()) {
                column.notifyChanged();
                return TileColumnShapes.EMPTY;
            } else {
                return this;
            }
        }

        @Override
        public TileColumnShapes remove(TileColumn column, CollisionSurface surface) {
            if (surface == this.surface) {
                column.notifyChanged();
                return TileColumnShapes.EMPTY;
            } else {
                return this;
            }
        }

        @Override
        public TileColumnShapes add(TileColumn column, CollisionSurface surface, CollisionFloorTileShape shape) {
            if (surface == this.surface) {
                boolean changed = !this.shape.equals(shape);
                this.updateShape(shape);
                if (changed) {
                    column.notifyChanged();
                }
                return this;
            } else {
                return new MultiTileColumn(this).add(column, surface, shape);
            }
        }
    }

    private static final class MultiTileColumn implements TileColumnShapes {
        private final List<ShapeSlot> slots = new ArrayList<>(5);
        private CollisionFloorTileShape combinedShape = null;

        public MultiTileColumn(SingleTileColumn existing) {
            this.slots.add(new ShapeSlot(existing));
        }

        @Override
        public CollisionFloorTileShape getShape() {
            CollisionFloorTileShape combinedShape = this.combinedShape;
            if (combinedShape == null) {
                // Re-sort the slots from highest to lowest
                Collections.sort(this.slots);

                // Iterate all the tiles and merge into the combined shape while
                // the shape's min/max range is within the combined shape's range.
                Iterator<ShapeSlot> iter = this.slots.iterator();
                combinedShape = iter.next().shape;
                while (iter.hasNext()) {
                    CollisionFloorTileShape newShape = iter.next().shape;
                    if (newShape.getMaxY() > combinedShape.getMinY()) {
                        combinedShape = combinedShape.mergeWith(newShape);
                    } else {
                        break; // Tiles are too low, no merging will occur anymore
                    }
                }

                this.combinedShape = combinedShape;
            }
            return combinedShape;
        }

        @Override
        public TileColumnShapes cleanupClearedTiles(TileColumn column) {
            return remove(column, ShapeSlot::isClearedFromSurface);
        }

        @Override
        public TileColumnShapes remove(TileColumn column, CollisionSurface surface) {
            return remove(column, s -> s.surface == surface);
        }

        private TileColumnShapes remove(TileColumn column, Predicate<ShapeSlot> predicate) {
            if (!slots.removeIf(predicate)) {
                return this;
            }

            this.combinedShape = null;
            column.notifyChanged();
            int remainingSize = slots.size();
            if (remainingSize == 1) {
                return new SingleTileColumn(slots.get(0));
            } else if (remainingSize == 0) {
                return TileColumnShapes.EMPTY;
            } else {
                return this;
            }
        }

        @Override
        public TileColumnShapes add(TileColumn column, CollisionSurface surface, CollisionFloorTileShape shape) {
            // Update existing
            for (ShapeSlot slot : slots) {
                if (slot.surface == surface) {
                    boolean changed = !slot.shape.equals(shape);
                    slot.updateShape(shape);
                    if (changed) {
                        combinedShape = null;
                        column.notifyChanged();
                    }
                    return this;
                }
            }

            // Add a new slot
            combinedShape = null;
            slots.add(new ShapeSlot(surface, shape));
            column.notifyChanged();
            return this;
        }
    }

    /**
     * A single shape set by a single floor view.
     */
    private static class ShapeSlot implements Comparable<ShapeSlot> {
        public final CollisionSurface surface;
        public CollisionFloorTileShape shape;
        public int token;

        public ShapeSlot(ShapeSlot copy) {
            this.surface = copy.surface;
            this.shape = copy.shape;
            this.token = copy.token;
        }

        public ShapeSlot(CollisionSurface surface, CollisionFloorTileShape shape) {
            this.surface = surface;
            this.shape = shape;
            this.token = surface.getUpdateCounter();
        }

        protected boolean isClearedFromSurface() {
            return this.token != surface.getUpdateCounter();
        }

        protected void updateShape(CollisionFloorTileShape shape) {
            this.shape = shape;
            this.token = this.surface.getUpdateCounter();
        }

        @Override
        public int compareTo(CollisionFloorTileGrid.ShapeSlot shapeSlot) {
            return this.shape.compareTo(shapeSlot.shape);
        }
    }
}
