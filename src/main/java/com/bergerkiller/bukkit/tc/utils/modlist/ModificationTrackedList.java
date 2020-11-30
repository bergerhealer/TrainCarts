package com.bergerkiller.bukkit.tc.utils.modlist;

import java.util.List;

/**
 * List implementation that tracks the modification count
 * 
 * @param <E> Element type
 */
public interface ModificationTrackedList<E> extends List<E> {
    /**
     * Reads the value of the mod counter, which tracks changes made to this list
     * 
     * @return mod count
     */
    int getModCount();
}
