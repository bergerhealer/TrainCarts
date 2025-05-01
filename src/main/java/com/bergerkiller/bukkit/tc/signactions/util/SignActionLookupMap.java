package com.bergerkiller.bukkit.tc.signactions.util;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Lookup table for sign actions, matched by the sign action events.
 */
public interface SignActionLookupMap {
    /**
     * Is used when TrainCarts is in a disabled state, and no new actions can be
     * registered, and no actions exist anymore.
     */
    SignActionLookupMap DISABLED = new SignActionLookupMap() {
        @Override
        public Optional<Entry> lookup(SignActionEvent event, LookupMode lookupMode) {
            return Optional.empty();
        }

        @Override
        public <T extends SignAction> T register(T action, boolean priority) {
            return action; // No-Op
        }

        @Override
        public void unregister(SignAction action) {}
    };

    /**
     * Creates a new initially-empty lookup map that is optimized for storing
     * {@link com.bergerkiller.bukkit.tc.signactions.TrainCartsSignAction TrainCartsSignAction}
     * instances in a special way, avoiding a long linear search for those.
     *
     * @return New Optimized SignActionLookupMap
     */
    static SignActionLookupMap create() {
        return new SignActionLookupMapImpl();
    }

    /**
     * Creates a basic unoptimized linear-searching lookup map. Goes by all registered actions
     * one by one, matching each until a matching one is found. Used for benchmarking purposes.
     *
     * @return Basic unoptimized SignActionLookupMap
     */
    static SignActionLookupMap createBasicUnoptimized() {
        return new SignActionLookupMapBasicImpl();
    }

    default Optional<Entry> lookup(SignActionEvent event) {
        return lookup(event, SignActionLookupMapImpl.LookupMode.ALL);
    }

    Optional<Entry> lookup(SignActionEvent event, SignActionLookupMapImpl.LookupMode lookupMode);

    default  <T extends SignAction> T register(T action) {
        return register(action, false);
    }

    <T extends SignAction> T register(T action, boolean priority);

    void unregister(SignAction action);

    /**
     * Specified the mode of looking up a SignAction
     */
    enum LookupMode implements Predicate<Entry> {
        /** Allow all sign actions registered to match */
        ALL {
            @Override
            public boolean test(Entry e) {
                return true;
            }
        },
        /** Only try matching sign actions that have a loaded changed handler */
        WITH_LOADED_CHANGED_HANDLER {
            @Override
            public boolean test(Entry e) {
                return e.hasLoadedChangedHandler();
            }
        };
    }

    /**
     * A registered entry inside this lookup map. Has extra metadata information
     * about the registered entry.
     */
    interface Entry {
        /**
         * Gets the registered SignAction implementation
         *
         * @return SignAction
         */
        SignAction action();

        /**
         * Gets whether the {@link #action()} has the {@link SignAction#loadedChanged(SignActionEvent, boolean)}
         * callback overridden.
         *
         * @return True if loadedChanged is handled by the action
         */
        boolean hasLoadedChangedHandler();
    }
}
