package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shows a list of attachment names that exist. Also has one
 * option to set one by name, which can be used to set one that
 * doesn't exist yet. (add attachment after adding it here)
 * Optionally, an option can be included to allow for de-selecting
 * a name (set to 'none') with a custom text. At the top right the
 * player can change in what way attachments are selected (search strategy).
 * By default this is for the entire cart.
 */
public abstract class MapWidgetAttachmentSelector<T> extends MapWidgetMenu {
    private static final byte ITEM_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte ITEM_BG_FOCUS = MapColorPalette.getColor(255, 252, 245);
    private static final int ROW_HEIGHT = 11;
    private final boolean allowNone;
    private AttachmentSelector<T> allSelector;
    private MapWidgetScroller scroller;
    private String title = "Set Attachment Name";
    private String anyItemText = null; // If non-null, include

    public MapWidgetAttachmentSelector(AttachmentSelector<T> selector) {
        this(selector, false);
    }

    public MapWidgetAttachmentSelector(AttachmentSelector<T> selector, boolean allowNone) {
        this.setPositionAbsolute(true);
        this.setBounds(10, 20, 108, 98);
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.labelColor = MapColorPalette.COLOR_BLACK;

        this.allowNone = allowNone;
        this.allSelector = selector.withSelectAll();
        if (!allowNone && this.allSelector.strategy() == AttachmentSelector.SearchStrategy.NONE) {
            this.allSelector = this.allSelector.withStrategy(AttachmentSelector.SearchStrategy.CHILDREN);
        }
    }

    /**
     * Gets the names of all attachments matching particular search strategy and type filter.
     * Can use live attachments, if available. In that case use
     * {@link AttachmentNameLookup.Supplier#getSelection(AttachmentSelector) getSelection(selector)}.<br>
     * <br>
     * For example:
     * <pre>
     *     return attachment.getSelection(allSelector).names();
     * </pre>
     *
     * @param allSelector Selector for selecting all attachment, matching the current
     *                  search strategy and type filter.
     * @return List of attachment names that are found with this search strategy
     */
    public abstract List<String> getAttachmentNames(AttachmentSelector<T> allSelector);

    /**
     * Callback called when a final selection is made by the Player
     *
     * @param selector Updated selector based on the option the Player chose. Will
     *                 have the same type filter as specified in the constructor.
     */
    public abstract void onSelected(AttachmentSelector<T> selector);

    public MapWidgetAttachmentSelector<T> includeAny(String text) {
        anyItemText = text;
        return this;
    }

    public MapWidgetAttachmentSelector<T> setTitle(String title) {
        this.title = title;
        return this;
    }

    @Override
    public void onAttached() {
        addLabel(5, 5, title);

        scroller = this.addWidget(new MapWidgetScroller());
        scroller.setScrollPadding(10)
                .setBounds(5, 12, getWidth() - 10, getHeight() - 17);

        loadItems();

        // Add last so it doesn't get initial focus
        this.addWidget(new SearchStrategyWidget())
                .setPosition(getWidth() - 16, 4);

        super.onAttached();
    }

    private void loadItems() {
        scroller.getContainer().clearWidgets();

        // Don't show any items (empty) when NONE (disabled)
        // Require player to focus the top-right selector button to fix this
        if (this.allSelector.strategy() == AttachmentSelector.SearchStrategy.NONE) {
            return;
        }

        List<MapWidget> items = getAttachmentNames(allSelector).stream()
                .sorted().distinct().map(NameItem::new)
                .collect(Collectors.toCollection(ArrayList::new));
        items.add(new SelectNameItem());
        if (anyItemText != null) {
            items.add(new AnyNameItem());
        }

        int y = 0;
        for (MapWidget item : items) {
            item.setBounds(0, y, scroller.getWidth(), ROW_HEIGHT);
            scroller.addContainerWidget(item);
            y += ROW_HEIGHT - 1;
        }
    }

    private AttachmentSelector.SearchStrategy getNextStrategy(AttachmentSelector.SearchStrategy current) {
        final AttachmentSelector.SearchStrategy[] strategies = AttachmentSelector.SearchStrategy.values();

        int index = current.ordinal();
        index = (index + 1) % strategies.length;
        AttachmentSelector.SearchStrategy next = strategies[index];
        if (!allowNone && next == AttachmentSelector.SearchStrategy.NONE) {
            index = (index + 1) % strategies.length;
            next = strategies[index];
        }
        return next;
    }

    private class SearchStrategyWidget extends MapWidget {
        private final MapWidgetTooltip tooltip = new MapWidgetTooltip();

        public SearchStrategyWidget() {
            this.setFocusable(true);
            this.setSize(11, 7);
            tooltip.setText(allSelector.strategy().getCaption());
        }

        @Override
        public void onAttached() {
            super.onAttached();
            if (allSelector.strategy() == AttachmentSelector.SearchStrategy.NONE) {
                focus(); // Avoid breakage
            }
        }

        @Override
        public void onActivate() {
            allSelector = allSelector.withStrategy(getNextStrategy(allSelector.strategy()));
            tooltip.setText(allSelector.strategy().getCaption());
            invalidate();
            loadItems();
            display.playSound(SoundEffect.CLICK_WOOD);
        }

        @Override
        public void onFocus() {
            addWidget(tooltip);
        }

        @Override
        public void onBlur() {
            removeWidget(tooltip);
        }

        @Override
        public void onDraw() {
            view.draw(allSelector.strategy().getIcon(isFocused()), 0, 0);
        }
    }

    private class NameItem extends MapWidget {
        private final String name;

        public NameItem(String name) {
            this.name = name;
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected(allSelector.withName(name));
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    name);
        }
    }

    private class SelectNameItem extends MapWidget {

        public SelectNameItem() {
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            MapWidget parent = MapWidgetAttachmentSelector.this.getParent();
            close();

            parent.addWidget(new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    setDescription("Set Name");
                    activate();
                }

                @Override
                public void onAccept(String text) {
                    onSelected(allSelector.withName(text.trim()));
                }
            });
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    "<Set Name>");
        }
    }

    private class AnyNameItem extends MapWidget {

        public AnyNameItem() {
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected(allSelector);
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    anyItemText);
        }
    }
}
