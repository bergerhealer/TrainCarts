package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.map.MapTexture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dropdown selector with icon rendering support. When collapsed, only the icon
 * of the selected item is shown. When activated, a dropdown list appears below.
 */
public abstract class MapWidgetIconDropDown extends MapWidgetBlinkyButton {
    private final List<String> items = new ArrayList<String>();
    private final DropDownListWidget dropDownList = new DropDownListWidget();
    private int selectedIndex = -1;
    private int maxVisibleItems = 5;
    private int dropDownWidth = 80;
    private boolean loopAround = false;
    private int lastHandledSelectedIndex = Integer.MIN_VALUE;
    private String lastHandledSelectedItem = null;

    public MapWidgetIconDropDown() {
        this.setRepeatClickEnabled(false);
        this.setFocusable(true);
        this.setRetainChildWidgets(true);
        this.addWidget(dropDownList);
    }

    /**
     * Called when the item selected in this drop down is changed.
     * Call {@link #getSelectedIndex()} and {@link #getSelectedItem()} to get the new selection.
     */
    public abstract void onSelectedItemChanged();

    /**
     * Creates an icon texture for an input item value.
     * Called once per item index, then cached in the dropdown item widget itself.
     *
     * @param itemIndex Item index
     * @param item Item value
     * @return icon texture to use, null for no icon
     */
    protected abstract MapTexture createItemIcon(int itemIndex, String item);

    /**
     * Creates the tooltip to display while hovering over this button, for an input item.
     * Called once per item index when switching selection. Can be overridden
     * to make the tooltip text differ from the item text value in the dropdown list.
     *
     * @param itemIndex Item index
     * @param item Item value
     * @return Tooltip text, return null to disable the tooltip
     */
    protected String createItemTooltip(int itemIndex, String item) {
        return item;
    }

    public List<String> getItems() {
        return this.items;
    }

    public void clearItems() {
        int oldItemIndex = getSelectedIndex();
        this.items.clear();
        this.selectedIndex = -1;
        this.dropDownList.rebuildItems();
        handleUpdateSelection(oldItemIndex);
        this.invalidate();
    }

    public MapWidgetIconDropDown addItem(String item) {
        int oldItemIndex = getSelectedIndex();
        this.items.add(item);
        if (this.selectedIndex == -1) {
            this.selectedIndex = 0;
        }
        this.dropDownList.rebuildItems();
        handleUpdateSelection(oldItemIndex);
        this.invalidate();
        return this;
    }

    public int getItemCount() {
        return this.items.size();
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

    public void setSelectedIndex(int selectedIndex) {
        int oldItemIndex = this.getSelectedIndex();
        int maxIndex = this.items.size() - 1;
        int newIndex = Math.max(-1, Math.min(maxIndex, selectedIndex));
        if (newIndex != this.selectedIndex) {
            this.selectedIndex = newIndex;
            this.dropDownList.invalidate();
            this.dropDownList.updateScrollForSelection();
            handleUpdateSelection(oldItemIndex);
            this.invalidate();
        }
    }

    public MapWidgetIconDropDown setMaxVisibleItems(int maxVisibleItems) {
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        this.dropDownList.updateBounds();
        this.dropDownList.updateScrollForSelection();
        return this;
    }

    public int getMaxVisibleItems() {
        return this.maxVisibleItems;
    }

    public MapWidgetIconDropDown setDropDownWidth(int dropDownWidth) {
        this.dropDownWidth = Math.max(1, dropDownWidth);
        this.dropDownList.updateBounds();
        this.dropDownList.updateScrollForSelection();
        return this;
    }

    public int getDropDownWidth() {
        return this.dropDownWidth;
    }

    public MapWidgetIconDropDown setLoopAround(boolean loopAround) {
        this.loopAround = loopAround;
        return this;
    }

    public boolean isLoopAround() {
        return this.loopAround;
    }

    public boolean isDropdownOpen() {
        return isActivated();
    }

    public void setDropdownOpen(boolean open) {
        if (open) {
            // Set activated, which will mark the dropdown as being open and as well
            // hides the tooltip (blur, because activated not just focused).
            if (root != null) {
                root.setActivatedWidget(this);
            }
        } else if (isActivated()) {
            focus();
        }
        updateDropDownState();
    }

    @Override
    public void onAttached() {
        this.lastHandledSelectedIndex = Integer.MIN_VALUE;
        this.lastHandledSelectedItem = null;
        this.dropDownList.setVisible(false);
    }

    @Override
    public void onBoundsChanged() {
        super.onBoundsChanged();
        this.dropDownList.updateBounds();
    }

    @Override
    public void onBlur() {
        super.onBlur();
        updateDropDownState();
    }

    @Override
    public void onClick() {
        setDropdownOpen(true);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        // Normal focusing navigation logic
        // Enter will activate (click), which will make isDropdownOpen() = true
        if (!this.isDropdownOpen()) {
            super.onKeyPressed(event);
            return;
        }

        //  Back / enter is special, as we don't want it to go through navigation and close the entire dialog
        // it sits inside or trigger a re-activation.
        if (event.getKey() == MapPlayerInput.Key.ENTER || event.getKey() == MapPlayerInput.Key.BACK) {
            this.focus(); //
            updateDropDownState();
            return;
        }

        // When navigating left/right, handle it using the parent widget automatically.
        // This will close the dropdown (+blur) and focus the neighboring widget, if any.
        if (event.getKey() == MapPlayerInput.Key.LEFT || event.getKey() == MapPlayerInput.Key.RIGHT) {
            setDropdownOpen(false);
            super.onKeyPressed(event);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.UP) {
            cycleSelected(-1);
            return;
        }

        if (event.getKey() == MapPlayerInput.Key.DOWN) {
            cycleSelected(1);
            return;
        }
    }

    /**
     * Draws one item inside the dropdown list.
     * By default, this draws the cached icon and item text.
     *
     * @param view Clipped item view
     * @param item Item text
     * @param itemIndex Item index
     * @param selected Whether selected
     * @param focused Whether focused
     */
    protected void drawDropDownItem(MapCanvas view, String item, int itemIndex, boolean selected, boolean focused) {
        int iconOffset = 0;
        MapTexture icon = getCachedIcon(itemIndex, item);
        if (icon != null) {
            view.draw(icon, 0, 0);
            iconOffset = icon.getWidth();
        }

        byte outlineColor = selected ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK;
        MapWidgetButton.fillBackground(view.getView(iconOffset, 1, view.getWidth() - 1 - iconOffset, view.getHeight() - 2), true, selected);
        view.drawRectangle(0, 0, view.getWidth(), view.getHeight(), outlineColor);
        if (iconOffset > 0) {
            view.drawLine(iconOffset - 1, 1, iconOffset - 1, view.getHeight() - 2, outlineColor);
        }

        int textX = Math.max(0, iconOffset + 2);
        if (textX < view.getWidth()) {
            view.draw(MapFont.MINECRAFT, textX, Math.max(0, (view.getHeight() - 7) / 2),
                    selected ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_WHITE,
                    (item == null) ? "" : item);
        }
    }

    private void cycleSelected(int delta) {
        if (this.items.isEmpty()) {
            return;
        }

        int newIndex = this.selectedIndex + delta;
        if (this.loopAround) {
            if (newIndex < 0) {
                newIndex = this.items.size() - 1;
            } else if (newIndex >= this.items.size()) {
                newIndex = 0;
            }
        } else {
            if (newIndex < 0 || newIndex >= this.items.size()) {
                return;
            }
        }

        if (newIndex != this.selectedIndex) {
            setSelectedIndex(newIndex);
            if (this.display != null) {
                this.display.playSound(SoundEffect.CLICK);
            }
        }
    }

    protected final void clearIconCache() {
        this.dropDownList.clearIconCache();
        this.lastHandledSelectedIndex = Integer.MIN_VALUE;
        this.lastHandledSelectedItem = null;
        handleUpdateSelection(getSelectedIndex());
        this.dropDownList.invalidate();
    }

    private void handleUpdateSelection(int prevSelectedIndex) {
        String selectedItem = this.getSelectedItem();
        if (this.selectedIndex == this.lastHandledSelectedIndex && Objects.equals(selectedItem, this.lastHandledSelectedItem)) {
            return;
        }

        this.lastHandledSelectedIndex = this.selectedIndex;
        this.lastHandledSelectedItem = selectedItem;

        if (this.selectedIndex != -1) {
            MapTexture icon = getCachedIcon(this.selectedIndex, selectedItem);
            if (icon != null) {
                this.setIcon(icon);
            }
            this.setTooltip(this.getSelectedIndex() == -1 ? null : createItemTooltip(this.getSelectedIndex(), selectedItem));

            if (prevSelectedIndex != this.getSelectedIndex()) {
                this.onSelectedItemChanged();
            }
        }
    }

    private MapTexture getCachedIcon(int itemIndex, String item) {
        DropDownItemWidget itemWidget = this.dropDownList.getItemWidget(itemIndex);
        if (itemWidget != null) {
            return itemWidget.getCachedIcon();
        }
        return createItemIcon(itemIndex, item);
    }

    private void updateDropDownState() {
        this.dropDownList.setVisible(this.isDropdownOpen());
        this.dropDownList.updateBounds();
        if (this.isVisible()) {
            this.dropDownList.updateScrollForSelection();
            this.dropDownList.invalidate();
        }
        this.dropDownList.invalidate();
        this.invalidate();
    }

    private class DropDownListWidget extends MapWidget {
        private final MapWidgetScroller scroller = new MapWidgetScroller();

        DropDownListWidget() {
            this.setClipParent(false);
            this.scroller.setScrollPadding(0);
            this.scroller.getContainer().setRetainChildWidgets(true);
            this.addWidget(this.scroller);
            this.setRetainChildWidgets(true);
        }

        void updateBounds() {
            int visibleCount = Math.min(maxVisibleItems, Math.max(1, items.size()));
            int itemHeight = Math.max(1, MapWidgetIconDropDown.this.getHeight());
            int safeBoundary = Math.max(1, itemHeight / 2);
            int width = Math.max(1, dropDownWidth);
            int height = visibleCount * itemHeight;

            int absParentX = MapWidgetIconDropDown.this.getAbsoluteX();
            int absParentCenterX = absParentX + (MapWidgetIconDropDown.this.getWidth() / 2);
            int absX = absParentCenterX - (width / 2);

            int displayWidth = (display != null) ? display.getWidth() : 128;
            if (absX < 0) {
                absX = 0;
            } else if ((absX + width) > displayWidth) {
                absX = displayWidth - width;
            }

            int relX = absX - absParentX;
            this.setBounds(relX, MapWidgetIconDropDown.this.getHeight(), width, height);
            this.scroller.setBounds(0, 0, this.getWidth(), this.getHeight());
            this.scroller.setScrollPadding(safeBoundary);
            this.recalculateContainerSize();
            this.updateScrollForSelection();
        }

        void rebuildItems() {
            MapWidget container = this.scroller.getContainer();
            while (container.getWidgetCount() < items.size()) {
                DropDownItemWidget itemWidget = new DropDownItemWidget();
                this.scroller.addContainerWidget(itemWidget);
            }
            while (container.getWidgetCount() > items.size()) {
                container.getWidget(container.getWidgetCount() - 1).removeWidget();
            }

            int itemHeight = Math.max(1, MapWidgetIconDropDown.this.getHeight());
            for (int i = 0; i < items.size(); i++) {
                DropDownItemWidget itemWidget = getItemWidget(i);
                itemWidget.setBounds(0, i * itemHeight, Math.max(1, this.getWidth()), itemHeight);
                itemWidget.setItem(i, items.get(i));
            }

            this.recalculateContainerSize();
            this.updateBounds();
            this.invalidate();
        }

        DropDownItemWidget getItemWidget(int itemIndex) {
            MapWidget container = this.scroller.getContainer();
            if (itemIndex < 0 || itemIndex >= container.getWidgetCount()) {
                return null;
            }
            return castItemWidget(container.getWidget(itemIndex));
        }

        void clearIconCache() {
            MapWidget container = this.scroller.getContainer();
            for (int i = 0; i < container.getWidgetCount(); i++) {
                castItemWidget(container.getWidget(i)).clearCachedIcon();
            }
        }

        void recalculateContainerSize() {
            this.scroller.recalculateContainerSize();
        }

        void updateScrollForSelection() {
            if (selectedIndex < 0 || selectedIndex >= this.scroller.getContainer().getWidgetCount()) {
                this.scroller.getContainer().setPosition(0, 0);
                return;
            }

            int itemHeight = Math.max(1, MapWidgetIconDropDown.this.getHeight());
            int listHeight = this.scroller.getHeight();
            int safeBoundary = Math.max(1, Math.min(itemHeight / 2, Math.max(0, (listHeight - itemHeight) / 2)));
            int itemTop = selectedIndex * itemHeight;
            int itemBottom = itemTop + itemHeight;
            int currentScroll = -this.scroller.getContainer().getY();
            int nextScroll = currentScroll;
            int visibleTop = currentScroll + safeBoundary;
            int visibleBottom = currentScroll + listHeight - safeBoundary;

            if (itemTop < visibleTop) {
                nextScroll = itemTop - safeBoundary;
            } else if (itemBottom > visibleBottom) {
                nextScroll = itemBottom - listHeight + safeBoundary;
            }

            int maxScroll = Math.max(0, this.scroller.getContainer().getHeight() - listHeight);
            if (nextScroll < 0) {
                nextScroll = 0;
            } else if (nextScroll > maxScroll) {
                nextScroll = maxScroll;
            }

            this.scroller.getContainer().setPosition(0, -nextScroll);
        }

        private DropDownItemWidget castItemWidget(MapWidget widget) {
            return (DropDownItemWidget) widget;
        }

        @Override
        public void onDraw() {
            this.view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        }
    }

    private class DropDownItemWidget extends MapWidget {
        private int itemIndex = -1;
        private String item = null;
        private MapTexture icon;

        DropDownItemWidget() {
            this.setClipParent(true);
        }

        void setItem(int itemIndex, String item) {
            this.itemIndex = itemIndex;
            if (!Objects.equals(this.item, item)) {
                this.item = item;
                this.icon = null;
                this.invalidate();
            }
        }

        MapTexture getCachedIcon() {
            if (this.icon == null && this.itemIndex >= 0 && this.item != null) {
                this.icon = createItemIcon(this.itemIndex, this.item);
            }
            return this.icon;
        }

        void clearCachedIcon() {
            this.icon = null;
        }

        @Override
        public void onDraw() {
            if (itemIndex < 0 || this.item == null) {
                return;
            }

            boolean selected = (itemIndex == selectedIndex);
            drawDropDownItem(this.view, this.item, itemIndex, selected, selected);
        }
    }
}
