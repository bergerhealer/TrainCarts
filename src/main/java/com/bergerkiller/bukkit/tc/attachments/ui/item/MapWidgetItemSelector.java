package com.bergerkiller.bukkit.tc.attachments.ui.item;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * Combines a toggleable item grid with preview and an item variant item selector list
 * to select an ItemStack
 */
public class MapWidgetItemSelector extends MapWidget {
    private final MapWidgetItemPreview preview = new MapWidgetItemPreview() {
        
    };
    private final MapWidgetItemVariantList variantList = new MapWidgetItemVariantList() {
        @Override
        public void onActivate() {
            setGridOpened(true);
        }

        @Override
        public void onItemChanged() {
            preview.setItem(getItem());
        }
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

    public MapWidgetItemSelector() {
        // Set the positions/bounds of all the child widgets and set self to its limits
        grid.setDimensions(6, 4);
        variantList.setPosition((grid.getWidth() - variantList.getWidth()) / 2, 0);
        grid.setPosition(0, variantList.getHeight() + 1);
        grid.addCreativeItems();
        preview.setBounds(grid.getX(), grid.getY(), grid.getWidth(), grid.getHeight());
        setSize(grid.getWidth(), grid.getY() + grid.getHeight());
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
        return this.variantList.getItem();
    }

    @Override
    public void onAttached() {
        this.addWidget(this.variantList);
        setGridOpened(false);
    }

    private void setGridOpened(boolean opened) {
        if (!opened && !this.getWidgets().contains(this.preview)) {
            this.swapWidget(this.grid, this.preview);
        } else if (opened && !this.getWidgets().contains(this.grid)) {
            this.swapWidget(this.preview, this.grid).activate();
        }
    }
}
