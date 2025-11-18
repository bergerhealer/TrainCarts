package com.bergerkiller.bukkit.tc.signactions.util;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.signactions.SignActionRegisterEvent;
import com.bergerkiller.bukkit.tc.events.signactions.SignActionUnregisterEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.TrainCartsSignAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lookup table for sign actions, matched by the sign action events. Has an optimized
 * structure for registered {@link TrainCartsSignAction} instances to avoid iterating
 * and checking String startsWith for every sign in a loop.
 */
class SignActionLookupMapImpl implements SignActionLookupMap {
    // All registered sign actions. Also controls order in which actions are matched.
    private final PriorityEntryList allEntries = new PriorityEntryList();
    // TrainCarts signs
    private final NavigableMap<String, List<TrainCartsEntry>> traincartsEntries = new TreeMap<>();
    // Non-TrainCarts signs that need match() called on them
    // This includes third-party plugin sign actions that don't yet use TrainCartsSignAction
    private final PriorityEntryList nonTrainCartsEntries = new PriorityEntryList();;

    @Override
    public Optional<Entry> lookup(SignActionEvent event, LookupMode lookupMode) {
        // If sign is not a traincarts sign, only try to match a non-traincarts sign
        if (!event.getHeader().isValid()) {
            return lookupNonTrainCarts(event, lookupMode);
        }

        // Second line of a TrainCarts [cart] or [train] sign uniquely identifies the action
        // If this line is empty, don't bother trying to match one.
        String signIdentifier = event.getLowerCaseSecondCleanedLine();
        if (signIdentifier.isEmpty()) {
            return lookupNonTrainCarts(event, lookupMode);
        }

        // First match all signs that are traincarts signs, by using the TreeMap to
        // efficiently match all identifiers that could match.
        // We use headMap() so that if multiple TreeMap entries match, it combines the lists
        // into one long sorted list.
        List<TrainCartsEntry> allTypeMatchingEntries = Collections.emptyList();
        boolean allTypeMatchingEntriesModifiable = false;
        {
            Set<Map.Entry<String, List<TrainCartsEntry>>> orderedMatchingEntries = traincartsEntries.headMap(signIdentifier, true)
                            .descendingMap()
                            .entrySet();
            for (Map.Entry<String, List<TrainCartsEntry>> e : orderedMatchingEntries) {
                // If the type identifier on the sign does not start with the entry key, abort.
                // This is needed because headMap() will return all entries in order, including the
                // few entries whose type identifier matches the input.
                if (!signIdentifier.startsWith(e.getKey())) {
                    break;
                }

                // Merge the maps, unless its the initial empty state which will happen commonly
                if (allTypeMatchingEntries.isEmpty()) {
                    allTypeMatchingEntries = e.getValue();
                } else {
                    if (!allTypeMatchingEntriesModifiable) {
                        allTypeMatchingEntriesModifiable = true;
                        allTypeMatchingEntries = new ArrayList<>(allTypeMatchingEntries);
                    }
                    allTypeMatchingEntries.addAll(e.getValue());
                    Collections.sort(allTypeMatchingEntries);
                }
            }
        }

        // If none match, try non-traincarts signs
        if (allTypeMatchingEntries.isEmpty()) {
            return lookupNonTrainCarts(event, lookupMode);
        }

        // Go by all matching entries. But make sure to iterate the non-traincarts entries in the
        // right order first.
        int prevNonTrainCartsIndex = 0;
        for (TrainCartsEntry tcEntry : allTypeMatchingEntries) {
            int nextNonTrainCartsIndex = tcEntry.getFirstIndexAfterOrder(nonTrainCartsEntries);

            // I think always true but this is just a guard...
            if (nextNonTrainCartsIndex > prevNonTrainCartsIndex) {
                // Match all non-traincarts signs that are registered before this traincarts sign entry
                Optional<Entry> nonTCEntry = lookupNonTrainCartsRange(event, lookupMode, prevNonTrainCartsIndex, nextNonTrainCartsIndex);
                if (nonTCEntry.isPresent()) {
                    return nonTCEntry;
                }

                // Don't check again next time
                prevNonTrainCartsIndex = nextNonTrainCartsIndex;
            }

            // Match this traincarts sign. We can skip match() as we know that to be true already.
            SignAction tcAction = tcEntry.action;
            if (lookupMode.test(tcEntry) && tcAction.verify(event)) {
                return Optional.of(tcEntry);
            }
        }

        // Match all remaining non-traincarts signs
        return lookupNonTrainCartsRange(event, lookupMode, prevNonTrainCartsIndex, nonTrainCartsEntries.size());
    }

    private Optional<Entry> lookupNonTrainCartsRange(SignActionEvent event, LookupMode lookupMode, int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            EntryImpl nonTCEntry = nonTrainCartsEntries.getAt(i);
            SignAction nonTCAction = nonTCEntry.action;
            if (lookupMode.test(nonTCEntry) && nonTCAction.match(event) && nonTCAction.verify(event)) {
                return Optional.of(nonTCEntry);
            }
        }

        return Optional.empty();
    }

    private Optional<Entry> lookupNonTrainCarts(SignActionEvent event, LookupMode lookupMode) {
        for (EntryImpl e : nonTrainCartsEntries) {
            SignAction action = e.action;
            if (lookupMode.test(e) && action.match(event) && action.verify(event)) {
                return Optional.of(e);
            }
        }

        return Optional.empty();
    }

    @Override
    public <T extends SignAction> T register(T action, boolean priority) {
        if (action == null) {
            throw new IllegalArgumentException("SignAction is null");
        }

        if (action instanceof TrainCartsSignAction) {
            final TrainCartsEntry entry = new TrainCartsEntry((TrainCartsSignAction) action, priority);
            allEntries.add(entry);
            allEntries.refreshEntryOrder();

            // Register in the by-type-identifier mapping
            // Preserve when multiple entries share the same identifier, but ensure
            // they're stored honoring EntryImpl.orderIndex.
            for (String typeIdentifier : entry.typeIdentifiers) {
                traincartsEntries.compute(typeIdentifier, (key, list) -> {
                    if (list == null) {
                        return Collections.singletonList(entry);
                    } else if (list.size() == 1) {
                        List<TrainCartsEntry> newEntries = new ArrayList<>(2);
                        newEntries.addAll(list);
                        newEntries.add(entry);
                        Collections.sort(newEntries);
                        return newEntries;
                    } else {
                        list.add(entry);
                        Collections.sort(list);
                        return list;
                    }
                });
            }
        } else {
            EntryImpl entry = new EntryImpl(action, priority);
            allEntries.add(entry);
            allEntries.refreshEntryOrder();
            nonTrainCartsEntries.add(entry);
        }

        // Fire register event so that all signs can be refreshed on the server
        // Extra guards so that this also works under test
        if (!Common.IS_TEST_MODE) {
            CommonUtil.callEvent(new SignActionRegisterEvent(action, priority));
        }

        return action;
    }

    @Override
    public void unregister(SignAction action) {
        EntryImpl e = allEntries.remove(action);
        if (e == null) {
            return;
        }

        allEntries.refreshEntryOrder();

        if (e instanceof TrainCartsEntry) {
            final TrainCartsEntry tcEntry = (TrainCartsEntry) e;
            for (String typeIdentifier : tcEntry.typeIdentifiers) {
                traincartsEntries.computeIfPresent(typeIdentifier, (key, list) -> {
                    if (list.size() == 1) {
                        return list.get(0) == tcEntry ? null : list;
                    } else {
                        list.remove(tcEntry);
                        return list;
                    }
                });
            }
        } else {
            nonTrainCartsEntries.remove(e);
        }

        // Fire unregister event so that all signs can be refreshed on the server
        // Extra guards so that this also works under test
        if (!Common.IS_TEST_MODE) {
            CommonUtil.callEvent(new SignActionUnregisterEvent(action));
        }
    }

    /**
     * A sorted list of entries, sorted in the order elements are added. If the entry
     * has priority set, it is added to the front instead of the back so that it overrides
     * previous entries.
     */
    private static class PriorityEntryList implements Iterable<EntryImpl> {
        private final List<EntryImpl> entries = new ArrayList<>();

        @Override
        public Iterator<EntryImpl> iterator() {
            return entries.iterator();
        }

        public EntryImpl getAt(int index) {
            return entries.get(index);
        }

        public int size() {
            return entries.size();
        }

        /**
         * Looks up the index of the entry in this list that is first to come
         * after the orderIndex specified. The order index is the order of this
         * entry in the "all entries" list.
         *
         * @param orderIndex Order Index
         * @return Index position, {@link #size()} if all entries in this list come before
         */
        public int getFirstIndexAfterOrder(int orderIndex) {
            int size = entries.size();
            for (int i = 0; i < size; i++) {
                EntryImpl e = entries.get(i);
                if (e.orderIndex > orderIndex) {
                    return i;
                }
            }
            return size;
        }

        public void add(EntryImpl entry) {
            if (entry.priority) {
                entries.add(0, entry);
            } else {
                entries.add(entry);
            }
        }

        public EntryImpl remove(SignAction action) {
            int size = entries.size();
            for (int i = 0; i < size; i++) {
                EntryImpl e = entries.get(i);
                if (e.action.equals(action)) {
                    entries.remove(i);
                    return e;
                }
            }
            return null;
        }

        public void remove(EntryImpl entry) {
            int index = entry.priority ? entries.indexOf(entry) : entries.lastIndexOf(entry);
            if (index != -1) {
                entries.remove(index);
            }
        }

        /**
         * Assigns the {@link EntryImpl#orderIndex} the index value of the entry in this sorted list.
         * Should only be called on the "all entries" sorted list.
         */
        public void refreshEntryOrder() {
            int size = entries.size();
            for (int i = 0; i < size; i++) {
                entries.get(i).onOrderUpdated(i);
            }
        }
    }

    /**
     * A [train] or [cart] SignAction, with a list of type identifiers that uniquely
     * match it.
     */
    private static class TrainCartsEntry extends EntryImpl {
        public final List<String> typeIdentifiers;
        private int nonTrainCartsAfterEntryIndex = -1;

        public TrainCartsEntry(TrainCartsSignAction action, boolean priority) {
            super(action, priority);
            this.typeIdentifiers = action.getTypeIdentifiers();
        }

        public int getFirstIndexAfterOrder(PriorityEntryList nonTrainCartsEntries) {
            int index = nonTrainCartsAfterEntryIndex;
            if (index == -1) {
                nonTrainCartsAfterEntryIndex = index = nonTrainCartsEntries.getFirstIndexAfterOrder(this.orderIndex);
            }
            return index;
        }

        @Override
        public void onOrderUpdated(int orderIndex) {
            super.onOrderUpdated(orderIndex);
            this.nonTrainCartsAfterEntryIndex = -1;
        }
    }

    /**
     * A unique registered action, with extra cached information about this entry that is used
     * during the resolving process.
     */
    private static class EntryImpl implements Entry, Comparable<EntryImpl> {
        public final SignAction action;
        public final boolean priority;
        public final boolean hasLoadedChangedHandler;
        public int orderIndex = -1; // (Re-)Calculated later. Enforced matching order.

        public EntryImpl(SignAction action, boolean priority) {
            this.action = action;
            this.priority = priority;
            this.hasLoadedChangedHandler = CommonUtil.isMethodOverrided(SignAction.class, action.getClass(), "loadedChanged", SignActionEvent.class, boolean.class);
        }

        public void onOrderUpdated(int orderIndex) {
            this.orderIndex = orderIndex;
        }

        @Override
        public SignAction action() {
            return action;
        }

        @Override
        public boolean hasLoadedChangedHandler() {
            return hasLoadedChangedHandler;
        }

        @Override
        public int compareTo(EntryImpl entry) {
            return Integer.compare(orderIndex, entry.orderIndex);
        }
    }
}
