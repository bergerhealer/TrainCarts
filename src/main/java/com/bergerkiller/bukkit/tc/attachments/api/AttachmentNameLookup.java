package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An immutable object storing all the attachments in an attachment tree mapped
 * by their assigned names. Can be used to (asynchronously) efficiently look up
 * attachment groups by name, or list all the names in use.<br>
 * <br>
 * A lookup is uniquely created starting at a certain root attachment, and so can
 * also be created for subtrees of attachments.
 */
public class AttachmentNameLookup {
    public static final AttachmentNameLookup EMPTY = new AttachmentNameLookup(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    static {
        EMPTY.invalidate(); // Not backed by anything so is invalid
    }

    private final List<Attachment> all;
    private final List<Attachment> parents;
    private final Map<String, List<Attachment>> byName;
    private final List<String> names;
    private boolean valid = true;

    private AttachmentNameLookup(AttachmentNameLookup original) {
        this.all = original.all;
        this.parents = original.parents;
        this.byName = original.byName;
        this.names = original.names;
        this.valid = original.valid;
    }

    private AttachmentNameLookup(List<Attachment> all, List<Attachment> parents, Map<String, List<Attachment>> byName) {
        this.all = all;
        this.parents = parents;
        this.byName = byName;
        this.names = byName.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(byName.keySet()));
    }

    private static void makeListsImmutable(Map<String, List<Attachment>> attachments) {
        for (Map.Entry<String, List<Attachment>> e : attachments.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
    }

    private static void fill(List<Attachment> all, Map<String, List<Attachment>> attachments, Attachment attachment) {
        for (String name : attachment.getNames()) {
            attachments.computeIfAbsent(name, n -> new ArrayList<>(4)).add(attachment);
        }
        all.add(attachment);
        //TODO: This recursion could maybe cause a stack overflow - maybe use a flattened view instead?
        for (Attachment child : attachment.getChildren()) {
            fill(all, attachments, child);
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
        return byName.entrySet().stream()
                .filter(e -> containsMatching(e.getValue(), filter))
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
        return byName.getOrDefault(name, Collections.emptyList());
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
        return Util.filterList(get(name), filter);
    }

    /**
     * Gets an unmodifiable List of attachments that are of the specified Class type.
     *
     * @param type Type of attachment
     * @return List of attachments matching this name
     * @param <T> Attachment Type
     */
    public <T extends Attachment> List<T> allOfType(Class<T> type) {
        //noinspection unchecked
        return (List<T>) all(type::isInstance);
    }

    /**
     * Gets an unmodifiable list of all attachments below the root attachment(s) queried,
     * named or not, that match a specified predicate.
     *
     * @param filter Filter predicate
     * @return List of all attachments
     */
    public List<Attachment> all(Predicate<Attachment> filter) {
        return Util.filterList(all, filter);
    }

    /**
     * Gets an unmodifiable list of all attachments below the root attachment(s) queried.
     *
     * @return List of all attachments
     */
    public List<Attachment> all() {
        return all;
    }

    /**
     * Gets an unmodifiable list of all parent attachments below the root attachment(s) queried.
     *
     * @return List of all parent attachments
     */
    public List<Attachment> parents() {
        return parents;
    }

    /**
     * Gets an unmodifiable list of all parent attachments below the root attachment(s) queried,
     * named or not, that match a specified predicate.
     *
     * @param filter Filter predicate
     * @return List of all parent attachments
     */
    public List<Attachment> parents(Predicate<Attachment> filter) {
        return parents.stream().filter(filter).collect(StreamUtil.toUnmodifiableList());
    }

    /**
     * Implements the seat= selector condition behavior. Will return a List of entities
     * that pass the selector condition.
     *
     * @param sender CommandSender (for @p)
     * @param condition SelectorCondition
     * @return List of entities in Seat Attachments that pass the condition
     */
    public Stream<Entity> matchSeatSelector(CommandSender sender, SelectorCondition condition) {
        // What seats do we look at? All of them, or only those of the key path specified?
        List<CartAttachmentSeat> seats;
        if (condition.hasKeyPath()) {
            seats = getOfType(condition.getKeyPath(), CartAttachmentSeat.class);
        } else {
            seats = allOfType(CartAttachmentSeat.class);
        }

        // If comparing against true/false, simply return all the passengers
        // The actual boolean check occurs elsewhere
        if (condition.isBoolean()) {
            return seats.stream()
                    .map(CartAttachmentSeat::getEntity)
                    .filter(Objects::nonNull);
        }

        // Do we include the sender in the checks? (@p)
        final boolean includePlayer = condition.matchesText("@p");

        // Look for players in the seat with a particular name (or if @p, are the sender)
        return seats.stream()
                .map(CartAttachmentSeat::getEntity)
                .filter(e -> e instanceof Player)
                .filter(p -> (includePlayer && p == sender) || condition.matchesText(((Player) p).getName()));
    }

    /**
     * Selects the attachment names according to a selector's filter rules.
     * Does not differentiate between search strategy ROOT_CHILDREN and CHILDREN.
     *
     * @param selector Attachment Selector filter
     * @param excluding Attachments to exclude from the listing
     * @return List of Attachment Names included in the selector results
     */
    public List<String> selectNames(AttachmentSelector<?> selector, Set<Attachment> excluding) {
        switch (selector.strategy()) {
            case NONE:
                return Collections.emptyList();
            case PARENTS:
                return Util.filterAndMultiMapList(parents,
                        a -> selector.matches(a) && !excluding.contains(a),
                        Attachment::getNames);
            default:
                final Predicate<Attachment> filter = a -> selector.matchesExceptName(a) && !excluding.contains(a);
                if (selector.nameFilter().isPresent()) {
                    return Util.filterAndMultiMapList(get(selector.nameFilter().get()), filter, Attachment::getNames);
                } else {
                    return names(filter);
                }
        }
    }

    /**
     * Selects the attachment values according to a selector's filter rules.
     * Does not differentiate between search strategy ROOT_CHILDREN and CHILDREN.
     *
     * @param selector Attachment Selector filter
     * @param excluding Attachments to exclude from the listing
     * @return List of Attachments that match the selector's filters
     * @param <T> Selector Attachment Type
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> selectValues(AttachmentSelector<T> selector, Set<Attachment> excluding) {
        switch (selector.strategy()) {
            case NONE:
                return Collections.emptyList();
            case PARENTS:
                return (List<T>) parents(a -> selector.matches(a) && !excluding.contains(a));
            default:
                final Predicate<Attachment> filter = a -> selector.matchesExceptName(a) && !excluding.contains(a);
                if (selector.nameFilter().isPresent()) {
                    return (List<T>) get(selector.nameFilter().get(), filter);
                } else {
                    return (List<T>) all(filter);
                }
        }
    }

    private static boolean containsMatching(List<Attachment> attachments, Predicate<Attachment> filter) {
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
        // Compute flattened list of attachments / by name mapping
        Map<String, List<Attachment>> attachments = new HashMap<>();
        List<Attachment> all = new ArrayList<>();
        fill(all, attachments, root);
        makeListsImmutable(attachments);

        // Compute a flattened list of all parents
        List<Attachment> parents;
        {
            Attachment p;
            if ((p = root.getParent()) != null) {
                parents = new ArrayList<>();
                parents.add(root);
                parents.add(p);
                while ((p = p.getParent()) != null) {
                    parents.add(p);
                }
                parents = Collections.unmodifiableList(parents);
            } else {
                parents = Collections.singletonList(root);
            }
        }

        return new AttachmentNameLookup(Collections.unmodifiableList(all), parents, attachments);
    }

    /**
     * Merges all the by-name lookups into a single by-name lookup view.
     *
     * @param nameLookups Lookups to merge. This Collection must be immutable.
     * @return Merged view
     */
    public static AttachmentNameLookup merge(Collection<AttachmentNameLookup> nameLookups) {
        // Some optimizations
        if (nameLookups.isEmpty()) {
            return AttachmentNameLookup.EMPTY;
        } else if (nameLookups.size() == 1) {
            return nameLookups.iterator().next();
        }

        // Go by all lookups and merge them into one collection
        Map<String, List<Attachment>> resultByName = new HashMap<>(32);
        List<Attachment> resultAll = new ArrayList<>(64);
        List<Attachment> resultParents = new ArrayList<>(16);
        for (AttachmentNameLookup lookup : nameLookups) {
            // Merge into the by-name mapping
            if (!lookup.byName.isEmpty()) {
                for (Map.Entry<String, List<Attachment>> e : lookup.byName.entrySet()) {
                    resultByName.computeIfAbsent(e.getKey(), n -> new ArrayList<>()).addAll(e.getValue());
                }
            }

            // Merge All
            resultAll.addAll(lookup.all);

            // Merge Parents
            resultParents.addAll(lookup.parents);
        }

        makeListsImmutable(resultByName);
        return new AttachmentNameLookupMerged(
                Collections.unmodifiableList(resultAll),
                Collections.unmodifiableList(resultParents),
                resultByName,
                nameLookups);
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
                List<Attachment> all,
                List<Attachment> parents,
                Map<String, List<Attachment>> byName,
                Collection<AttachmentNameLookup> originalLookups
        ) {
            super(all, parents, byName);
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

        /**
         * Obtains an immutable snapshot of the {@link AttachmentNameLookup name lookup} of the root
         * attachment(s). Internally caches the result until this subtree changes.
         * Identity comparison can be used to check whether this subtree changed since a previous
         * invocation.<br>
         * <br>
         * Includes a strategy parameter. Implementations can handle the
         * {@link AttachmentSelector.SearchStrategy#ROOT_CHILDREN SearchStrategy.ROOT_CHILDREN}
         * strategy here, and defer to the name lookup of the root.<br>
         * <br>
         * Is multi-thread safe.
         *
         * @return AttachmentNameLookup
         */
        default AttachmentNameLookup getNameLookup(AttachmentSelector.SearchStrategy strategy) {
            return getNameLookup();
        }

        /**
         * Gets a set of attachments that represent the 'self' of this Supplier. If this supplier
         * is itself an attachment, returns a singleton set of this attachment. If it's a merged
         * result of many attachments, returns a set of all these attachments.<br>
         * <br>
         * This is used for handling {@link AttachmentSelector#isExcludingSelf()}.
         *
         * @return Set of attachments that represents 'self'
         */
        default Set<Attachment> getSelfFilterOfNameLookup() {
            return Collections.emptySet();
        }

        /**
         * Selects attachments using this Attachment Supplier's
         * {@link #getNameLookup(AttachmentSelector.SearchStrategy) getNameLookup(strategy)}
         * based on an attachment selector as a filter.
         *
         * @param selector Attachment Selector filter
         * @return Selection of Attachments
         * @param <T> Selection Attachment Class or Interface type
         */
        default <T> AttachmentSelection<T> getSelection(AttachmentSelector<T> selector) {
            return new SelectionImpl<T>(this, selector);
        }

        /**
         * Selects attachments from multiple suppliers, merging the result together
         * into a single attachment selection. A selector filter can be specified
         * which controls the search strategy by which the suppliers are queried,
         * as well as filtering the result.
         *
         * @param selector Attachment Selector filter
         * @param suppliers Getter for a collection of AttachmentNameLookup Suppliers.
         *                  The getter will be queried every time
         *                  {@link AttachmentSelection#sync() sync()} detects changes.
         * @return Selection of Attachments
         * @param <T> Selection Attachment Class or Interface type
         */
        static <T> AttachmentSelection<T> getSelection(
                final AttachmentSelector<T> selector,
                final java.util.function.Supplier<Collection<? extends Supplier>> suppliers
        ) {
            Supplier deferMerged = new Supplier() {
                @Override
                public AttachmentNameLookup getNameLookup() {
                    // Get current list of suppliers. Optimization for empty/1-size (common)
                    Collection<? extends Supplier> currSuppliers = suppliers.get();
                    if (currSuppliers.isEmpty()) {
                        return EMPTY;
                    } else if (currSuppliers.size() == 1) {
                        return currSuppliers.iterator().next().getNameLookup(selector.strategy());
                    }

                    // Perform merging
                    List<AttachmentNameLookup> lookups = new ArrayList<>(currSuppliers.size());
                    for (Supplier supplier : currSuppliers) {
                        lookups.add(supplier.getNameLookup(selector.strategy()));
                    }
                    return AttachmentNameLookup.merge(lookups);
                }

                @Override
                public Set<Attachment> getSelfFilterOfNameLookup() {
                    Collection<? extends Supplier> currSuppliers = suppliers.get();
                    if (currSuppliers.isEmpty()) {
                        return Collections.emptySet();
                    } else if (currSuppliers.size() == 1) {
                        return currSuppliers.iterator().next().getSelfFilterOfNameLookup();
                    }

                    // Perform merging
                    Set<Attachment> excluding = new HashSet<>();
                    for (Supplier supplier : currSuppliers) {
                        excluding.addAll(supplier.getSelfFilterOfNameLookup());
                    }
                    return Collections.unmodifiableSet(excluding);
                }
            };

            return deferMerged.getSelection(selector);
        }
    }

    /**
     * A selection of attachments produced using a {@link AttachmentNameLookup} instance, or one merged
     * from several. Largely replaces the legacy {@link NameGroup} class with better type-safety and
     * filtering capabilities.
     *
     * @param <T> Selection Attachment Class or Interface type
     */
    private static final class SelectionImpl<T> implements AttachmentSelection<T> {
        private final Supplier lookupSupplier;
        private final AttachmentSelector<T> selector;
        private AttachmentNameLookup cachedLookup;
        private Set<Attachment> cachedExcluding;
        // Regenerated on demand
        private List<T> values = null;
        private List<String> names = null;

        public SelectionImpl(Supplier lookupSupplier, AttachmentSelector<T> selector) {
            if (lookupSupplier == null) {
                throw new IllegalArgumentException("Lookup Supplier is null");
            }
            if (selector == null) {
                throw new IllegalArgumentException("Attachment Selector is null");
            }
            this.lookupSupplier = lookupSupplier;
            this.cachedExcluding = Collections.emptySet();
            this.selector = selector;
            this.cachedLookup = AttachmentNameLookup.EMPTY; // Always detects changes with sync()
            this.sync();
        }

        @Override
        public AttachmentSelector<T> selector() {
            return selector;
        }

        @Override
        public List<String> names() {
            List<String> names;
            if ((names = this.names) != null) {
                return names;
            }

            // Synchronize: got to be careful we're not generating it using a stale lookup
            synchronized (this) {
                if ((names = this.names) != null) {
                    return names;
                } else {
                    return this.names = this.cachedLookup.selectNames(this.selector, this.cachedExcluding);
                }
            }
        }

        @Override
        public List<T> values() {
            List<T> values;
            if ((values = this.values) != null) {
                return values;
            }

            // Synchronize: got to be careful we're not generating it using a stale lookup
            synchronized (this) {
                if ((values = this.values) != null) {
                    return values;
                } else {
                    return this.values = this.cachedLookup.selectValues(this.selector, this.cachedExcluding);
                }
            }
        }

        @Override
        public boolean sync() {
            if (cachedLookup.isValid()) {
                return false;
            } else {
                AttachmentNameLookup lookup = lookupSupplier.getNameLookup(selector.strategy());
                Set<Attachment> excluding = selector.isExcludingSelf()
                        ? lookupSupplier.getSelfFilterOfNameLookup() : Collections.emptySet();
                synchronized (this) {
                    cachedLookup = lookup;
                    cachedExcluding = excluding;
                    values = null;
                    names = null;
                }
                return true;
            }
        }
    }

    /**
     * A group of attachments retrieved that have a certain name. Can be synchronized (on the main thread)
     * to keep it up-to-date with changes to the underlying attachments.
     *
     * @param <T> Attachment Type
     * @deprecated These days just wraps {@link AttachmentSelection}. Use that instead.
     */
    @Deprecated
    public static final class NameGroup<T extends Attachment> implements Iterable<T> {
        private static final NameGroup<Attachment> NONE = new NameGroup<>(AttachmentSelection.NONE);
        private final AttachmentSelection<T> selection;

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
            return new NameGroup<>(
                    lookupSupplier.getSelection(AttachmentSelector.named(
                            AttachmentSelector.SearchStrategy.CHILDREN,
                            name
                    ).withType(type))
            );
        }

        /**
         * Gets a constant NameGroup that refers to no attachments at all. List is always empty.
         *
         * @return Empty NameGroup
         * @param <T> Attachment Type
         */
        @SuppressWarnings("unchecked")
        public static <T extends Attachment> NameGroup<T> none() {
            return (NameGroup<T>) NONE;
        }

        private NameGroup(AttachmentSelection<T> selection) {
            this.selection = selection;
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
            return this.selection.values();
        }

        /**
         * Updates the List of attachments if these have changed. Must ideally only
         * be called on the main thread.
         */
        public void sync() {
            this.selection.sync();
        }

        @Override
        public Iterator<T> iterator() {
            return this.selection.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            this.selection.forEach(action);
        }
    }
}
