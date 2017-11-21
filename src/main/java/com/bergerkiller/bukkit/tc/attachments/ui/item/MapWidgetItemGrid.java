package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Shows a grid of items that can be selected using W/A/S/D
 */
public class MapWidgetItemGrid extends MapWidget {
    private int _columns = 4;
    private int _rows = 3;
    private int _itemSize = 16;
    private int _itemSpacing = 1;
    private int _scrollOffset = 0;
    private int _selectedIndex = 0;
    private List<ItemStack> _items = new ArrayList<ItemStack>();

    public MapWidgetItemGrid() {
        this.setFocusable(true);
        this.calcSize();
    }

    /**
     * Gets the currently selected item
     * 
     * @return selected item
     */
    public ItemStack getSelectedItem() {
        return (_selectedIndex >= 0 && _selectedIndex < _items.size()) ?
                _items.get(_selectedIndex) : null;
    }

    /**
     * Sets the currently selected item. If the item does not
     * exist in the added items, no item is selected.
     * 
     * @param item to select
     */
    public void setSelectedItem(ItemStack item) {
        // Find the item
        int newIndex = -1;
        if (item != null) {
            for (int i = 0; i < this._items.size(); i++) {
                if (this._items.get(i).isSimilar(item)) {
                    newIndex = i;
                    break;
                } else if (this._items.get(i).getType() == item.getType()) {
                    newIndex = i; // Match type
                }
            }
        }
        if (this._selectedIndex != newIndex) {
            this._selectedIndex = newIndex;
            this.onSelectionChanged();
        }
        this.scrollToSelection();
        this.invalidate();
    }

    /**
     * Gets the number of columns and rows in the grid
     * 
     * @param columns
     * @param rows
     * @return this item grid widget
     */
    public MapWidgetItemGrid setDimensions(int columns, int rows) {
        this._columns = columns;
        this._rows = rows;
        this.calcSize();
        this.invalidate();
        return this;
    }

    /**
     * Sets the width and height of the individual items in the grid
     * 
     * @param itemSize
     * @return this item grid widget
     */
    public MapWidgetItemGrid setItemSize(int itemSize) {
        this._itemSize = itemSize;
        this.calcSize();
        this.invalidate();
        return this;
    }

    /**
     * Sets the size of the space between the items in the grid
     * 
     * @param itemSpacing
     * @return this item grid widget
     */
    public MapWidgetItemGrid setItemSpacing(int itemSpacing) {
        this._itemSpacing = itemSpacing;
        this.calcSize();
        this.invalidate();
        return this;
    }

    /**
     * Adds all the items available in the creative mode menu of Minecraft
     * 
     * @return this item grid widget
     */
    public MapWidgetItemGrid addCreativeItems() {
        for (Material type : ItemUtil.getItemTypes()) {
            if (type == Material.AIR) continue;
            this.addItem(new ItemStack(type));
        }
        return this;
    }

    /**
     * Adds a new item to be displayed in the grid
     * 
     * @param item
     * @return this item grid widget
     */
    public MapWidgetItemGrid addItem(ItemStack item) {
        this._items.add(item);
        this.invalidate();
        return this;
    }

    @Override
    public void onDraw() {
        boolean activated = this.isActivated();

        int index = this._scrollOffset * this._columns;
        for (int row = 0; row < this._rows; row++) {
            for (int col = 0; col < this._columns; col++) {
                int x = calcX(col);
                int y = calcY(row);
                if (index >= 0 && index < this._items.size()) {
                    view.drawItem(MapResourcePack.SERVER, this._items.get(index), x, y, this._itemSize, this._itemSize);
                    if (this._selectedIndex == index) {
                        if (activated) {
                            view.drawRectangle(x, y, this._itemSize, this._itemSize, MapColorPalette.COLOR_RED);
                        } else {
                            // Show a more subtle marking perhaps?
                            view.drawRectangle(x, y, this._itemSize, this._itemSize, MapColorPalette.getColor(128, 128, 128));
                        }
                    }
                }
                index++;
            }
        }

        // Display the label of the selected item
        if (activated && this._selectedIndex >= 0 && this._selectedIndex < this._items.size()) {
            ItemStack item = this._items.get(this._selectedIndex);
            String label = ItemUtil.getDisplayName(item);

            // Deduce the maximum height we have available for showing the label
            int selRelCol = this._selectedIndex % this._columns;
            int selRelRow = (this._selectedIndex / this._columns) - this._scrollOffset;
            int spaceRows = Math.max(selRelRow, this._rows - selRelRow - 1);
            if (spaceRows > 0) {
                int maxLabelHeight = (spaceRows - 1) * this._itemSpacing + spaceRows * this._itemSize;
                int maxLabelWidth = this.getWidth();
                Dimension labelSize = this.view.calcFontSize(MapFont.MINECRAFT, label);
                if (labelSize.getWidth() > maxLabelWidth) {
                    // TODO: Try putting the label on multiple lines?
                }

                // Calculate x-coordinate where to draw the label. Clamp at borders.
                int labelX = calcX(selRelCol) + ((this._itemSize - labelSize.width) / 2);
                labelX = MathUtil.clamp(labelX, 0, this.getWidth() - labelSize.width);

                // Calculate y-coordinate where to draw the label. Find the best spot (above/below)
                int labelY = calcY(selRelRow);
                if ((this.getHeight() - (labelY + this._itemSize)) >= labelSize.getHeight()) {
                    // Below the item
                    labelY += this._itemSize;
                } else {
                    // Above the item. Clamp at top border.
                    labelY -= labelSize.height;
                    if (labelY < 0) {
                        labelY = 0;
                    }
                }

                // Actually draw the label contents
                view.fillRectangle(labelX, labelY, labelSize.width, labelSize.height, MapColorPalette.COLOR_BLACK);
                view.draw(MapFont.MINECRAFT, labelX, labelY, MapColorPalette.COLOR_WHITE, label);
            }
        }

        // If this widget is focused, show a focus box around it
        if (this.isFocused()) {
            view.drawRectangle(0, 0, this.getWidth(), this.getHeight(), MapColorPalette.COLOR_RED);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!this.isActivated()) {
            super.onKeyPressed(event);
            return;
        }
        int selCol = this._selectedIndex % this._columns;
        int selRow = this._selectedIndex / this._columns;
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            this.setSelectedCell(selCol - 1, selRow);
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            this.setSelectedCell(selCol + 1, selRow);
        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            this.setSelectedCell(selCol, selRow - 1);
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            this.setSelectedCell(selCol, selRow + 1);
        } else {
            super.onKeyPressed(event);
        }
    }

    private int calcX(int col) {
        return (col == 0) ? 0 : (col * this._itemSize + (col - 1) * this._itemSpacing);
    }

    private int calcY(int row) {
        return (row == 0) ? 0 : (row * this._itemSize + (row - 1) * this._itemSpacing);
    }

    private void setSelectedCell(int col, int row) {
        while (col >= this._columns) {
            col -= this._columns;
            row++;
        }
        while (col < 0) {
            col += this._columns;
            row--;
        }

        int maxRowIndex = (this._items.size() / this._columns) - 1;
        if (row < 0) {
            row = 0;
        } else if (row > maxRowIndex) {
            row = maxRowIndex;
        }

        int newIndex = (row * this._columns) + col;
        if (newIndex < 0) {
            newIndex = 0;
        } else if (newIndex >= this._items.size()) {
            newIndex = this._items.size() - 1;
        }
        if (this._selectedIndex != newIndex) {
            this._selectedIndex = newIndex;
            this.onSelectionChanged();
        }

        this.scrollToSelection();
        this.invalidate();
    }

    private void scrollToSelection() {
        if (this._selectedIndex == -1) {
            return;
        }

        int selRow = (this._selectedIndex / this._columns);
        int selRowRelative = selRow - this._scrollOffset;
        if (selRowRelative < 0) {
            this._scrollOffset = selRow;
        } else if (selRowRelative >= this._rows) {
            this._scrollOffset = selRow - this._rows + 1;
        }
    }

    private void calcSize() {
        this.setSize(this._columns * this._itemSize + (this._columns - 1) * this._itemSpacing,
                     this._rows * this._itemSize + (this._rows - 1) * this._itemSpacing);
    }

    public void onSelectionChanged() {
    }
}
