package com.bergerkiller.bukkit.tc.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Collects instances passed to a callback, producing a Set with
 * these (unique) elements. Defaults to returning an empty set of never called.
 */
public class SetCallbackCollector<T> implements Consumer<T> {
    private Set<T> buffer = Collections.emptySet();
    private Set<T> result = buffer;

    /**
     * Gets an unmodifiable Set of all elements previously consumed
     *
     * @return unmodifiable set of consumed elements
     */
    public Set<T> result() {
        return result;
    }

    @Override
    public void accept(T t) {
        acceptCheckAdded(t);
    }

    /**
     * Same as {@link #accept(Object)} but returns whether the object was added to the Set
     *
     * @param t Object to add
     * @return True if the object was unique and added
     */
    public boolean acceptCheckAdded(T t) {
        Set<T> buffer = this.buffer;
        int size = buffer.size();
        if (size == 0) {
            this.result = this.buffer = Collections.singleton(t);
            return true;
        } else if (size == 1) {
            // If buffer is not yet a HashSet, make it one so it can be added to
            if (!(buffer instanceof HashSet)) {
                HashSet<T> newSet = new HashSet<>(16);
                newSet.addAll(buffer);
                this.buffer = buffer = newSet;
            }

            // Try adding, once added make it also the result (as opposed to singleton)
            if (buffer.add(t)) {
                this.result = Collections.unmodifiableSet(buffer);
                return true;
            } else {
                return false;
            }
        } else {
            return buffer.add(t);
        }
    }
}
