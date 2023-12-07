package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;

/**
 * Shows a list of sequencer types that are registered
 */
public abstract class MapWidgetSequencerTypeSelector extends MapWidgetMenu {
    private static final byte ITEM_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte ITEM_BG_FOCUS = MapColorPalette.getColor(255, 252, 245);
    private static final int ROW_HEIGHT = 11;

    public MapWidgetSequencerTypeSelector() {
        this.setPositionAbsolute(true);
        this.setBounds(10, 20, 108, 98);
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.labelColor = MapColorPalette.COLOR_BLACK;
    }

    public abstract void onSelected(SequencerType type);

    @Override
    public void onAttached() {
        addLabel(5, 5, "Set Sequencer to add");

        MapWidgetScroller scroller = this.addWidget(new MapWidgetScroller());
        scroller.setScrollPadding(10)
                .setBounds(5, 12, getWidth() - 10, getHeight() - 17);

        int y = 0;
        for (SequencerType type : SequencerType.all()) {
            Item item = new Item(type);
            item.setBounds(0, y, scroller.getWidth(), ROW_HEIGHT);
            scroller.addContainerWidget(item);
            y += ROW_HEIGHT - 1;
        }

        super.onAttached();
    }

    private class Item extends MapWidget {
        private final SequencerType type;

        public Item(SequencerType type) {
            this.type = type;
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected(type);
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    type.name());
        }
    }
}
