package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents an immutable snapshot of attachments previously selected using
 * the {@link AttachmentSelector}. The contents of this selection can be updated
 * by calling {@link #sync()} on the main thread, after which {@link #values()}
 * are updated with a new immutable snapshot. It is safe to iterate the list
 * on multiple threads, even when calling sync().
 *
 * @param <T> Type of attachments/interface the attachments were filtered by.
 */
public interface AttachmentSelection<T> extends Iterable<T> {
    /** No selection. Has no names, no values and sync does nothing. */
    AttachmentSelection<Attachment> NONE = none(Attachment.class);

    /**
     * Returns an Empty selection using a particular type filter.
     * Has no names, no values and sync does nothing.
     * The {@link #selector()} does have the right type set.
     *
     * @param typeFilter Type Filter
     * @return Empty Selection
     * @param <T> Type
     */
    static <T> AttachmentSelection<T> none(Class<T> typeFilter) {
        final AttachmentSelector<T> selector = AttachmentSelector.none(typeFilter);
        return new AttachmentSelection<T>() {
            @Override
            public AttachmentSelector<T> selector() {
                return selector;
            }

            @Override
            public List<String> names() {
                return Collections.emptyList();
            }

            @Override
            public List<T> values() {
                return Collections.emptyList();
            }

            @Override
            public boolean sync() {
                return false;
            }
        };
    }

    /**
     * Gets the selector that was used to make this selection
     *
     * @return AttachmentSelector
     */
    AttachmentSelector<T> selector();

    /**
     * Gets an immutable snapshot List of all attachment names that are part
     * of this selection. When selecting by name, this might include additional
     * names when attachments have multiple names defined.
     *
     * @return Immutable List of attachment names part of this selection
     */
    List<String> names();

    /**
     * Gets an immutable snapshot List of all attachments that are part of this
     * selection.
     *
     * @return Immutable List of attachments part of this selection
     */
    List<T> values();

    /**
     * Synchronizes the {@link #names()} and {@link #values()} if the underlying
     * structure changed. Must be called on the main thread.
     *
     * @return True if changes may have occurred. Might sometimes also return true
     *         if no changes (relevant to this selection) occurred.
     */
    boolean sync();

    @Override
    default Iterator<T> iterator() {
        return values().iterator();
    }

    @Override
    default void forEach(Consumer<? super T> action) {
        values().forEach(action);
    }
}
