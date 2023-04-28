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
        Set<T> buffer = this.buffer;
        int size = buffer.size();
        if (size == 0) {
            this.result = this.buffer = Collections.singleton(t);
        } else if (size == 1) {
            HashSet<T> newSet = new HashSet<>(16);
            newSet.addAll(buffer);
            this.buffer = newSet;
            this.result = Collections.unmodifiableSet(newSet);
        } else {
            buffer.add(t);
        }
    }
}
