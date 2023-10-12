package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.utils.StreamUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An immutable object storing all the attachments in an attachment tree mapped
 * by their assigned names. Can be used to (asynchronously) efficiently look up
 * attachment groups by name, or list all the names in use.<br>
 * <br>
 * A lookup is uniquely created starting at a certain root attachment, and so can
 * also be created for subtrees of attachments.
 */
public class AttachmentNameLookup {
    public static final AttachmentNameLookup EMPTY = new AttachmentNameLookup(Collections.emptyMap());
    static {
        EMPTY.invalidate(); // Not backed by anything so is invalid
    }

    private final Map<String, List<Attachment>> attachments;
    private final List<String> names;
    private boolean valid = true;

    private AttachmentNameLookup(AttachmentNameLookup original) {
        this.attachments = original.attachments;
        this.names = original.names;
        this.valid = original.valid;
    }

    private AttachmentNameLookup(Map<String, List<Attachment>> attachments) {
        this.attachments = attachments;
        this.names = attachments.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attachments.keySet()));
    }

    private static void makeListsImmutable(Map<String, List<Attachment>> attachments) {
        for (Map.Entry<String, List<Attachment>> e : attachments.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
    }

    private static void fill(Map<String, List<Attachment>> attachments, Attachment attachment) {
        for (String name : attachment.getNames()) {
            attachments.computeIfAbsent(name, n -> new ArrayList<>(4)).add(attachment);
        }
        //TODO: This recursion could maybe cause a stack overflow - maybe use a flattened view instead?
        for (Attachment child : attachment.getChildren()) {
            fill(attachments, child);
        }
    }

    /**
     * Gets whether this lookup is still valid. If attachments have changed since this object
     * was requested, this will return false indicating it should be looked up again.
     *
     * @return True if valid and still representative of the attachment configuration
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Makes this configuration no longer valid, making {@link #isValid()} return false.
     */
    public void invalidate() {
        valid = false;
    }

    /**
     * Gets an unordered list of unique names of attachments that exist
     *
     * @return Names
     */
    public List<String> names() {
        return names;
    }

    /**
     * Gets an unordered list of unique names of attachments that exist, with the
     * result filtered by attachments that pass a specified filter predicate.
     *
     * @param filter Predicate that filters what attachments are included
     * @return Names
     */
    public List<String> names(Predicate<Attachment> filter) {
        return attachments.entrySet().stream()
                .filter(e -> filterAttachments(e.getValue(), filter))
                .map(Map.Entry::getKey)
                .collect(StreamUtil.toUnmodifiableList());
    }

    /**
     * Gets an unmodifiable List of attachments that match the specified name. If none
     * match, an empty List is returned.
     *
     * @param name Assigned name
     * @return List of attachments matching this name
     */
    public List<Attachment> get(String name) {
        return attachments.getOrDefault(name, Collections.emptyList());
    }

    /**
     * Gets an unmodifiable List of attachments that match the specified name, and are of
     * the specified Class type.
     *
     * @param name Assigned name
     * @param type Type of attachment
     * @return List of attachments matching this name
     * @param <T> Attachment Type
     */
    public <T extends Attachment> List<T> getOfType(String name, Class<T> type) {
        //noinspection unchecked
        return (List<T>) get(name, type::isInstance);
    }

    /**
     * Gets an unmodifiable List of attachments that match the specified name. A filter
     * can be specified to filter the results by attachments that pass the predicate.
     * If none match, an empty List is returned.
     *
     * @param name Assigned name
     * @param filter Predicate that filters what attachments are included
     * @return List of attachments matching this name
     */
    public List<Attachment> get(String name, Predicate<Attachment> filter) {
        // Go by all attachments at least once. It's very likely that at this point
        // all elements will pass the filter (assuming names(filter) was used before).
        // So only create a list copy if we find an element that should be omitted.
        List<Attachment> attachments = get(name);
        int numAttachments = attachments.size();
        for (int i = 0; i < numAttachments; i++) {
            Attachment attachment = attachments.get(i);
            if (!filter.test(attachment)) {
                // This one is excluded! Create a new list that excludes this attachment.
                // Then populate it with all remaining elements that pass the filter.
                List<Attachment> result = new ArrayList<>(numAttachments - 1);
                for (int j = 0; j < i; j++) {
                    result.add(attachments.get(j));
                }
                for (int j = i + 1; j < numAttachments; j++) {
                    attachment = attachments.get(j);
                    if (filter.test(attachment)) {
                        result.add(attachment);
                    }
                }
                // Make it unmodifiable again
                return Collections.unmodifiableList(result);
            }
        }

        // All are included, return as-is
        return attachments;
    }

    private static boolean filterAttachments(List<Attachment> attachments, Predicate<Attachment> filter) {
        for (Attachment attachment : attachments) {
            if (filter.test(attachment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a new AttachmentNameLookup of an attachment tree starting at the
     * root attachment specified
     *
     * @param root Root attachment of the attachment tree
     * @return AttachmentNameLookup
     */
    public static AttachmentNameLookup create(Attachment root) {
        Map<String, List<Attachment>> attachments = new HashMap<>();
        fill(attachments, root);
        makeListsImmutable(attachments);
        return new AttachmentNameLookup(attachments);
    }

    /**
     * Merges all the by-name lookups into a single by-name lookup view.
     *
     * @param nameLookups Lookups to merge. This Collection must be immutable.
     * @return Merged view
     */
    public static AttachmentNameLookup merge(Collection<AttachmentNameLookup> nameLookups) {
        // Go by all lookups and clone the map of the first one that is non-empty
        Map<String, List<Attachment>> result = Collections.emptyMap();
        AttachmentNameLookup first = null;
        for (AttachmentNameLookup lookup : nameLookups) {
            if (lookup.attachments.isEmpty()) {
                continue;
            }

            if (first == null) {
                first = lookup;
            } else {
                // Initialize a new modifiable hashmap
                if (result.isEmpty()) {
                    result = new HashMap<>(first.attachments);
                    for (Map.Entry<String, List<Attachment>> e : result.entrySet()) {
                        e.setValue(new ArrayList<>(e.getValue()));
                    }
                }
                // Merge into it
                for (Map.Entry<String, List<Attachment>> e : lookup.attachments.entrySet()) {
                    result.computeIfAbsent(e.getKey(), n -> new ArrayList<>()).addAll(e.getValue());
                }
            }
        }

        // If only one non-empty element existed, just return that same one
        // Otherwise, create a new immutable view of the results we've gathered
        if (first != null && result.isEmpty()) {
            return new AttachmentNameLookupMerged(first, nameLookups);
        } else {
            makeListsImmutable(result);
            return new AttachmentNameLookupMerged(result, nameLookups);
        }
    }

    /**
     * Provides a merged view of the attachments of many members
     */
    private static class AttachmentNameLookupMerged extends AttachmentNameLookup {
        private final Collection<AttachmentNameLookup> originalLookups;

        private AttachmentNameLookupMerged(
                AttachmentNameLookup original,
                Collection<AttachmentNameLookup> originalLookups
        ) {
            super(original);
            this.originalLookups = originalLookups;
        }

        private AttachmentNameLookupMerged(
                Map<String, List<Attachment>> attachments,
                Collection<AttachmentNameLookup> originalLookups
        ) {
            super(attachments);
            this.originalLookups = originalLookups;
        }

        @Override
        public boolean isValid() {
            if (!super.isValid()) {
                return false;
            }

            for (AttachmentNameLookup lookup : originalLookups) {
                if (!lookup.isValid()) {
                    invalidate();
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Object that can produce an {@link AttachmentNameLookup}
     */
    @FunctionalInterface
    public interface Supplier {
        /**
         * Obtains an immutable snapshot of the {@link AttachmentNameLookup name lookup} of the root
         * attachment(s). Internally caches the result until this subtree changes.
         * Identity comparison can be used to check whether this subtree changed since a previous
         * invocation.<br>
         * <br>
         * Is multi-thread safe.
         *
         * @return AttachmentNameLookup
         */
        AttachmentNameLookup getNameLookup();
    }

    /**
     * A group of attachments retrieved that have a certain name. Can be synchronized (on the main thread)
     * to keep it up-to-date with changes to the underlying attachments.
     *
     * @param <T> Attachment Type
     */
    public static final class NameGroup<T extends Attachment> implements Iterable<T> {
        private final Supplier lookupSupplier;
        private final String name;
        private final Class<T> type;
        private AttachmentNameLookup cachedLookup;
        private List<T> cachedValues;

        /**
         * Creates a new NameGroup taking attachment details from an attachment name lookup supplier
         *
         * @param lookupSupplier Supplier for the AttachmentNameLookup from which to find attachments.
         *                       Can specify an Attachment, MinecartMember or MinecartGroup as well.
         * @param name Name of the attachments to look for
         * @param type Type filter for the type of attachments to include
         * @return NameGroup
         * @param <T> Attachment Type
         */
        public static <T extends Attachment> NameGroup<T> of(Supplier lookupSupplier, String name, Class<T> type) {
            return new NameGroup<T>(lookupSupplier, name, type);
        }

        private NameGroup(Supplier lookupSupplier, String name, Class<T> type) {
            this.lookupSupplier = lookupSupplier;
            this.name = name;
            this.type = type;
            this.cachedLookup = AttachmentNameLookup.EMPTY;
            this.cachedValues = Collections.emptyList();
            sync();
        }

        /**
         * Gets an unmodifiable List of attachments matching this name group.
         * This method can be safely called from another thread.
         * Returns the attachments known since last {@link #sync()}.
         * You can also just iterate this class itself, which iterates over the
         * same values.
         *
         * @return Attachment Values
         */
        public List<T> values() {
            return cachedValues;
        }

        /**
         * Updates the List of attachments if these have changed. Must ideally only
         * be called on the main thread.
         */
        public void sync() {
            if (!cachedLookup.isValid()) {
                AttachmentNameLookup lookup = lookupSupplier.getNameLookup();
                cachedLookup = lookup;
                cachedValues = lookup.getOfType(name, type);
            }
        }

        @Override
        public Iterator<T> iterator() {
            return cachedValues.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            cachedValues.forEach(action);
        }
    }
}
