package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.mountiplex.MountiplexUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shows a list of effect attachments that exist. Also has one
 * option to set one by name, which can be used to set one that
 * doesn't exist yet. (add effect after adding it here)
 */
public abstract class MapWidgetSequencerEffectSelector extends MapWidgetMenu {
    private static final byte ITEM_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte ITEM_BG_FOCUS = MapColorPalette.getColor(255, 252, 245);
    private static final int ROW_HEIGHT = 11;
    private final List<String> effectNames;

    public MapWidgetSequencerEffectSelector(List<String> effectNames) {
        this.effectNames = effectNames;
        this.setPositionAbsolute(true);
        this.setBounds(10, 20, 108, 98);
        this.setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
        this.labelColor = MapColorPalette.COLOR_BLACK;
    }

    public abstract void onSelected(String effectName);

    @Override
    public void onAttached() {
        addLabel(5, 5, "Set Effect to play");

        MapWidgetScroller scroller = this.addWidget(new MapWidgetScroller());
        scroller.setScrollPadding(10)
                .setBounds(5, 12, getWidth() - 10, getHeight() - 17);

        List<MapWidget> items = Stream.<MapWidget>concat(
                    effectNames.stream().distinct().map(NameItem::new),
                    MountiplexUtil.toStream(new SelectNameItem()))
                .collect(Collectors.toList());
        int y = 0;
        for (MapWidget item : items) {
            item.setBounds(0, y, scroller.getWidth(), ROW_HEIGHT);
            scroller.addContainerWidget(item);
            y += ROW_HEIGHT - 1;
        }

        super.onAttached();
    }

    private class NameItem extends MapWidget {
        private final String effectName;

        public NameItem(String effectName) {
            this.effectName = effectName;
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected(effectName);
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    effectName);
        }
    }

    private class SelectNameItem extends MapWidget {

        public SelectNameItem() {
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            MapWidget parent = MapWidgetSequencerEffectSelector.this.getParent();
            close();

            parent.addWidget(new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    setDescription("Set Effect Attachment");
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
}
