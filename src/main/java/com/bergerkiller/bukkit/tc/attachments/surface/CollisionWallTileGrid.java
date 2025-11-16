package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.collections.FastTrackedUpdateSet;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stores a 2D block grid of the 5 walls above and around the Player, made up of
 * offset instances stored there. Each of the 5 walls are their own instance.<br>
 * <br>
 * One or more surfaces can be created and then individually kept updated
 * as the surface shapes change.<br>
 * <br>
 * Note: tiles are stored as x/y coordinates. These are arbitrary, but for whatever face
 * axis this tile grid is, it tries to make this x/y "relative" to this face direction.
 * The value will be the missing coordinate's absolute value.
 */
final class CollisionWallTileGrid {
    private final ShulkerTracker shulkerCache;
    private final WallAxisLogic axisLogic;
    private final LongHashMap<TileColumn> columns = new LongHashMap<>();
    private final FastTrackedUpdateSet<TileColumn> changedColumns = new FastTrackedUpdateSet<>();

    public CollisionWallTileGrid(ShulkerTracker shulkerCache, BlockFace face) {
        this.shulkerCache = shulkerCache;
        this.axisLogic = WallAxisLogic.fromFace(face);
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
            if (column.slots.isEmpty()) {
                // Despawn any shulkers and remove the tile
                column.despawnShulkers(shulkerCache);
                columns.remove(column.key);
            } else {
                // Spawn / move the shulkers for the tile
                column.updateShulkers(shulkerCache, axisLogic);
            }
        });
    }

    public boolean isEmpty() {
        return columns.size() == 0;
    }

    public void addWallTile(CollisionSurface surface, int x, int y, double value) {
        final long key = MathUtil.longHashToLong(x, y);
        columns.computeIfAbsent(key, k -> new TileColumn(x, y, key, changedColumns))
                .add(surface, value);
    }

    public void removeWallTile(CollisionSurface surface, int x, int y) {
        long key = MathUtil.longHashToLong(x, y);
        TileColumn column = columns.get(key);
        if (column != null) {
            column.remove(surface);
        }
    }

    private static final class TileColumn {
        public final int tile_x, tile_y;
        public final long key;
        public final FastTrackedUpdateSet.Tracker<TileColumn> tracker;
        private List<TileSlot> slots = Collections.emptyList();
        private boolean slotsMutable = false;
        private Shulker shulker = null;

        public TileColumn(int x, int y, long key, FastTrackedUpdateSet<TileColumn> changedColumns) {
            this.tile_x = x;
            this.tile_y = y;
            this.key = key;
            this.tracker = changedColumns.track(this);
        }

        public void notifyChanged() {
            tracker.set(true);
        }

        public void add(CollisionSurface surface, double value) {
            // Update existing
            for (TileSlot slot : slots) {
                if (slot.surface == surface) {
                    slot.token = surface.getUpdateCounter();
                    if (slot.value != value) {
                        slot.value = value;
                        notifyChanged();
                    }
                    return;
                }
            }

            // Add a new one
            if (slotsMutable) {
                slots.add(new TileSlot(surface, value));
            } else if (slots.isEmpty()) {
                slots = Collections.singletonList(new TileSlot(surface, value));
            } else {
                slots = new ArrayList<>(slots);
                slots.add(new TileSlot(surface, value));
                slotsMutable = true;
            }

            notifyChanged();
        }

        private void removeIf(Predicate<TileSlot> condition) {
            if (slotsMutable) {
                if (slots.removeIf(condition)) {
                    notifyChanged();
                }
            } else if (slots.size() == 1 && condition.test(slots.get(0))) {
                slots = Collections.emptyList();
                notifyChanged();
            }
        }

        public void remove(CollisionSurface surface) {
            removeIf(slot -> slot.surface == surface);
        }

        public void cleanupClearedTiles() {
            removeIf(slot -> slot.token != slot.surface.getUpdateCounter());
        }

        public void despawnShulkers(ShulkerTracker shulkerCache) {
            Shulker shulker = this.shulker;
            if (shulker != null) {
                shulkerCache.destroy(shulker);
                this.shulker = null;
            }
        }

        public void updateShulkers(ShulkerTracker shulkerCache, WallAxisLogic axisLogic) {
            Shulker shulker = this.shulker;
            if (shulker == null) {
                this.shulker = shulker = shulkerCache.spawn(axisLogic.getPushAxis());
                axisLogic.applyShulkerTile(shulker, tile_x, tile_y);
            }

            // If more than one slot (and thus mutable), sort it based on value nearest-first
            // If the axis modifier is negative, perform the sort in reverse (highest value is nearest)
            if (slots.size() > 1) {
                axisLogic.sortNearest(slots);
            }

            // Apply value of the first slot (nearest)
            axisLogic.applyShulkerValue(shulker, slots.get(0).value);
            shulker.scheduleMovement();
        }
    }

    public static final class TileSlot implements Comparable<TileSlot> {
        public final CollisionSurface surface;
        public int token;
        public double value;

        public TileSlot(CollisionSurface surface, double value) {
            this.surface = surface;
            this.token = surface.getUpdateCounter();
            this.value = value;
        }

        @Override
        public int compareTo(CollisionWallTileGrid.TileSlot other) {
            return Double.compare(this.value, other.value);
        }
    }

    interface WallAxisLogic {
        static WallAxisLogic fromFace(BlockFace face) {
            final BlockFace pushAxis = face.getOppositeFace();
            switch (face) {
                case NORTH:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            slots.sort(Collections.reverseOrder());
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.x = x + 0.5;
                            shulker.y = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.z = value;
                        }
                    };
                case SOUTH:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            Collections.sort(slots);
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.x = x + 0.5;
                            shulker.y = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.z = value;
                        }
                    };
                case WEST:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            slots.sort(Collections.reverseOrder());
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.z = x + 0.5;
                            shulker.y = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.x = value;
                        }
                    };
                case EAST:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            Collections.sort(slots);
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.z = x + 0.5;
                            shulker.y = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.x = value;
                        }
                    };
                case DOWN:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            slots.sort(Collections.reverseOrder());
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.x = x + 0.5;
                            shulker.z = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.y = value;
                        }
                    };
                case UP:
                    return new WallAxisLogic() {
                        @Override
                        public BlockFace getPushAxis() {
                            return pushAxis;
                        }

                        @Override
                        public void sortNearest(List<TileSlot> slots) {
                            Collections.sort(slots);
                        }

                        @Override
                        public void applyShulkerTile(Shulker shulker, int x, int y) {
                            shulker.x = x + 0.5;
                            shulker.z = y + 0.5;
                        }

                        @Override
                        public void applyShulkerValue(Shulker shulker, double value) {
                            shulker.y = value;
                        }
                    };
                default:
                    throw new IllegalArgumentException("Invalid face for wall: " + face);
            }
        }

        BlockFace getPushAxis();
        void sortNearest(List<TileSlot> slots);
        void applyShulkerTile(Shulker shulker, int x, int y);
        void applyShulkerValue(Shulker shulker, double value);
    }
}
