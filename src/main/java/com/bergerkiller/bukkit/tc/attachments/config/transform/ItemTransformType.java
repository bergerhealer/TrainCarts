package com.bergerkiller.bukkit.tc.attachments.config.transform;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualHybridItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Type of transformation applied when displaying an item model. This represents
 * the full scale of transformations possible, including both armorstands
 * and display entities. Provides a generic mechanism to create the spawnable
 * object and to configure the item displayed.
 */
public interface ItemTransformType {

    /**
     * Gets a unique name displayed in the selection dropdown when selecting
     * this type.
     *
     * @return Type name
     */
    String typeName();

    /**
     * Gets the serialized type name that will be written to the configuration.
     * For some types, this will have been prefixed with the
     * {@link #category() category} name.
     *
     * @return serialized name
     * @see #deserialize(String) 
     */
    default String serializedName() {
        return category().name() + "_" + serializedNameWithoutCategory();
    }

    /**
     * Gets the serialized type name that will be written to the configuration.
     * Name is <b>not</b >prefixed with the category name!
     *
     * @return serialized name
     */
    String serializedNameWithoutCategory();

    /**
     * Gets the category this item transform type is
     *
     * @return item transform type category
     */
    Category category();

    /**
     * Updates this transform type to be of a new category. This migrates existing
     * transformations for head/right hand to the most appropriate type for the
     * different categories.
     *
     * @param newCategory New category to switch to
     * @return Updated item transform type
     */
    ItemTransformType switchCategory(Category newCategory);

    /**
     * Creates a new virtual spawnable object configured according to this item
     * transform type.
     *
     * @param manager Manager
     * @param item Item to set initially
     * @return created virtual spawnable object. Type based on the transform type.
     */
    VirtualSpawnableObject create(AttachmentManager manager, ItemStack item);

    /**
     * Attempts to update the transform type and displayed item of an already created
     * spawnable object. This transform type will be applied to the entity.
     *
     * @param entity Entity result of {@link #create(AttachmentManager, ItemStack)}
     * @param item New item
     * @throws UnsupportedOperationException If the spawned entity is not compatible
     *                                       with this transform type.
     */
    void update(VirtualSpawnableObject entity, ItemStack item);

    /**
     * Gets whether the specified spawnable object can be
     * {@link #update(VirtualSpawnableObject, ItemStack) updated} using this new
     * transform type.
     *
     * @param entity VirtualSpawnableObject result of
     *               {@link #create(AttachmentManager, ItemStack)}
     * @return True if an update is possible. False if the previous entity should be
     *         de-spawned and a new one be created
     */
    boolean canUpdate(VirtualSpawnableObject entity);

    /**
     * A category of item transform types available
     */
    enum Category {
        DISPLAY("display \u24B9", ItemDisplayMode.HEAD, Display::new),
        ARMORSTAND("armorstand \u24B6", ArmorStandItemTransformType.HEAD, ArmorStand::new),
        HYBRID("hybrid \u24B9/\u24B6", HybridItemTransformType.ARMORSTAND_HEAD, Hybrid::new);

        private final String name;
        private final ItemTransformType defaultType;
        private final List<ItemTransformType> types;

        private static final Map<String, ItemTransformType> typesBySerializedName = new HashMap<>();
        static {
            for (Category category : Category.values()) {
                for (ItemTransformType type : category.types()) {
                    typesBySerializedName.put(category.name() + "_" + type.serializedNameWithoutCategory(), type);
                }
            }

            // Also store the non-category-prefixed types for legacy support
            for (ItemTransformType type : Category.ARMORSTAND.types()) {
                typesBySerializedName.put(type.serializedNameWithoutCategory(), type);
            }
        }

        @SuppressWarnings("unchecked")
        <T> Category(String name, T defaultEnumType, Function<T, ItemTransformType> ctor) {
            this(name, ctor.apply(defaultEnumType), Stream.of(CommonUtil.getClassConstants((Class<T>) defaultEnumType.getClass()))
                    .map(ctor)
                    .collect(StreamUtil.toUnmodifiableList()));
        }

        Category(String name, ItemTransformType defaultType, List<ItemTransformType> types) {
            this.name = name;
            this.defaultType = defaultType;
            this.types = types;
        }

        /**
         * Gets the default item transform type when this category is selected
         *
         * @return default item transform type
         */
        public ItemTransformType defaultType() {
            return defaultType;
        }

        /**
         * Gets a list of unique types that are part of this category. Can be used
         * to list available types, or during parsing.
         *
         * @return type names
         */
        public List<ItemTransformType> types() {
            return types;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Deserializes an item transform type by a unique name from the configuration
     *
     * @param name Name
     * @return deserialized type. Returns a default type if not found.
     */
    static ItemTransformType deserialize(String name) {
        ItemTransformType type = Category.typesBySerializedName.get(name);
        if (type == null) {
            type = Category.typesBySerializedName.get(name.toUpperCase(Locale.ENGLISH));
            if (type == null) {
                type = Category.ARMORSTAND.defaultType();
            }
        }
        // Don't use types we cannot use on the current server
        if (type.category() != Category.ARMORSTAND && !CommonCapabilities.HAS_DISPLAY_ENTITY) {
            type = type.switchCategory(Category.ARMORSTAND);
        }
        return type;
    }

    /**
     * Deserializes an item transform type from a configuration
     *
     * @param config ConfigurationNode
     * @param key Key in the configuration the serialized name is put
     * @return item transform type to use
     */
    static ItemTransformType deserialize(ConfigurationNode config, String key) {
        String name = config.get(key, String.class, null);
        if (name == null) {
            ItemTransformType defaultType = Category.ARMORSTAND.defaultType();
            config.set(key, defaultType.serializedName());
            return defaultType;
        } else {
            return deserialize(name);
        }
    }

    /**
     * ItemTransformType that only spawns armorstands
     */
    class ArmorStand implements ItemTransformType {
        private final ArmorStandItemTransformType transformType;

        public ArmorStand(ArmorStandItemTransformType transformType) {
            this.transformType = transformType;
        }

        @Override
        public String typeName() {
            return transformType.toString();
        }

        @Override
        public String serializedNameWithoutCategory() {
            return transformType.name();
        }

        @Override
        public String serializedName() {
            // For legacy support, omit the category for armorstand transforms
            return serializedNameWithoutCategory();
        }

        @Override
        public Category category() {
            return Category.ARMORSTAND;
        }

        @Override
        public ItemTransformType switchCategory(Category newCategory) {
            switch (newCategory) {
                case ARMORSTAND:
                    return this;
                case DISPLAY:
                    if (transformType.isHead()) {
                        return new Display(ItemDisplayMode.HEAD);
                    } else if (transformType.isLeftHand()) {
                        return new Display(ItemDisplayMode.THIRD_PERSON_LEFT_HAND);
                    } else if (transformType.isRightHand()) {
                        return new Display(ItemDisplayMode.THIRD_PERSON_RIGHT_HAND);
                    } else {
                        break;
                    }
                case HYBRID:
                    if (transformType.isHead()) {
                        return new Hybrid(transformType.isSmallArmorStand()
                                ? HybridItemTransformType.ARMORSTAND_HEAD_SMALL
                                : HybridItemTransformType.ARMORSTAND_HEAD);
                    } else if (transformType.isRightHand() || transformType.isLeftHand()) {
                        return new Hybrid(transformType.isSmallArmorStand()
                                ? HybridItemTransformType.ARMORSTAND_RIGHT_HAND_SMALL
                                : HybridItemTransformType.ARMORSTAND_RIGHT_HAND);
                    } else {
                        break;
                    }
            }
            return newCategory.defaultType();
        }

        @Override
        public VirtualSpawnableObject create(AttachmentManager manager, ItemStack item) {
            VirtualArmorStandItemEntity entity = new VirtualArmorStandItemEntity(manager);
            entity.setItem(transformType, item);
            return entity;
        }

        @Override
        public void update(VirtualSpawnableObject entity, ItemStack item) {
            if (canUpdate(entity)) {
                ((VirtualArmorStandItemEntity) entity).setItem(transformType, item);
            } else {
                throw new UnsupportedOperationException("Incompatible virtual entity");
            }
        }

        @Override
        public boolean canUpdate(VirtualSpawnableObject entity) {
            return entity instanceof VirtualArmorStandItemEntity;
        }

        @Override
        public String toString() {
            return "ItemTransformType.ArmorStand{" + transformType.name() + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof ArmorStand) {
                return ((ArmorStand) o).transformType == transformType;
            } else {
                return false;
            }
        }
    }

    /**
     * ItemTransformType that only spawns display entities. Legacy clients will see nothing.
     */
    class Display implements ItemTransformType {
        private final ItemDisplayMode mode;

        public Display(ItemDisplayMode mode) {
            this.mode = mode;
        }

        @Override
        public String typeName() {
            return mode.toString();
        }

        @Override
        public String serializedNameWithoutCategory() {
            return mode.name();
        }

        @Override
        public Category category() {
            return Category.DISPLAY;
        }

        @Override
        public ItemTransformType switchCategory(Category newCategory) {
            switch (newCategory) {
                case ARMORSTAND:
                    if (mode == ItemDisplayMode.HEAD) {
                        return new ArmorStand(ArmorStandItemTransformType.HEAD);
                    } else if (mode == ItemDisplayMode.THIRD_PERSON_LEFT_HAND) {
                        return new ArmorStand(ArmorStandItemTransformType.LEFT_HAND);
                    } else if (mode == ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
                        return new ArmorStand(ArmorStandItemTransformType.RIGHT_HAND);
                    } else {
                        break;
                    }
                case DISPLAY:
                    return this;
                case HYBRID:
                    if (mode == ItemDisplayMode.HEAD) {
                        return new Hybrid(HybridItemTransformType.DISPLAY_HEAD);
                    } else if (mode == ItemDisplayMode.THIRD_PERSON_RIGHT_HAND) {
                        return new Hybrid(HybridItemTransformType.DISPLAY_RIGHT_HAND);
                    } else {
                        break;
                    }
            }
            return newCategory.defaultType();
        }

        @Override
        public VirtualSpawnableObject create(AttachmentManager manager, ItemStack item) {
            VirtualDisplayItemEntity entity = new VirtualDisplayItemEntity(manager);
            entity.setItem(mode, item);
            return entity;
        }

        @Override
        public void update(VirtualSpawnableObject entity, ItemStack item) {
            if (canUpdate(entity)) {
                ((VirtualDisplayItemEntity) entity).setItem(mode, item);
            } else {
                throw new UnsupportedOperationException("Incompatible virtual entity");
            }
        }

        @Override
        public boolean canUpdate(VirtualSpawnableObject entity) {
            return entity instanceof VirtualDisplayItemEntity;
        }

        @Override
        public String toString() {
            return "ItemTransformType.Display{" + mode.name() + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Display) {
                return ((Display) o).mode == mode;
            } else {
                return false;
            }
        }
    }

    /**
     * ItemTransformType that spawns armorstands for legacy clients, and display entities
     * for 1.19.4 clients and beyond.
     */
    class Hybrid implements ItemTransformType {
        private final HybridItemTransformType transformType;

        public Hybrid(HybridItemTransformType transformType) {
            this.transformType = transformType;
        }

        @Override
        public String typeName() {
            return transformType.toString();
        }

        @Override
        public String serializedNameWithoutCategory() {
            return transformType.name();
        }

        @Override
        public Category category() {
            return Category.HYBRID;
        }

        @Override
        public ItemTransformType switchCategory(Category newCategory) {
            switch (newCategory) {
                case ARMORSTAND:
                    return new ArmorStand(transformType.armorStandTransform());
                case DISPLAY:
                    return new Display(transformType.displayMode());
                case HYBRID:
                    return this;
            }
            return newCategory.defaultType();
        }

        @Override
        public VirtualSpawnableObject create(AttachmentManager manager, ItemStack item) {
            VirtualHybridItemEntity entity = new VirtualHybridItemEntity(manager);
            entity.setItem(transformType, item);
            return entity;
        }

        @Override
        public void update(VirtualSpawnableObject entity, ItemStack item) {
            if (canUpdate(entity)) {
                ((VirtualHybridItemEntity) entity).setItem(transformType, item);
            } else {
                throw new UnsupportedOperationException("Incompatible virtual entity");
            }
        }

        @Override
        public boolean canUpdate(VirtualSpawnableObject entity) {
            return entity instanceof VirtualHybridItemEntity;
        }

        @Override
        public String toString() {
            return "ItemTransformType.Hybrid{" + transformType.name() + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Hybrid) {
                return ((Hybrid) o).transformType == transformType;
            } else {
                return false;
            }
        }
    }
}
