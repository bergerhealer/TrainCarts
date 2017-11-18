package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * A map widget showing a selection of text items between which can be selected
 */
public class MapWidgetSelectionBox extends MapWidget {
    private List<String> items = new ArrayList<String>();
    private int selectedIndex = -1;

    public MapWidgetSelectionBox() {
        this.setFocusable(true);
    }

    /**
     * Adds a selectable option
     * 
     * @param item
     * @return this selection box widget
     */
    public MapWidgetSelectionBox addItem(String item) {
        this.items.add(item);
        return this;
    }

    public String getSelectedItem() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.items.size()) {
            return this.items.get(this.selectedIndex);
        } else {
            return null;
        }
    }

    public void setSelectedItem(String item) {
        this.selectedIndex = this.items.indexOf(item);
        this.invalidate();
    }

    @Override
    public void onDraw() {
        this.view.drawRectangle(0, 0, getWidth(), getHeight(), this.isFocused() ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

        String selectedItem = this.getSelectedItem();
        if (selectedItem != null) {
            // Display text of the item centred in the middle
            this.view.setAlignment(Alignment.MIDDLE);
            this.view.draw(MapFont.MINECRAFT, getWidth() / 2, 1, MapColorPalette.COLOR_WHITE, selectedItem);
        } else {
            // No item selected / error
        }
    }

    @Override
    public void onActivate() {
        // Suppress
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                this.invalidate();
                this.onSelectedItemChanged();
            }
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            if (this.selectedIndex < (this.items.size() - 1)) {
                this.selectedIndex++;
                this.invalidate();
                this.onSelectedItemChanged();
            }
        } else {
            super.onKeyPressed(event);
        }
    }

    public void onSelectedItemChanged() {
    }
}
