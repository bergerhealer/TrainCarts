package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.List;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetVerticalNavigableList;
import com.bergerkiller.bukkit.tc.attachments.ui.models.ResourcePackModelListing;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;

/**
 * Combines a toggleable item grid with preview and an item variant item selector list
 * to select an ItemStack
 */
public abstract class MapWidgetItemSelector extends MapWidget implements ItemDropTarget, SetValueTarget {

    private final MapWidgetVerticalNavigableList itemOptions = new MapWidgetVerticalNavigableList() {
        @Override
        public boolean shouldInterceptInput(MapKeyEvent event) {
            // Disable this when inside the custom model data selector
            // Sadly there is no clean way to do this
            if (root.getActivatedWidget() instanceof CustomModelDataSelector) {
                return false;
            }

            return true;
        }

        @Override
        public void onLastItemDown(MapKeyEvent event) {
            display.playSound(SoundEffect.PISTON_CONTRACT);
            this.setSelectedIndex(0); // loop around first
            setGridOpened(true);
        }
    };

    private final MapWidgetItemVariantList variantList = new MapWidgetItemVariantList() {
        @Override
        public void onActivate() {
            // If no resource pack is loaded on the server end, there's nothing to show
            // Just pop open the item grid in that case (same as doing 2x DOWN)
            ResourcePackModelListing listing = TrainCarts.plugin.getModelListing();
            if (listing.isEmpty()) {
                setGridOpened(true);
                return;
            }

            // Show dialog, set the item that is selected
            for (Player owner : display.getOwners()) {
                if (!display.isControlling(owner)) {
                    continue;
                }
                listing.buildDialog(owner)
                        .cancelOnRootRightClick(false)
                        .title("Select an item model")
                        .show()
                        .thenAccept(result -> {
                            if (result.success()) {
                                setSelectedItem(result.selectedBareItem());
                            }
                        });
            }
        }
    };

    private final MapWidgetItemPreview preview = new MapWidgetItemPreview() {
        
    };
    private final MapWidgetItemGrid grid = new MapWidgetItemGrid() {
        @Override
        public void onSelectionChanged() {
            variantList.setItem(this.getSelectedItem());
        }

        @Override
        public void onAttached() {
            this.setSelectedItem(variantList.getItem());
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == Key.ENTER || event.getKey() == Key.BACK) {
                setGridOpened(false);
                return;
            }
            super.onKeyPressed(event);
        }
    };

    private final MapWidgetBlinkyButton brightnessButton;

    public MapWidgetItemSelector() {
        // Set the positions/bounds of all the child widgets and set self to its limits
        grid.setDimensions(6, 4);
        itemOptions.setSize(100, 18);
        itemOptions.setPosition((grid.getWidth() - itemOptions.getWidth()) / 2, 0);
        grid.setPosition(0, itemOptions.getHeight() + 1);
        grid.addCreativeItems();
        preview.setBounds(grid.getX(), grid.getY(), grid.getWidth(), grid.getHeight());
        setSize(grid.getWidth(), grid.getY() + grid.getHeight());

        // Different tabs for different options/properties that can be changed for an item
        { // Tab with damage value
            itemOptions.addTab().addWidget(variantList);
        }
        { // Tab with additional item options
            MapWidgetTabView.Tab tab = itemOptions.addTab(new MapWidgetTabView.Tab() {
                private final MapTexture bg_texture = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_options_bg.png");

                @Override
                public void onDraw() {
                    this.view.draw(bg_texture, 0, 0);
                }
            });

            { // Damageable or not
                final MapWidgetBlinkyButton unbreakableOption = new MapWidgetBlinkyButton() {
                    @Override
                    protected MapWidget navigateNextWidget(List<MapWidget> widgets, MapPlayerInput.Key key) {
                        if (key == MapPlayerInput.Key.LEFT) {
                            return null; // cancel
                        }
                        return super.navigateNextWidget(widgets, key);
                    }

                    @Override
                    public void onClick() {
                        CommonItemStack item = variantList.getItem();
                        if (item.isEmpty()) {
                            return;
                        }

                        item = item.clone();
                        item.setUnbreakable(!item.isUnbreakable());
                        variantList.setItem(item);
                    }
                };
                unbreakableOption.setSize(14, 14);

                this.variantList.registerItemChangedListener(new ItemChangedListener() {
                    @Override
                    public void onItemChanged(CommonItemStack item) {
                        if (item.isUnbreakable()) {
                            unbreakableOption.setTooltip("Unbreakable");
                            unbreakableOption.setIcon("attachments/item_unbreakable.png");
                        } else {
                            unbreakableOption.setTooltip("Breakable");
                            unbreakableOption.setIcon("attachments/item_breakable.png");
                        }
                    }
                }, true);
                tab.addWidget(unbreakableOption.setPosition(8, 2));
            }

            { // Name the item
                final MapWidgetSubmitText nameItemTextBox = new MapWidgetSubmitText() {
                    @Override
                    public void onAttached() {
                        this.setDescription("Enter Item Display Name\nUse empty space to reset");
                    }

                    @Override
                    public void onAccept(String text) {
                        CommonItemStack item = variantList.getItem();
                        if (item.isEmpty()) {
                            return;
                        }
                        item = item.clone();
                        if (text.trim().isEmpty()) {
                            item.setCustomName(null);
                        } else {
                            item.setCustomNameMessage(text);
                        }
                        variantList.setItem(item);
                    }
                };
                tab.addWidget(nameItemTextBox);

                final MapWidgetBlinkyButton nameItemButton = new MapWidgetBlinkyButton() {
                    @Override
                    public void onClick() {
                        nameItemTextBox.activate();
                    }
                };
                nameItemButton.setSize(14, 14);
                nameItemButton.setIcon("attachments/item_named.png");
                tab.addWidget(nameItemButton.setPosition(23, 2));

                this.variantList.registerItemChangedListener(new ItemChangedListener() {
                    @Override
                    public void onItemChanged(CommonItemStack item) {
                        if (item.hasCustomName()) {
                            nameItemButton.setTooltip("Name (\"" + item.getCustomNameMessage() + "\")");
                        } else {
                            nameItemButton.setTooltip("Name (None)");
                        }
                    }
                }, true);
            }

            { // Display Entity Brightness (1.19.4 or later, optional)
                brightnessButton = new MapWidgetBlinkyButton() {
                    @Override
                    public void onClick() {
                        onBrightnessClicked();
                    }
                };
                brightnessButton.setSize(14, 14);
                brightnessButton.setIcon("attachments/item_brightness.png");
                brightnessButton.setVisible(false);
                brightnessButton.setTooltip("Item Brightness");
                tab.addWidget(brightnessButton.setPosition(38, 2));
            }

            { // Custom Model Data (1.14 or later)
                final CustomModelDataSelector selector = new CustomModelDataSelector() {
                    @Override
                    protected MapWidget navigateNextWidget(List<MapWidget> widgets, MapPlayerInput.Key key) {
                        if (key == MapPlayerInput.Key.RIGHT) {
                            return null; // cancel
                        }
                        return super.navigateNextWidget(widgets, key);
                    }

                    @Override
                    public void onValueChanged() {
                        CommonItemStack item = variantList.getItem();
                        if (item.isEmpty()) {
                            return;
                        }

                        item = item.clone();
                        if (this.getValue() <= 0) {
                            item.clearCustomModelData();
                        } else {
                            item.setCustomModelData(this.getValue());
                        }
                        variantList.setItem(item);
                    }
                };
                selector.setPosition(54, 2);
                tab.addWidget(selector);

                this.variantList.registerItemChangedListener(new ItemChangedListener() {
                    @Override
                    public void onItemChanged(CommonItemStack item) {
                        int value = item.hasCustomModelData() ? item.getCustomModelData() : 0;
                        selector.setValue(value);
                    }
                }, true);
            }
        }

        // Handle item being changed, changing the preview and firing event
        this.variantList.registerItemChangedListener(new ItemChangedListener() {
            @Override
            public void onItemChanged(CommonItemStack item) {
                preview.setItem(item.toBukkit());
                onSelectedItemChanged();
            }
        }, false);
    }

    /**
     * Sets whether the brightness adjustment button is shown
     *
     * @param show Whether it is shown
     */
    public void setShowBrightnessButton(boolean show) {
        brightnessButton.setVisible(show);
    }

    /**
     * Sets the item selected by this item selector widget
     * 
     * @param item to set to
     * @return this item selector widget
     */
    public MapWidgetItemSelector setSelectedItem(ItemStack item) {
        this.variantList.setItem(item);
        return this;
    }

    /**
     * Gets the item currently selected by this item selector widget
     * 
     * @return selected item
     */
    public ItemStack getSelectedItem() {
        return this.variantList.getItem().toBukkit();
    }

    @Override
    public void onAttached() {
        // Add the options tab view and show preview widget
        this.addWidget(this.itemOptions);
        setGridOpened(false);
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        setGridOpened(false);
        this.variantList.setItem(item);
        display.playSound(SoundEffect.CLICK_WOOD);
        return true;
    }

    @Override
    public String getAcceptedPropertyName() {
        return this.variantList.getAcceptedPropertyName();
    }

    @Override
    public boolean acceptTextValue(String value) {
        return this.variantList.acceptTextValue(value);
    }

    private void setGridOpened(boolean opened) {
        if (!opened && !this.getWidgets().contains(this.preview)) {
            boolean focus = this.getWidgets().contains(this.grid);
            this.swapWidget(this.grid, this.preview);
            if (focus) {
                this.itemOptions.focus();
            }
        } else if (opened && !this.getWidgets().contains(this.grid)) {
            this.swapWidget(this.preview, this.grid).activate();
        }
    }

    public abstract void onSelectedItemChanged();

    /**
     * Optional: is called when the brightness adjustment button is clicked.
     * Should open some dialog where the display entity brightness levels can
     * be adjusted.
     */
    public void onBrightnessClicked() {
    }
}
