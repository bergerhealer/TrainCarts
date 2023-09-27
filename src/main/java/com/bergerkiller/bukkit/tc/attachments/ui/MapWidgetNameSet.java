package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays a scrollable set of names that can be modified. The player can scroll
 * all the way down to add additional entries, or spacebar on existing entries to prompt
 * to remove them (confirmation dialog).
 */
public abstract class MapWidgetNameSet extends MapWidget {
    private static final int ROW_HEIGHT = 11;
    private static final int HSCROLL_DELAY_TICKS = 60;
    private static final int HSCROLL_HOLD_TICKS = 30;
    private static final int HSCROLL_PIXEL_STEPS = 2;
    private final List<ListItem> items = new ArrayList<>();
    private final Set<String> uniqueItemNames = new LinkedHashSet<>();
    private int scrollOffset = 0;
    private int selectedIndex = 0;
    private int horScrollTicks = 0;
    private int numTicksOfNoScroll = 0;
    private String newNameDialogTitle = "Add a new item";
    private String newNameText = "+++ NEW +++";

    MapWidgetSubmitText newItemDialog;

    public MapWidgetNameSet() {
        this.setFocusable(true);
    }

    /**
     * Called when a new item is added by the player
     *
     * @param item Item that was added
     */
    public abstract void onItemAdded(String item);

    /**
     * Called when an item is removed (deleted) by the player
     *
     * @param item Item that was removed
     */
    public abstract void onItemRemoved(String item);

    /**
     * Adds a new item to this list. Does not call {@link #onItemAdded(String)}
     *
     * @param item Item to add
     * @return this
     */
    public MapWidgetNameSet addItem(String item) {
        if (uniqueItemNames.add(item)) {
            items.add(new ListItem(item));
            invalidate();
        }
        return this;
    }

    /**
     * Clears all previous items and adds the items specified
     *
     * @param items Items to set
     * @return this
     */
    public MapWidgetNameSet setItems(Collection<String> items) {
        this.items.clear();
        this.uniqueItemNames.clear();
        for (String item : items) {
            addItem(item);
        }
        return this;
    }

    /**
     * Gets a set containing all the items set in this menu, in the same order
     * they were added/displayed.
     *
     * @return names
     */
    public Set<String> getItems() {
        return Collections.unmodifiableSet(uniqueItemNames);
    }

    /**
     * Sets the title of the anvil dialog displayed to add a new item to this list
     *
     * @param title Title
     * @return this
     */
    public MapWidgetNameSet setNewItemDescription(String title) {
        this.newNameDialogTitle = title;
        if (newItemDialog != null) {
            newItemDialog.setDescription(title);
        }
        return this;
    }

    /**
     * Sets the text displayed for the bottom-most item, which adds a new item
     *
     * @param text Text to display
     * @return this
     */
    public MapWidgetNameSet setNewItemText(String text) {
        this.newNameText = text;
        this.invalidate();
        return this;
    }

    @Override
    public void onAttached() {
        newItemDialog = addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAccept(String text) {
                text = text.trim();
                if (text.isEmpty()) {
                    onCancel();
                    return;
                }

                ListItem newItem = new ListItem(text);
                if (items.contains(newItem)) {
                    // Show a dialog showing this item was already added
                    MapWidgetNameSet.this.addWidget(new ItemAlreadyAddedDialog());
                } else {
                    // Add to items and refresh
                    uniqueItemNames.add(newItem.name);
                    items.add(newItem);
                    selectedIndex = items.size();
                    scrollToSelection();
                    MapWidgetNameSet.this.invalidate();
                    onItemAdded(newItem.name);
                }
            }
        }).setDescription(newNameDialogTitle);
    }

    @Override
    public void onDraw() {
        int numVisibleItems = calcNumItems();

        // Grid color is yellow when hovered, black otherwise
        byte gridColor = isFocused() ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_BLACK;

        // Draw a border around the entire widget
        view.drawRectangle(0, 0, getWidth(), numVisibleItems * ROW_HEIGHT, gridColor);

        // Draw all the items and one additional item at the end to insert a new item
        for (int i = 0; i < numVisibleItems; i++) {
            int index = scrollOffset + i;
            boolean isNewIcon = (index >= items.size());
            boolean isSelected = (index == selectedIndex) && isActivated();
            byte bgColor;
            if (isNewIcon) {
                bgColor = isSelected ? MapColorPalette.getColor(0, 160, 0)
                                     : MapColorPalette.getColor(0, 64, 0);
            } else if (isSelected) {
                bgColor = MapColorPalette.getColor(128, 128, 128);
            } else {
                bgColor = (index & 0x1) == 0x1 ? MapColorPalette.getColor(32, 32, 32)
                                               : MapColorPalette.getColor(64, 64, 64);
            }

            // Fill background + line below
            view.fillRectangle(1, i * ROW_HEIGHT + 1, getWidth() - 2, ROW_HEIGHT-1, bgColor);
            view.drawLine(1, (i + 1) * ROW_HEIGHT, getWidth() - 2, (i + 1) * ROW_HEIGHT, gridColor);

            if (isNewIcon) {
                // Show "insert new item" text icon
                view.setAlignment(MapFont.Alignment.MIDDLE);
                view.draw(MapFont.MINECRAFT, getWidth()/2, i * ROW_HEIGHT + 2, MapColorPalette.COLOR_RED, newNameText);
                break;
            } else {
                // Show text of an item
                ListItem listItem = items.get(index);
                view.setAlignment(MapFont.Alignment.LEFT);
                view.getView(2, i * ROW_HEIGHT + 2, getWidth() - 3, ROW_HEIGHT - 3)
                        .draw(MapFont.MINECRAFT, -listItem.horOffset, 0, MapColorPalette.COLOR_WHITE, listItem.name);
            }
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!isActivated() || event.getKey() == MapPlayerInput.Key.BACK) {
            super.onKeyPressed(event);
        } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
            if (selectedIndex < items.size()) {
                // Delete existing item dialog
                this.addWidget(new ConfirmItemDeleteDialog() {
                    @Override
                    public void onConfirmDelete() {
                        invalidate();
                        String nameRemoved = items.remove(selectedIndex).name;
                        uniqueItemNames.remove(nameRemoved);
                        onItemRemoved(nameRemoved);
                    }
                });
            } else {
                // New item anvil dialog
                newItemDialog.activate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                //resetHScroll();
                scrollToSelection();
                invalidate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            if (selectedIndex < items.size()) {
                selectedIndex++; // Allow index=size for the 'new' button
                //resetHScroll();
                scrollToSelection();
                invalidate();
            }
        }
    }

    @Override
    public void onTick() {
        if (numTicksOfNoScroll > 0) {
            if (++numTicksOfNoScroll > HSCROLL_HOLD_TICKS) {
                resetHScroll();
            }
        } else if (++horScrollTicks >= HSCROLL_DELAY_TICKS) {
            // Start increasing the horizontal scroll offset of items that are clipped off
            int numVisibleItems = calcNumItems();
            int textViewWidth = getWidth() - 3;
            boolean scrolled = false;
            for (int i = 0; i < numVisibleItems; i++) {
                int index = scrollOffset + i;
                if (index < items.size()) {
                    scrolled |= items.get(index).scrollLeft(view, textViewWidth);
                }
            }
            if (scrolled) {
                invalidate();
            } else {
                numTicksOfNoScroll = 1;
            }
        }
    }

    private void resetHScroll() {
        if (horScrollTicks > HSCROLL_DELAY_TICKS) {
            boolean changed = false;
            for (ListItem item : items) {
                if (item.horOffset > 0) {
                    item.horOffset = 0;
                    changed = true;
                }
            }
            if (changed) {
                invalidate();
            }
        }
        horScrollTicks = 0;
        numTicksOfNoScroll = 0;
    }

    private void scrollToSelection() {
        int numItems = calcNumItems();
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if ((selectedIndex - numItems + 1) > scrollOffset) {
            scrollOffset = (selectedIndex - numItems + 1);
        }
    }

    private int calcNumItems() {
        return (getHeight() - 1) / ROW_HEIGHT;
    }

    private static class ListItem {
        public final String name;
        private int width = -1;
        public int horOffset = 0;

        public ListItem(String name) {
            this.name = name;
        }

        public boolean scrollLeft(MapCanvas view, int textViewWidth) {
            int cutOff = getWidth(view) - textViewWidth - horOffset;
            if (cutOff > 0) {
                horOffset += Math.min(cutOff, HSCROLL_PIXEL_STEPS);
                return true;
            } else {
                return false;
            }
        }

        public int getWidth(MapCanvas view) {
            int w = width;
            if (width == -1) {
                width = w = view.calcFontSize(MapFont.MINECRAFT, name).width;
            }
            return w;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return ((ListItem) o).name.equals(name);
        }
    }

    /**
     * Dialog telling the player that this name was already added
     */
    private static class ItemAlreadyAddedDialog extends MapWidgetMenu {

        public ItemAlreadyAddedDialog() {
            this.setBackgroundColor(MapColorPalette.getColor(135, 33, 33));
            this.setSize(90, 46);
        }

        @Override
        public void onAttached() {
            super.onAttached();

            // Position center of parent
            this.setPosition((parent.getWidth() - getWidth())/2, (parent.getHeight() - getHeight())/2);

            // Label
            this.addWidget(new MapWidgetText()
                    .setText("This item was\nalready added!")
                    .setBounds(5, 5, 80, 30));

            // Ok
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ItemAlreadyAddedDialog.this.close();
                }
            }.setText("OK").setBounds(27, 27, 36, 13));
        }

        /**
         * Called when the player specifically said 'yes' to deleting
         */
        public void onConfirmDelete() {
        }
    }

    /**
     * Dialog asking whether or not it is OK to delete an item
     */
    private static class ConfirmItemDeleteDialog extends MapWidgetMenu {

        public ConfirmItemDeleteDialog() {
            this.setBackgroundColor(MapColorPalette.getColor(135, 33, 33));
            this.setSize(90, 40);
        }

        @Override
        public void onAttached() {
            super.onAttached();

            // Position center of parent
            this.setPosition((parent.getWidth() - getWidth())/2, (parent.getHeight() - getHeight())/2);

            // Label
            this.addWidget(new MapWidgetText()
                    .setText("Delete this item?")
                    .setBounds(5, 5, 80, 30));

            // Cancel
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmItemDeleteDialog.this.close();
                }
            }.setText("No").setBounds(6, 21, 36, 13));

            // Yes!
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmItemDeleteDialog.this.close();
                    ConfirmItemDeleteDialog.this.onConfirmDelete();
                }
            }.setText("Yes").setBounds(48, 21, 36, 13));
        }

        /**
         * Called when the player specifically said 'yes' to deleting
         */
        public void onConfirmDelete() {
        }
    }
}
