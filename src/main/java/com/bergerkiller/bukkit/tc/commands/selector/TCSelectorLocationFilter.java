package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.storage.OfflineMember;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;

/**
 * Filters the train properties by world and/or coordinates specified in
 * the selector query
 */
class TCSelectorLocationFilter {
    private static final Map<String, SelectorConsumer> CONSUMERS = new HashMap<>();
    static {
        CONSUMERS.put("world", (filter, condition) -> {
            filter.world = Bukkit.getWorld(condition.getValue()); // Don't allow multiple
            if (filter.world == null) {
                throw new SelectorException("World '" + condition.getValue() + "' does not exist");
            }
        });
        CONSUMERS.put("x", (filter, condition) -> filter.initAxis().x = condition.getBoundingRange());
        CONSUMERS.put("y", (filter, condition) -> filter.initAxis().y = condition.getBoundingRange());
        CONSUMERS.put("z", (filter, condition) -> filter.initAxis().z = condition.getBoundingRange());
        CONSUMERS.put("dx", (filter, condition) -> {
            BoundingRange.Axis range = filter.initAxis();
            if (range.x == null) {
                throw new SelectorException("No X-coordinate was specified");
            }
            range.x = range.x.add(condition.getBoundingRange());
        });
        CONSUMERS.put("dy", (filter, condition) -> {
            BoundingRange.Axis range = filter.initAxis();
            if (range.y == null) {
                throw new SelectorException("No Y-coordinate was specified");
            }
            range.y = range.y.add(condition.getBoundingRange());
        });
        CONSUMERS.put("dz", (filter, condition) -> {
            BoundingRange.Axis range = filter.initAxis();
            if (range.z == null) {
                throw new SelectorException("No Z-coordinate was specified");
            }
            range.z = range.z.add(condition.getBoundingRange());
        });
        CONSUMERS.put("distance", (filter, condition) -> {
            filter.initAxis();
            filter.distanceSquared = condition.getBoundingRange().squared();
        });
    }

    private CommandSender sender = null;
    private World world = null;
    private BoundingRange.Axis range = null;
    private BoundingRange distanceSquared = null; // check inside by default

    public void read(CommandSender sender, List<SelectorCondition> conditions) throws SelectorException {
        this.sender = sender;

        for (Iterator<SelectorCondition> iter = conditions.iterator(); iter.hasNext(); ) {
            SelectorCondition condition = iter.next();
            SelectorConsumer consumer = CONSUMERS.get(condition.getKey());
            if (consumer != null) {
                consumer.accept(this, condition);
                iter.remove();
                continue;
            }
        }

        // If a filter was specified, do some extra work
        if (range != null) {
            if (world == null) {
                world = range.world;
            }
            if (world == null) {
                throw new SelectorException("World must be specified when selecting trains by coordinates");
            }
            if (range.x == null) {
                throw new SelectorException("No X-coordinate was specified");
            }
            if (range.y == null) {
                throw new SelectorException("No Y-coordinate was specified");
            }
            if (range.z == null) {
                throw new SelectorException("No Z-coordinate was specified");
            }
        }
    }

    private BoundingRange.Axis initAxis() {
        if (this.range == null) {
            this.range = BoundingRange.Axis.forSender(sender);
        }
        return this.range;
    }

    public boolean hasFilters() {
        return world != null || distanceSquared != null || range != null;
    }

    public boolean filter(TrainProperties properties) {
        // If only world name and no range filter was specified, only check those
        if (range == null) {
            return isOnWorld(properties, world);
        } else {
            return forAllCartPositions(properties, world, this::matchCart);
        }
    }

    public static boolean isOnWorld(TrainProperties properties, World world) {
        // Easy mode: train is loaded
        MinecartGroup group = properties.getHolder();
        if (group != null) {
            return group.getWorld() == world;
        }

        // Hard mode: check offline train storage
        OfflineGroup offlineGroup = OfflineGroupManager.findGroup(properties.getTrainName());
        return offlineGroup != null && world.getUID().equals(offlineGroup.worldUUID);
    }

    /**
     * Sends all cart positions of a train past a position-accepting sink. If the sink function
     * returns true, then the loop is cut short and this function returns true as well. If no
     * carts were found, they're on the wrong world, or none match sink true, then this
     * function returns false
     *
     * @param properties TrainProperties to iterate the cart positions of
     * @param world World to filter by
     * @param func Sink function
     * @return True if one or more cart was accepted by the sink
     */
    public static boolean forAllCartPositions(TrainProperties properties, World world, CartPositionSink func) {
        // Checks world and range
        MinecartGroup group = properties.getHolder();
        if (group != null) {
            // Easy mode: train is loaded
            if (group.getWorld() != world) {
                return false;
            }

            // If any of the carts of the train are within range, pass
            for (MinecartMember<?> member : group) {
                if (func.apply(member.getEntity().loc.vector())) {
                    return true;
                }
            }
            return false;
        } else {
            // Hard mode: check offline train storage
            OfflineGroup offlineGroup = OfflineGroupManager.findGroup(properties.getTrainName());
            if (offlineGroup == null) {
                return false; // Weird!
            }
            if (!world.getUID().equals(offlineGroup.worldUUID)) {
                return false;
            }

            // if any of the carts of the train are within range, pass
            // Assume middle of the chunk at y = 128.
            // TODO: Perhaps store exact coordinates in the offline storage?
            for (OfflineMember member : offlineGroup.members) {
                Vector cartPosition = new Vector((member.cx << 4) + 8, /* x */
                                                 128.0, /* y */
                                                 (member.cz << 4) + 8 /* z */ );
                if (func.apply(cartPosition)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean matchCart(Vector cartPosition) {
        if (distanceSquared == null) {
            // Check inside range
            return range.isInside(cartPosition);
        } else {
            // Compute distance and check within
            return distanceSquared.isInside(range.distanceSquared(cartPosition));
        }
    }

    @FunctionalInterface
    private static interface SelectorConsumer {
        void accept(TCSelectorLocationFilter filter, SelectorCondition condition) throws SelectorException;
    }

    @FunctionalInterface
    public static interface CartPositionSink {
        boolean apply(Vector position);
    }
}
