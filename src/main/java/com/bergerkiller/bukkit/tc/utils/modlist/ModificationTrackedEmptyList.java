package com.bergerkiller.bukkit.tc.utils.modlist;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Implementation of {@link Collections#emptyList()} with a getModCount() function
 * that always returns 0.
 * 
 * @param <E> Element type
 */
public final class ModificationTrackedEmptyList<E> extends AbstractList<E> implements ModificationTrackedList<E> {
    @SuppressWarnings("rawtypes")
    public static final ModificationTrackedEmptyList EMPTY_LIST = new ModificationTrackedEmptyList();

    @SuppressWarnings("unchecked")
    public static <E> ModificationTrackedList<E> emptyList() {
        return EMPTY_LIST;
    }

    private ModificationTrackedEmptyList() {
    }

    @Override
    public int getModCount() {
        return 0;
    }

    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }
    public ListIterator<E> listIterator() {
        return Collections.emptyListIterator();
    }

    public int size() {return 0;}
    public boolean isEmpty() {return true;}

    public boolean contains(Object obj) {return false;}
    public boolean containsAll(Collection<?> c) { return c.isEmpty(); }

    public Object[] toArray() { return new Object[0]; }

    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    public E get(int index) {
        throw new IndexOutOfBoundsException("Index: "+index);
    }

    public boolean equals(Object o) {
        return (o instanceof List) && ((List<?>)o).isEmpty();
    }

    public int hashCode() { return 1; }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return false;
    }
    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
    }
    @Override
    public void sort(Comparator<? super E> c) {
    }

    // Override default methods in Collection
    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
    }

    @Override
    public Spliterator<E> spliterator() { return Spliterators.emptySpliterator(); }
}
