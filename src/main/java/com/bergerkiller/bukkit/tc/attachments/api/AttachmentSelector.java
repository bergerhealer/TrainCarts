package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerGroup;

import java.util.Optional;

/**
 * Uniquely defines a selection of attachments. The selection can optionally
 * include an attachment name and type to filter by. It can also search/filter the
 * attachments in one of the following ways:
 * <ul>
 *     <li>Children of the attachment</li>
 *     <li>All attachments of the same root (cart)</li>
 *     <li>Parent attachments of the attachment</li>
 * </ul>
 * A prepared selector can be used to retrieve a {@link AttachmentSelection}:
 * <ul>
 *     <li>{@link Attachment#getSelection(AttachmentSelector) Attachment.getSelection(selector)}</li>
 *     <li>{@link AttachmentControllerGroup#getSelection(AttachmentSelector) group.getAttachments().getSelection(selector)}</li>
 *     <li>Merging selection results of many attachments together with the static getSelection() helper method.
 *         Pass a list of attachments, carts or trains to it. And it'll combine the results.</li>
 * </ul>
 *
 * @param <T> Type filter class. Results will always implement Attachment.
 */
public final class AttachmentSelector<T> {
    private final SearchStrategy strategy;
    private final Optional<String> nameFilter;
    private final Class<T> typeFilter;

    /**
     * Gets an attachment selector that selects no attachments at all
     *
     * @return AttachmentSelector
     */
    public static AttachmentSelector<Attachment> none() {
        return SearchStrategy.NONE.selectAll();
    }

    /**
     * Gets an attachment selector that selects no attachments at all. Does track
     * the type filter to apply.
     *
     * @param typeFilter Type Filter
     * @return AttachmentSelector
     * @param <T> Type filter class
     */
    public static <T> AttachmentSelector<T> none(Class<T> typeFilter) {
        return new AttachmentSelector<>(SearchStrategy.NONE, Optional.empty(), typeFilter);
    }

    /**
     * Gets an attachment selector that selects all attachments of a certain type.
     * Uses the default search strategy of {@link SearchStrategy#ROOT_CHILDREN}.
     *
     * @param typeFilter Type Filter
     * @return AttachmentSelector
     * @param <T> Type filter class
     */
    public static <T> AttachmentSelector<T> all(Class<T> typeFilter) {
        return new AttachmentSelector<>(SearchStrategy.ROOT_CHILDREN, Optional.empty(), typeFilter);
    }

    /**
     * Gets an attachment selector for all attachments using a certain search strategy
     *
     * @param strategy Search Strategy
     * @return AttachmentSelector
     */
    public static AttachmentSelector<Attachment> all(SearchStrategy strategy) {
        return strategy.selectAll();
    }

    /**
     * Gets an attachment selector for attachments matching a certain attachment name.
     * If the input name filter is null or empty, returns {@link #none()}
     *
     * @param strategy Search Strategy
     * @param nameFilter Name filter to match attachments by
     * @return AttachmentSelector
     */
    public static AttachmentSelector<Attachment> named(SearchStrategy strategy, String nameFilter) {
        return strategy.selectNamed(nameFilter);
    }

    private AttachmentSelector(SearchStrategy strategy, Optional<String> nameFilter, Class<T> typeFilter) {
        if (strategy == null) {
            throw new IllegalArgumentException("Search Strategy is null");
        }
        if (typeFilter == null) {
            throw new IllegalArgumentException("Type Filter is null");
        }
        this.strategy = strategy;
        this.nameFilter = nameFilter;
        this.typeFilter = typeFilter;
    }

    /**
     * Gets the search strategy used to select attachments
     *
     * @return search strategy
     */
    public SearchStrategy strategy() {
        return strategy;
    }

    /**
     * Gets the name filter used to select attachments. If no filter
     * is used, returns an empty optional.
     *
     * @return Name filter, or empty if not used
     */
    public Optional<String> nameFilter() {
        return nameFilter;
    }

    /**
     * Gets the type filter by which attachments are filtered. If no filter
     * is used, returns {@link Attachment}.class
     *
     * @return Type filter
     */
    public Class<T> typeFilter() {
        return typeFilter;
    }

    /**
     * Gets whether a {@link #typeFilter()} is configured
     *
     * @return True if results are filtered by type
     */
    public boolean usesTypeFilter() {
        return typeFilter != Attachment.class;
    }

    /**
     * Changes this selector so that it selects all attachments, instead of
     * filtering by a certain attachment name.
     *
     * @return Updated AttachmentSelector
     */
    public AttachmentSelector<T> withSelectAll() {
        return new AttachmentSelector<>(strategy, Optional.empty(), typeFilter);
    }

    /**
     * Changes this selector so that it selects only attachments that have
     * a certain name assigned.
     *
     * @param name Name filter to set
     * @return Updated AttachmentSelector
     */
    public AttachmentSelector<T> withName(String name) {
        if (name == null || name.isEmpty()) {
            return new AttachmentSelector<>(SearchStrategy.NONE, Optional.empty(), typeFilter);
        } else {
            return new AttachmentSelector<>(strategy, Optional.of(name), typeFilter);
        }
    }

    /**
     * Changes this selector so that it uses a different search strategy
     *
     * @param strategy New search strategy to use
     * @return Updated AttachmentSelector
     */
    public AttachmentSelector<T> withStrategy(SearchStrategy strategy) {
        return new AttachmentSelector<>(strategy, nameFilter, typeFilter);
    }

    /**
     * Changes this selector so that it filters the results to include only
     * the type specified. Discards any previous type filter that was set.
     *
     * @param typeFilter Class Type to filter the attachment results by
     * @return Updated AttachmentSelector
     * @param <A> Class Type
     */
    public <A> AttachmentSelector<A> withType(Class<A> typeFilter) {
        return new AttachmentSelector<>(strategy, nameFilter, typeFilter);
    }

    @Override
    public int hashCode() {
        if (nameFilter.isPresent()) {
            return nameFilter.get().hashCode();
        } else {
            return typeFilter.hashCode();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof AttachmentSelector) {
            AttachmentSelector<?> other = (AttachmentSelector<?>) o;
            return strategy == other.strategy &&
                    nameFilter.equals(other.nameFilter) &&
                    typeFilter.equals(other.typeFilter);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AttachmentSelector{type=" + typeFilter.getSimpleName() +
                ", strategy=" + strategy +
                ", name=" + (nameFilter.orElse("<any>")) + "}";
    }

    /**
     * Gets whether an attachment matches this selector. Does not check for search strategy.
     *
     * @param attachment Attachment
     * @return True if it matches this selector's filters
     */
    public boolean matches(Attachment attachment) {
        return matchesExceptName(attachment) &&
                (!nameFilter.isPresent() || attachment.getNames().contains(nameFilter.get()));
    }

    /**
     * Gets whether an attachment matches this selector. Does not check for search strategy,
     * and does not check any name filter (if set).
     *
     * @param attachment Attachment
     * @return True if it matches this selector's filters (except name)
     */
    public boolean matchesExceptName(Attachment attachment) {
        return typeFilter.isInstance(attachment);
    }

    /**
     * Writes this attachment selector configuration, excluding type filter, to a configuration
     * at a particular key.
     *
     * @param config ConfigurationNode
     * @param key Key to write to in the Configuration
     */
    public void writeToConfig(ConfigurationNode config, String key) {
        if (strategy == SearchStrategy.NONE) {
            config.remove(key);
        } else if (strategy == SearchStrategy.ROOT_CHILDREN && nameFilter.isPresent()) {
            // Simplified: just store the name to filter by
            config.set(key, nameFilter.get());
        } else {
            // Store a configuration block with all contents
            ConfigurationNode block = config.getNode(key);
            block.set("strategy", strategy);
            if (nameFilter.isPresent()) {
                block.set("name", nameFilter.get());
            } else {
                block.remove("name");
            }
        }
    }

    /**
     * Loads a previously saved attachment selector configuration, excluding type filter, from
     * a configuration at a particular key.
     *
     * @param config ConfigurationNode
     * @param key Key to read at from the Configuration
     * @return Attachment Selector for all attachments (no type filter)
     */
    public static AttachmentSelector<Attachment> readFromConfig(ConfigurationNode config, String key) {
        ConfigurationNode block = config.getNodeIfExists(key);
        if (block != null) {
            SearchStrategy strategy = block.getOrDefault("strategy", SearchStrategy.CHILDREN);
            String nameFilter = block.getOrDefault("name", String.class, null);
            if (nameFilter != null) {
                return strategy.selectNamed(nameFilter);
            } else {
                return strategy.selectAll();
            }
        } else {
            String nameFilter = config.getOrDefault(key, String.class, null);
            if (nameFilter != null) {
                return SearchStrategy.ROOT_CHILDREN.selectNamed(nameFilter);
            } else {
                return none();
            }
        }
    }

    private static final MapTexture SEARCH_STRATEGY_ICONS = MapTexture.loadPluginResource(TrainCarts.plugin,
            "com/bergerkiller/bukkit/tc/textures/attachments/search_strategies.png");

    /**
     * A search strategy by which attachments are selected
     */
    public enum SearchStrategy {
        /** No attachments are returned. Ever. Used for invalid configurations. */
        NONE("Disabled"),
        /** All child attachments of the same attachment root. This is the default behavior. */
        ROOT_CHILDREN("All of cart"),
        /** Child attachments of the attachment */
        CHILDREN("Children"),
        /** Parent attachment of the attachment */
        PARENTS("Parents");

        private final String caption;
        private final AttachmentSelector<Attachment> all;
        private final MapTexture iconDefault, iconFocused;

        SearchStrategy(String caption) {
            this.caption = caption;
            this.all = new AttachmentSelector<>(this, Optional.empty(), Attachment.class);
            this.iconDefault = SEARCH_STRATEGY_ICONS.getView(ordinal() * 11, 0, 11, 7).clone();
            this.iconFocused = SEARCH_STRATEGY_ICONS.getView(ordinal() * 11, 7, 11, 7).clone();
        }

        /**
         * Gets the caption/tooltip text displayed when this search strategy
         * is selected
         *
         * @return Caption
         */
        public String getCaption() {
            return caption;
        }

        /**
         * Gets the icon for the search strategy selector button when this strategy is selected
         *
         * @param focused Whether the icon has focus
         * @return Icon (11x7)
         */
        public MapTexture getIcon(boolean focused) {
            return focused ? iconFocused : iconDefault;
        }

        /**
         * Gets an attachment selector for all attachments, using this search strategy
         *
         * @return Attachment selector for all attachments
         */
        public AttachmentSelector<Attachment> selectAll() {
            return all;
        }

        /**
         * Gets an attachment selector for attachments matching a certain attachment name.
         * If the input name filter is null or empty, returns {@link #none()}
         *
         * @param nameFilter Name filter to match attachments by
         * @return AttachmentSelector
         */
        public AttachmentSelector<Attachment> selectNamed(String nameFilter) {
            if (nameFilter == null || nameFilter.isEmpty()) {
                return NONE.selectAll();
            } else {
                return new AttachmentSelector<>(this, Optional.of(nameFilter), Attachment.class);
            }
        }
    }
}
