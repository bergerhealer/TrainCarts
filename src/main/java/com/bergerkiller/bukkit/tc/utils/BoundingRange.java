package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * A numeric range between two values. Can be inclusive or exclusive,
 * and offers methods to mutate it.
 */
public class BoundingRange {
    private final double min;
    private final double max;
    private final boolean exclusive;

    private BoundingRange(double min, double max, boolean exclusive) {
        this.min = min;
        this.max = max;
        this.exclusive = exclusive;
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public boolean isInside(double value) {
        return (value >= min && value <= max) != exclusive;
    }

    /**
     * Calculates the closest distance between this range and
     * the value specified. Returns 0 if inside the range.
     *
     * @param value
     * @return distance
     */
    public double distance(double value) {
        if (value <= min) {
            return exclusive ? 0.0 : (min - value);
        } else if (value >= max) {
            return exclusive ? 0.0 : (value - max);
        } else if (exclusive) {
            return Math.min(value - min, max - value);
        } else {
            return 0.0;
        }
    }

    /**
     * Gets whether the distance between the min/max range bounds is 0.
     *
     * @return True if this range is zero in length
     */
    public boolean isZeroLength() {
        return min == max;
    }

    /**
     * Gets whether this bounding range is inclusive, that is, values
     * that fall between min and max pass
     *
     * @return True if inclusive
     */
    public boolean isInclusive() {
        return !exclusive;
    }

    /**
     * Gets whether this bounding range is excousive, that is, only
     * values that fall outside min and max pass
     *
     * @return True if exclusive
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Inverts the bounding range, inclusive become exclusive and
     * other way around.
     *
     * @return inverted bounding range
     */
    public BoundingRange invert() {
        return new BoundingRange(min, max, !exclusive);
    }

    /**
     * Squares the min/max bounding ranges. Note: if min/max values
     * are negative, preserves that negative sign.
     *
     * @return squared range
     */
    public BoundingRange squared() {
        double new_min = min * min;
        double new_max = max * max;
        if (min < 0.0) {
            new_min = -new_min;
        }
        if (max < 0.0) {
            new_max = -new_max;
        }
        return new BoundingRange(new_min, new_max, exclusive);
    }

    /**
     * Grows this bounding range with the bounds as specified by another.
     * This can be used to add a 'delta' to the coordinates.
     *
     * @param amount Bounding range by which to grow
     * @return result of adding
     */
    public BoundingRange add(BoundingRange amount) {
        // Figure out by how much to adjust the min and max ranges
        double new_min, new_max;
        if (amount.isZeroLength()) {
            if (amount.min > 0) {
                new_min = min;
                new_max = max + amount.min;
            } else {
                new_min = min + amount.min;
                new_max = max;
            }
        } else {
            new_min = min + amount.min;
            new_max = max + amount.max;
        }

        // If min exceeds max, keep whichever is closest to 0 as a single point
        // We must do this in case one of the units is Double.MAX_VALUE
        if (new_min > new_max) {
            if (Math.abs(new_max) < Math.abs(new_min)) {
                new_min = new_max;
            } else {
                new_max = new_min;
            }
        }

        return new BoundingRange(new_min, new_max, exclusive != amount.exclusive);
    }

    /**
     * Creates a new bounding range between one value and another.
     *
     * @param min Minimum range, inclusive
     * @param max Maximum range, inclusive
     * @return bounding range
     */
    public static BoundingRange create(double min, double max) {
        return new BoundingRange(min, max, false);
    }

    /**
     * Stores 3 bounding box axis ranges
     */
    public static final class Axis {
        public World world;
        public BoundingRange x, y, z;

        private Axis() {
        }

        public boolean isInside(Vector position) {
            return x.isInside(position.getX()) &&
                   y.isInside(position.getY()) &&
                   z.isInside(position.getZ());
        }

        public double distanceSquared(Vector position) {
            double dx = x.distance(position.getX());
            double dy = y.distance(position.getY());
            double dz = z.distance(position.getZ());
            return dx * dx + dy * dy + dz * dz;
        }

        public double distanceSquared(Location location) {
            double dx = x.distance(location.getX());
            double dy = y.distance(location.getY());
            double dz = z.distance(location.getZ());
            return dx * dx + dy * dy + dz * dz;
        }

        public static Axis empty() {
            return new Axis();
        }

        /**
         * Tries to figure out the initial range axis on the world where a sender is at.
         * This is used when a sender-relative command is executed.<br>
         * <br>
         * If the sender has no location, such as the terminal command sender,
         * an {@link #empty()} axis is returned. Use {@link #isValid()} to check.
         *
         * @param sender Command Sender
         * @return location of the sender, or null if it could not be found
         */
        public static Axis forSender(CommandSender sender) {
            if (sender instanceof BlockCommandSender) {
                return forBlock(((BlockCommandSender) sender).getBlock());
            } else if (sender instanceof Entity) {
                return forPoint(((Entity) sender).getLocation());
            } else {
                return empty();
            }
        }

        public static Axis forPoint(Location location) {
            Axis axis = new Axis();
            axis.world = location.getWorld();
            axis.x = create(location.getX(), location.getX());
            axis.y = create(location.getY(), location.getY());
            axis.z = create(location.getZ(), location.getZ());
            return axis;
        }

        public static Axis forBlock(Block block) {
            double x = (double) block.getX();
            double y = (double) block.getY();
            double z = (double) block.getZ();
            Axis axis = new Axis();
            axis.world = block.getWorld();
            axis.x = create(x, x + 1.0);
            axis.y = create(y, y + 1.0);
            axis.z = create(z, z + 1.0);
            return axis;
        }
    }
}
