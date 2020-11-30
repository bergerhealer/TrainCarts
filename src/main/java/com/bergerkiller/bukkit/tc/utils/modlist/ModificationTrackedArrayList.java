package com.bergerkiller.bukkit.tc.utils.modlist;

import java.util.ArrayList;

/**
 * Implementation of ArrayList that exposes the modification count
 * 
 * @param <E> Element type
 */
public final class ModificationTrackedArrayList<E> extends ArrayList<E> implements ModificationTrackedList<E> {
    private static final long serialVersionUID = -6451538676293234885L;

    @Override
    public int getModCount() {
        return this.modCount;
    }
}
