package com.bergerkiller.bukkit.tc.controller.functions.ui;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

/**
 * Shows a dialog to allow the player to select a type of transfer function. When selected,
 * the selected transfer function is created and returned using {@link #onSelected(TransferFunction)}
 */
public abstract class MapWidgetTransferFunctionTypeSelectorDialog extends MapWidgetMenu {
    private static final byte ITEM_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    private static final byte ITEM_BG_FOCUS = MapColorPalette.getColor(255, 252, 245);
    private static final int ROW_HEIGHT = 11;
    private final TransferFunctionHost host;

    public MapWidgetTransferFunctionTypeSelectorDialog(TransferFunctionHost host) {
        this.host = host;
        setBounds(20, 25, 88, 88);
        setPositionAbsolute(true);
        setBackgroundColor(MapColorPalette.getColor(164, 168, 184));
    }

    public abstract void onSelected(TransferFunction function);

    @Override
    public void onAttached() {
        this.addWidget(new MapWidgetScroller() {
                    @Override
                    public void onAttached() {
                        int y = 0;
                        boolean addedInput = false;
                        for (TransferFunction.Serializer<?> serializer : host.getRegistry().all()) {
                            if (serializer.isListed(host)) {
                                // Only add the very first input transform function, listed as "input"
                                if (serializer.isInput()) {
                                    if (addedInput) {
                                        continue;
                                    } else {
                                        addedInput = true;
                                    }
                                }

                                addContainerWidget(new Item(serializer).setBounds(0, y, getWidth(), ROW_HEIGHT + 1));
                                y += ROW_HEIGHT;
                            }
                        }
                        super.onAttached();
                    }
                }).setScrollPadding(ROW_HEIGHT / 2)
                .setBounds(4, 4, getWidth() - 8, getHeight() - 8);

        super.onAttached();
    }

    private class Item extends MapWidget {
        private final TransferFunction.Serializer<?> serializer;

        public Item(TransferFunction.Serializer<?> serializer) {
            this.serializer = serializer;
            this.setFocusable(true);
        }

        @Override
        public void onActivate() {
            close();
            onSelected(serializer.createNew(host));
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? ITEM_BG_FOCUS : ITEM_BG_DEFAULT);
            view.draw(MapFont.MINECRAFT, 2, 2,
                    isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK,
                    serializer.isInput() ? "Input" : serializer.title());
        }
    }
}
