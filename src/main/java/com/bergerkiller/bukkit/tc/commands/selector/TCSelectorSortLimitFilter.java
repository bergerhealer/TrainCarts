package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;

/**
 * Parses the 'sort' and 'limit' selector, and processes the stream using
 * this information accordingly.
 */
class TCSelectorSortLimitFilter {
    private BoundingRange.Axis senderBounds = null;
    private SortMode sort = SortMode.ARBITRARY;
    private int limit = -1;

    public void read(CommandSender sender, List<SelectorCondition> conditions) throws SelectorException {
        for (Iterator<SelectorCondition> iter = conditions.iterator(); iter.hasNext(); ) {
            SelectorCondition condition = iter.next();

            if (condition.getKey().equals("sort")) {
                try {
                    this.sort = SortMode.valueOf(condition.getValue().toUpperCase(Locale.ENGLISH));
                } catch (IllegalArgumentException ex) {
                    throw new SelectorException("Unknown sort option: " + condition.getValue());
                }
                this.senderBounds = BoundingRange.Axis.forSender(sender);
                if (this.senderBounds.world == null) {
                    throw new SelectorException("Sort by distance can only be used executing as a Player or CommandBlock");
                }
                iter.remove();
                continue;
            }

            if (condition.getKey().equals("limit")) {
                this.limit = (int) condition.getDouble(); //TODO: Special int parsing?
                iter.remove();
                continue;
            }
        }
    }

    public Stream<TrainProperties> apply(Stream<TrainProperties> stream) {
        stream = sort.modify(senderBounds, stream);
        if (limit >= 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }

    private static double findDistanceSquaredTo(TrainProperties properties, BoundingRange.Axis senderBounds, double altValue) {
        final DoubleHolder result = new DoubleHolder();
        result.value = Double.MAX_VALUE;
        TCSelectorLocationFilter.forAllCartPositions(properties, senderBounds.world, position -> {
            double distSq = senderBounds.distanceSquared(position);
            if (distSq < result.value) {
                result.value = distSq;
            }
            return false;
        });
        if (result.value == Double.MAX_VALUE) {
            return altValue;
        }
        return result.value;
    }

    private static class DoubleHolder {
        public double value;
    }

    private static enum SortMode {
        ARBITRARY(),
        RANDOM() {
            @Override
            public Stream<TrainProperties> modify(BoundingRange.Axis senderBounds, Stream<TrainProperties> stream) {
                ArrayList<TrainProperties> allValues = stream.collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(allValues);
                return allValues.stream();
            }
        },
        NEAREST() {
            @Override
            public Stream<TrainProperties> modify(BoundingRange.Axis senderBounds, Stream<TrainProperties> stream) {
                return stream.sorted((t1, t2) -> {
                    double t1d = findDistanceSquaredTo(t1, senderBounds, Double.MAX_VALUE);
                    double t2d = findDistanceSquaredTo(t2, senderBounds, Double.MAX_VALUE);
                    return Double.compare(t1d, t2d);
                });
            }
        },
        FURTHEST() {
            @Override
            public Stream<TrainProperties> modify(BoundingRange.Axis senderBounds, Stream<TrainProperties> stream) {
                return stream.sorted((t1, t2) -> {
                    double t1d = findDistanceSquaredTo(t1, senderBounds, -1.0);
                    double t2d = findDistanceSquaredTo(t2, senderBounds, -1.0);
                    return Double.compare(t2d, t1d);
                });
            }
        };

        /**
         * Modifies the input stream of properties based on this sorting mode
         *
         * @param senderBounds
         * @param stream
         * @return modified stream
         */
        public Stream<TrainProperties> modify(BoundingRange.Axis senderBounds, Stream<TrainProperties> stream) {
            return stream;
        }
    }
}
