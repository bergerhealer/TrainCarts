package com.bergerkiller.bukkit.tc.properties.standard.fieldbacked;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Combines the Sets of multiple cart properties into a single Set. Smartly checks
 * whether any of the sets of the individual carts have changed before re-creating
 * a new Set of the combined set.
 */
public class FieldBackedCombinedTrainProperty<T> {
    private final List<Set<T>> previousSets = new ArrayList<>();
    private Set<T> previousResult = Collections.emptySet();

    public Set<T> update(TrainProperties properties, FieldBackedStandardCartProperty<Set<T>> property) {
        boolean different = false;

        // Synchronize the list of sets and detect whether any of the sets changed instance
        // This assumes the field backed nature will ensure the same set is returned every time
        // when no changes are made.
        {
            int index = 0;
            for (CartProperties cartProperties : properties) {
                Set<T> cartSet = property.get(cartProperties);
                if (index >= previousSets.size()) {
                    different = true;
                    previousSets.add(cartSet);
                } else if (previousSets.get(index) != cartSet) {
                    different = true;
                    previousSets.set(index, cartSet);
                }
                index++;
            }
            while (previousSets.size() > index) {
                previousSets.remove(previousSets.size() - 1);
                different = true;
            }
        }

        // Regenerate combined set if different
        if (different) {
            Set<T> combined = new HashSet<>(Math.max(8, previousResult.size()));
            previousSets.forEach(combined::addAll);
            previousResult = combined.isEmpty() ? Collections.emptySet()
                                                : Collections.unmodifiableSet(combined);
        }

        return previousResult;
    }
}
