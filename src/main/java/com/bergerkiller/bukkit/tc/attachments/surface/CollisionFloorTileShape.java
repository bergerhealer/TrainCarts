package com.bergerkiller.bukkit.tc.attachments.surface;

/**
 * A type of shape rendered for a single block tile. For floors this
 * can be a single shulker (single y-value), two shulkers (axis-aligned slope)
 * or four shulkers (complex tile / diagonal slope).<br>
 * <br>
 * Only a single tile is kept below the player, so if multiple exist in a
 * vertical column, the highest tile is kept. For this logic there are
 * a couple of sort and merge operators.<br>
 * <br>
 * Implements {@link Comparable} in a way that it sorts by the maximum-y coordinate.
 * The highest tiles are put at the beginning of a list sorted this way.
 */
public interface CollisionFloorTileShape extends Comparable<CollisionFloorTileShape> {
    double DIFF_THRESHOLD = 0.05;

    /**
     * Gets the minimum floor Y value at this tile
     *
     * @return Minimum Y value
     */
    double getMinY();

    /**
     * Gets the maximum floor Y value at this tile
     *
     * @return Maximum Y value
     */
    double getMaxY();

    /**
     * Converts this tile, whatever it is, into the diagonal slope (4 heights)
     *
     * @return This tile upgraded to a ComplexTile
     */
    ComplexTile toComplexTile();

    /**
     * How many shulkers are required to spawn it
     *
     * @return Shulker count
     */
    int shulkerCount();

    /**
     * Iterates the shulker positions required for this shape
     *
     * @param x Column x block coordinate
     * @param z Column z block coordinate
     * @param consumer ShulkerPositionConsumer
     */
    void forEachShulker(int x, int z, ShulkerPositionConsumer consumer);

    /**
     * Performs a merge of this tile with another tile, and returns the merged
     * result that is the maximum of all floor Y-values. The result will be
     * reduced to a less complex shape if possible.
     *
     * @param other Other tile
     * @return Merged tile
     */
    CollisionFloorTileShape mergeWith(CollisionFloorTileShape other);

    @Override
    default int compareTo(CollisionFloorTileShape other) {
        // Sort highest Y-coordinate tiles at the beginning of the list (reversed)
        return -Double.compare(this.getMaxY(), other.getMaxY());
    }

    /**
     * A level floor tile with only a single Y-value
     */
    class Level implements CollisionFloorTileShape {
        public final double y;

        public Level(double y) {
            this.y = y;
        }

        @Override
        public double getMinY() {
            return y;
        }

        @Override
        public double getMaxY() {
            return y;
        }

        @Override
        public ComplexTile toComplexTile() {
            return new ComplexTile(y, y, y, y);
        }

        @Override
        public int shulkerCount() {
            return 1;
        }

        @Override
        public void forEachShulker(int x, int z, ShulkerPositionConsumer consumer) {
            consumer.accept(x + 0.5, y, z + 0.5);
        }

        @Override
        public CollisionFloorTileShape mergeWith(CollisionFloorTileShape other) {
            if (other instanceof Level) {
                return ((Level) other).y > this.y ? other : this;
            } else {
                return other.mergeWith(this);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Level) {
                return ((Level) o).y == this.y;
            } else {
                return false;
            }
        }
    }

    /**
     * An axis-aligned slope with two Y-values
     */
    class AlignedSlope implements CollisionFloorTileShape {
        public final AlignedAxis axis;
        public final double yp, yn;

        public AlignedSlope(AlignedAxis axis, double yp, double yn) {
            this.axis = axis;
            this.yp = yp;
            this.yn = yn;
        }

        @Override
        public double getMinY() {
            return Math.min(yp, yn);
        }

        @Override
        public double getMaxY() {
            return Math.max(yn, yp);
        }

        @Override
        public ComplexTile toComplexTile() {
            return (axis == AlignedAxis.X)
                    ? new ComplexTile(yn, yn, yp, yp)
                    : new ComplexTile(yn, yp, yn, yp);
        }

        @Override
        public int shulkerCount() {
            return 2;
        }

        @Override
        public void forEachShulker(int x, int z, ShulkerPositionConsumer consumer) {
            consumer.accept(x + 0.5 - axis.getDx(), yn, z + 0.5 - axis.getDz());
            consumer.accept(x + 0.5 + axis.getDx(), yp, z + 0.5 + axis.getDz());
        }

        @Override
        public CollisionFloorTileShape mergeWith(CollisionFloorTileShape other) {
            if (other instanceof Level) {
                Level level = (Level) other;
                double new_yp = Math.max(yp, level.y);
                double new_yn = Math.max(yn, level.y);

                // Not enough of a slope - return a level surface
                if (Math.abs(new_yp - new_yn) < DIFF_THRESHOLD) {
                    return new Level(Math.max(new_yp, new_yn));
                }

                return new AlignedSlope(axis, new_yp, new_yn);
            } else if (other instanceof AlignedSlope) {
                AlignedSlope slope = (AlignedSlope) other;
                if (this.axis == slope.axis) {
                    double new_yp = Math.max(yp, slope.yp);
                    double new_yn = Math.max(yn, slope.yn);

                    // Not enough of a slope - return a level surface
                    if (Math.abs(new_yp - new_yn) < DIFF_THRESHOLD) {
                        return new Level(Math.max(new_yp, new_yn));
                    }

                    return new AlignedSlope(axis, new_yp, new_yn);
                }
            }

            // Final fallback: turn both into complex tiles and merge those
            return this.toComplexTile().mergeWith(other.toComplexTile());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof AlignedSlope) {
                AlignedSlope slope = (AlignedSlope) o;
                return axis == slope.axis && yn == slope.yn && yp == slope.yp;
            } else {
                return false;
            }
        }
    }

    /**
     * A complex tile with four different Y-values. This can represent all sorts
     * of shapes, but unfortunately does require 4 shulkers to display it.
     */
    class ComplexTile implements CollisionFloorTileShape {
        public final double y_xn_zn, y_xn_zp, y_xp_zn, y_xp_zp;
        private final double y_min, y_max;

        public ComplexTile(double y_xn_zn, double y_xn_zp, double y_xp_zn, double y_xp_zp) {
            this.y_xn_zn = y_xn_zn;
            this.y_xn_zp = y_xn_zp;
            this.y_xp_zn = y_xp_zn;
            this.y_xp_zp = y_xp_zp;
            this.y_min = Math.min(Math.min(y_xn_zn, y_xn_zp), Math.min(y_xp_zn, y_xp_zp));
            this.y_max = Math.max(Math.max(y_xn_zn, y_xn_zp), Math.max(y_xp_zn, y_xp_zp));
        }

        @Override
        public double getMinY() {
            return y_min;
        }

        @Override
        public double getMaxY() {
            return y_max;
        }

        @Override
        public ComplexTile toComplexTile() {
            return this;
        }

        @Override
        public int shulkerCount() {
            return 4;
        }

        @Override
        public void forEachShulker(int x, int z, ShulkerPositionConsumer consumer) {
            consumer.accept(x + 0.5 - 0.25, y_xn_zn, z + 0.5 - 0.25);
            consumer.accept(x + 0.5 - 0.25, y_xn_zp, z + 0.5 + 0.25);
            consumer.accept(x + 0.5 + 0.25, y_xp_zn, z + 0.5 - 0.25);
            consumer.accept(x + 0.5 + 0.25, y_xp_zp, z + 0.5 + 0.25);
        }

        @Override
        public CollisionFloorTileShape mergeWith(CollisionFloorTileShape other) {
            return this.mergeWith(other.toComplexTile());
        }

        public CollisionFloorTileShape mergeWith(ComplexTile complex) {
            // Compute maximum of all four sub-tiles
            double new_y_xn_zn = Math.max(y_xn_zn, complex.y_xn_zn);
            double new_y_xn_zp = Math.max(y_xn_zp, complex.y_xn_zp);
            double new_y_xp_zn = Math.max(y_xp_zn, complex.y_xp_zn);
            double new_y_xp_zp = Math.max(y_xp_zp, complex.y_xp_zp);

            // After merging, we might find the result is not a diagonal shape at all, but a level
            // one or a slope along one axis. Detect those and return that instead of a diagonal
            // slope.
            boolean xn_same = Math.abs(new_y_xn_zn - new_y_xn_zp) < DIFF_THRESHOLD;
            boolean xp_same = Math.abs(new_y_xp_zn - new_y_xp_zp) < DIFF_THRESHOLD;
            boolean zn_same = Math.abs(new_y_xn_zn - new_y_xp_zn) < DIFF_THRESHOLD;
            boolean zp_same = Math.abs(new_y_xn_zp - new_y_xp_zp) < DIFF_THRESHOLD;
            if (xn_same && xp_same) {
                // Both z-axis X values are the same, X-aligned slope?
                double yn = Math.max(new_y_xn_zn, new_y_xn_zp);
                double yp = Math.max(new_y_xp_zn, new_y_xp_zp);
                if (zn_same && zp_same) {
                    // Level surface
                    return new Level(Math.max(yn, yp));
                } else {
                    // Is X-aligned slope.
                    return new AlignedSlope(AlignedAxis.X, yn, yp);
                }
            } else if (zn_same && zp_same) {
                // Both x-axis Z values are the same, but no other commonalities. Z-aligned slope.
                return new AlignedSlope(AlignedAxis.Z,
                        Math.max(new_y_xn_zn, new_y_xp_zn),
                        Math.max(new_y_xn_zp, new_y_xp_zp));
            } else {
                // Still a complex tile / diagonal slope
                return new ComplexTile(new_y_xn_zn, new_y_xn_zp, new_y_xp_zn, new_y_xp_zp);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof ComplexTile) {
                ComplexTile complex = (ComplexTile) o;
                return y_xn_zn == complex.y_xn_zn &&
                       y_xn_zp == complex.y_xn_zp &&
                       y_xp_zn == complex.y_xp_zn &&
                       y_xp_zp == complex.y_xp_zp;
            } else {
                return false;
            }
        }
    }

    enum AlignedAxis {
        X(0.25, 0.0),
        Z(0.0, 0.25);

        private final double dx, dz;

        AlignedAxis(double dx, double dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public double getDx() {
            return dx;
        }

        public double getDz() {
            return dz;
        }
    }

    @FunctionalInterface
    interface ShulkerPositionConsumer {
        void accept(double x, double y, double z);
    }
}
