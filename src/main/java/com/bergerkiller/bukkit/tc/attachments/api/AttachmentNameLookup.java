package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.utils.StreamUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, List<Attachment>> attachments;
    private final List<String> names;

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
     * Merges all the by-name lookups into a single by-name lookup view
     *
     * @param nameLookups Lookups to merge
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
            return first;
        } else {
            makeListsImmutable(result);
            return new AttachmentNameLookup(result);
        }
    }

    /**
     * Object that can produce an {@link AttachmentNameLookup}
     */
    public interface Holder {
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
}