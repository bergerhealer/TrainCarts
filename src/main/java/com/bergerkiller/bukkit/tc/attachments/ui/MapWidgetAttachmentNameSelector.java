package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shows a list of attachment names that exist. Also has one
 * option to set one by name, which can be used to set one that
 * doesn't exist yet. (add attachment after adding it here)
 * Optionally, an option can be included to allow for de-selecting
 * a name (set to 'none') with a custom text.
 */
public abstract class MapWidgetAttachmentNameSelector extends MapWidgetMenu {
    private static final byte ITEM_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte ITEM_BG_FOCUS = MapColorPalette.getColor(255, 252, 245);
    private static final int ROW_HEIGHT = 11;
    private final List<String> attachmentNames;
    private String title = "Set Attachment Name";
    private String noneItemText = null; // If non-null, include

    public MapWidgetAttachmentNameSelector(List<String> attachmentNames) {
        this.attachmentNames = attachmentNames;
        this.setPositionAbsolute(true);
        this.setBounds(10, 20, 108, 98);
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.labelColor = MapColorPalette.COLOR_BLACK;
    }

    public abstract void onSelected(String attachmentName);

    public MapWidgetAttachmentNameSelector includeNone(String text) {
        noneItemText = text;
        return this;
    }

    public MapWidgetAttachmentNameSelector setTitle(String title) {
        this.title = title;
        return this;
    }

    @Override
    public void onAttached() {
        addLabel(5, 5, title);

        MapWidgetScroller scroller = this.addWidget(new MapWidgetScroller());
        scroller.setScrollPadding(10)
                .setBounds(5, 12, getWidth() - 10, getHeight() - 17);

        List<MapWidget> items = attachmentNames.stream()
                .sorted().distinct().map(NameItem::new)
                .collect(Collectors.toCollection(ArrayList::new));
        items.add(new SelectNameItem());
        if (noneItemText != null) {
            items.add(new NoneNameItem());
        }

        int y = 0;
        for (MapWidget item : items) {
            item.setBounds(0, y, scroller.getWidth(), ROW_HEIGHT);
            scroller.addContainerWidget(item);
            y += ROW_HEIGHT - 1;
        }

        super.onAttached();
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
            onSelected(name);
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
            MapWidget parent = MapWidgetAttachmentNameSelector.this.getParent();
            close();

            parent.addWidget(new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    setDescription("Set Name");
                    activate();
                }

                @Override
                public void onAccept(String text) {
                    onSelected(text.trim());
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


    private class NoneNameItem extends MapWidget {

        public NoneNameItem() {
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected("");
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    noneItemText);
        }
    }
}
