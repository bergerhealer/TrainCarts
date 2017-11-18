package com.bergerkiller.bukkit.tc.attachments.ui.item;

import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;

/**
 * Combines a toggleable item grid with preview and an item variant item selector list
 * to select an ItemStack
 */
public abstract class MapWidgetItemSelector extends MapWidget {
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
            onSelectedItemChanged();
        }

        @Override
        public void onKeyReleased(MapKeyEvent event) {
            super.onKeyReleased(event);
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                nav_left.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                nav_right.stopFocus();
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            super.onKeyPressed(event);
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                nav_left.sendFocus();
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                nav_right.sendFocus();
            }
        }

        @Override
        public void onFocus() {
            nav_left.setVisible(true);
            nav_right.setVisible(true);
        }

        @Override
        public void onBlur() {
            nav_left.setVisible(false);
            nav_right.setVisible(false);
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
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);

    public MapWidgetItemSelector() {
        // Set the positions/bounds of all the child widgets and set self to its limits
        grid.setDimensions(6, 4);
        variantList.setPosition((grid.getWidth() - variantList.getWidth()) / 2, 0);
        grid.setPosition(0, variantList.getHeight() + 1);
        grid.addCreativeItems();
        preview.setBounds(grid.getX(), grid.getY(), grid.getWidth(), grid.getHeight());
        nav_left.setPosition(0, 4);
        nav_right.setPosition(grid.getWidth() - nav_right.getWidth(), 4);
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
        this.nav_left.setVisible(false);
        this.nav_right.setVisible(false);
        this.addWidget(this.variantList);
        this.addWidget(this.nav_left);
        this.addWidget(this.nav_right);
        setGridOpened(false);
    }

    private void setGridOpened(boolean opened) {
        if (!opened && !this.getWidgets().contains(this.preview)) {
            this.swapWidget(this.grid, this.preview);
        } else if (opened && !this.getWidgets().contains(this.grid)) {
            this.swapWidget(this.preview, this.grid).activate();
        }
    }

    public abstract void onSelectedItemChanged();
}
