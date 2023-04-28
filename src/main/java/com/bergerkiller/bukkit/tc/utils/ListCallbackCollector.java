package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Collects instances passed to a callback, producing a List with
 * these elements. Defaults to returning an empty list of never called.
 */
public class ListCallbackCollector<T> implements Consumer<T> {
    private List<T> buffer = Collections.emptyList();
    private List<T> result = buffer;

    /**
     * Gets an unmodifiable List of all elements previously consumed
     *
     * @return unmodifiable list of consumed elements
     */
    public List<T> result() {
        return result;
    }

    @Override
    public void accept(T t) {
        List<T> buffer = this.buffer;
        int size = buffer.size();
        if (size == 0) {
            this.result = this.buffer = Collections.singletonList(t);
        } else if (size == 1) {
            ArrayList<T> newList = new ArrayList<>(16);
            newList.add(buffer.get(0));
            newList.add(t);
            this.buffer = newList;
            this.result = Collections.unmodifiableList(newList);
        } else {
            buffer.add(t);
        }
    }
}
