package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;

/**
 * A map widget showing a selection of text items between which can be selected
 */
public class MapWidgetSelectionBox extends MapWidget {
    private List<String> items = new ArrayList<String>();
    private int selectedIndex = -1;
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);

    public MapWidgetSelectionBox() {
        this.setFocusable(true);
    }

    public List<String> getItems() {
        return this.items;
    }

    public void clearItems() {
        this.items.clear();
        if (this.selectedIndex != -1) {
            this.selectedIndex = -1;
            this.invalidate();
        }
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

    /**
     * Removes a selectable option. If the option was selected
     * previously, the selection closest next element is selected
     * instead.
     * 
     * @param item
     * @return this selection box widget
     */
    public MapWidgetSelectionBox removeItem(String item) {
        int index = this.items.indexOf(item);
        if (index != -1) {
            this.items.remove(index);
            if (index == this.selectedIndex) {
                if (this.selectedIndex >= this.items.size()) {
                    this.selectedIndex--;
                }
                this.invalidate();
                this.onSelectedItemChanged();
            } else if (index < this.selectedIndex) {
                this.selectedIndex--;
            }
        }
        return this;
    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public String getSelectedItem() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.items.size()) {
            return this.items.get(this.selectedIndex);
        } else {
            return null;
        }
    }

    public void setSelectedItem(String item) {
        setSelectedIndex(this.items.indexOf(item));
    }

    public void setSelectedIndex(int new_index) {
        if (new_index != this.selectedIndex) {
            this.selectedIndex = new_index;
            this.invalidate();
            this.onSelectedItemChanged();
        }
    }

    public int getItemCount() {
        return this.items.size();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        nav_left.setVisible(false);
        nav_right.setVisible(false);
        this.addWidget(nav_left);
        this.addWidget(nav_right);
    }

    @Override
    public void onDraw() {
        int offset = nav_left.getWidth() + 1;

        MapWidgetButton.fillBackground(this.view.getView(offset + 1, 1, getWidth() - 2 * offset - 2, getHeight() - 2), true, this.isFocused());
        this.view.drawRectangle(offset, 0, getWidth() - 2 * offset, getHeight(), this.isFocused() ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

        String selectedItem = this.getSelectedItem();
        if (selectedItem != null) {
            // Display text of the item centred in the middle
            this.view.setAlignment(Alignment.MIDDLE);
            this.view.draw(MapFont.MINECRAFT, getWidth() / 2, 2, MapColorPalette.COLOR_WHITE, selectedItem);
        } else {
            // No item selected / error
        }
    }

    @Override
    public void onActivate() {
        // Suppress
    }

    @Override
    public void onBoundsChanged() {
        nav_left.setPosition(0, 0);
        nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 0);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            nav_left.sendFocus();
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                this.invalidate();
                this.onSelectedItemChanged();
                this.display.playSound(SoundEffect.CLICK);
            }
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.sendFocus();
            if (this.selectedIndex < (this.items.size() - 1)) {
                this.selectedIndex++;
                this.invalidate();
                this.onSelectedItemChanged();
                this.display.playSound(SoundEffect.CLICK);
            }
        } else {
            super.onKeyPressed(event);
        }
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
    public void onFocus() {
        nav_left.setVisible(true);
        nav_right.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_left.setVisible(false);
        nav_right.setVisible(false);
    }

    public void onSelectedItemChanged() {
    }
}
