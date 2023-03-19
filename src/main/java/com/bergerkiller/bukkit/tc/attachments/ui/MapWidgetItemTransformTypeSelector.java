package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.transform.ItemTransformType;

import java.util.Arrays;

/**
 * Widget that allows selecting a {@link ItemTransformType}. On old versions of
 * the server it only selects armorstand transform types, and shows a single slider.
 * On newer versions, it shows two sliders for category and type.
 */
public abstract class MapWidgetItemTransformTypeSelector extends MapWidget {
    private MapWidgetSelectionBox categorySelector;
    private TransformTypeSelector typeSelector;
    private ItemTransformType selectedType = ItemTransformType.Category.ARMORSTAND.defaultType();

    public abstract void onSelectedTypeChanged(ItemTransformType type);

    public static int defaultHeight() {
        return CommonCapabilities.HAS_DISPLAY_ENTITY ? (2*11+1) : 11;
    }

    public static final MapFont<Character> ITEMTRANSFORMTYPE_FONT = new MapFont<Character>() {
        private MapTexture FONT_TEXTURE = null;

        @Override
        protected MapTexture loadSprite(Character key) {
            // Special fonts for use in this selector
            if (FONT_TEXTURE == null) {
                FONT_TEXTURE = MapTexture.loadPluginResource(TrainCarts.plugin,
                        "com/bergerkiller/bukkit/tc/textures/attachments/font.png");
            }

            // Armorstand
            if (key == '\u24B6') {
                return FONT_TEXTURE.getView(0, 0, 5, 8).clone();
            }

            // Display
            if (key == '\u24B9') {
                return FONT_TEXTURE.getView(6, 0, 5, 8).clone();
            }

            // Small icon
            if (key == '\u24AE') {
                return FONT_TEXTURE.getView(12, 0, 5, 8).clone();
            }

            // Fallback
            return MINECRAFT.getSprite(key);
        }

        @Override
        public boolean isNewline(Character key) {
            return key != null && key == '\n';
        }
    };

    public MapWidgetItemTransformTypeSelector() {
        this.setSize(100, defaultHeight());
    }

    @Override
    public void onAttached() {
        if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
            // Add a category selector
            this.categorySelector = addWidget(new MapWidgetSelectionBox() {
                private boolean changingItems = false;

                @Override
                public void onAttached() {
                    super.onAttached();
                    setFont(ITEMTRANSFORMTYPE_FONT);
                    changingItems = true;
                    for (ItemTransformType.Category category : ItemTransformType.Category.values()) {
                        addItem(category.toString());
                    }
                    setSelectedIndex(Arrays.asList(ItemTransformType.Category.values())
                            .indexOf(selectedType.category()));
                    changingItems = false;
                }

                @Override
                public void onSelectedItemChanged() {
                    if (!changingItems && getSelectedIndex() != -1) {
                        ItemTransformType.Category newCategory = ItemTransformType.Category.values()[getSelectedIndex()];
                        ItemTransformType newType = selectedType.switchCategory(newCategory);
                        if (!newType.equals(selectedType)) {
                            selectedType = newType;
                            typeSelector.updateItems();
                            onSelectedTypeChanged(selectedType);
                        }
                    }
                }
            });
            this.categorySelector.setClipParent(this.isClipParent());
        }

        // Add a type selector showing items of the current category (or armorstand)
        this.typeSelector = addWidget(new TransformTypeSelector());
        this.typeSelector.setClipParent(this.isClipParent());

        // Refresh size of added widgets
        this.onBoundsChanged();
    }

    @Override
    public void onDetached() {
        categorySelector = null;
        typeSelector = null;
    }

    @Override
    public void onBoundsChanged() {
        if (categorySelector != null) {
            int sliderHeight = (getHeight() - 1) / 2;
            categorySelector.setBounds(0, 0, getWidth(), sliderHeight);
            typeSelector.setBounds(0, getHeight() - sliderHeight, getWidth(), sliderHeight);
        } else {
            typeSelector.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    public ItemTransformType getSelectedType() {
        return selectedType;
    }

    public void setSelectedType(ItemTransformType selectedType) {
        this.selectedType = selectedType;
        if (typeSelector != null) {
            // Switch the two selectors
            if (this.categorySelector != null) {
                this.categorySelector.setSelectedIndex(Arrays.asList(ItemTransformType.Category.values())
                        .indexOf(selectedType.category()));
            }
            this.typeSelector.setSelectedIndex(selectedType.category().types().indexOf(selectedType));
        }
    }

    private class TransformTypeSelector extends MapWidgetSelectionBox {
        private boolean changingItems = false;

        @Override
        public void onAttached() {
            super.onAttached();
            setFont(ITEMTRANSFORMTYPE_FONT);
            updateItems();
        }

        public void updateItems() {
            changingItems = true;
            clearItems();

            ItemTransformType shownSelectedType = selectedType;
            if (!CommonCapabilities.HAS_DISPLAY_ENTITY) {
                shownSelectedType = shownSelectedType.switchCategory(ItemTransformType.Category.ARMORSTAND);
            }
            for (ItemTransformType type : shownSelectedType.category().types()) {
                addItem(type.typeName());
            }
            setSelectedIndex(shownSelectedType.category().types().indexOf(shownSelectedType));
            changingItems = false;
        }

        @Override
        public void onSelectedItemChanged() {
            if (!changingItems && getSelectedIndex() != -1) {
                selectedType = selectedType.category().types().get(getSelectedIndex());
                onSelectedTypeChanged(selectedType);
            }
        }
    }
}
