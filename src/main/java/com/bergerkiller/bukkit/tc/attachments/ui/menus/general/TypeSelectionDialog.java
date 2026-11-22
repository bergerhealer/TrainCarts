package com.bergerkiller.bukkit.tc.attachments.ui.menus.general;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.CachedAttachmentIcon;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Popup dialog listing attachment types for quickly selecting a type.
 */
public class TypeSelectionDialog extends MapWidgetMenu {
    private static final int ROW_HEIGHT = 16;
    private final ConfigurationNode attachmentConfig;
    private final List<AttachmentType> types;
    private final IntConsumer onAcceptedIndex;
    private final int initialSelectedIndex;
    private final MapWidgetScroller scroller = new MapWidgetScroller();
    private int focusedTypeIndex = -1;

    public TypeSelectionDialog(
            int x, int y, int width, int height,
            ConfigurationNode attachmentConfig,
            List<AttachmentType> types,
            int initialSelectedIndex,
            IntConsumer onAcceptedIndex
    ) {
        this.attachmentConfig = attachmentConfig;
        this.types = new ArrayList<>(types);
        this.initialSelectedIndex = initialSelectedIndex;
        this.onAcceptedIndex = onAcceptedIndex;
        this.playSoundWhenBackClosed = true;
        this.setBounds(x, y, width, height);
        this.setBackgroundColor(MapColorPalette.getColor(164, 168, 184));
        this.setDepthOffset(10);
    }

    @Override
    public void onAttached() {
        scroller.setScrollPadding(ROW_HEIGHT / 2)
                .setBounds(4, 4, getWidth() - 8, getHeight() - 8);
        addWidget(scroller);

        int y = 0;
        for (int i = 0; i < types.size(); i++) {
            TypeSelectionItem item = scroller.addContainerWidget(new TypeSelectionItem(i, types.get(i)));
            item.setBounds(0, y, scroller.getWidth(), ROW_HEIGHT);
            y += ROW_HEIGHT;
        }

        if (initialSelectedIndex >= 0 && initialSelectedIndex < scroller.getContainer().getWidgetCount()) {
            MapWidget selectedItem = scroller.getContainer().getWidget(initialSelectedIndex);
            if (selectedItem != null) {
                selectedItem.focus();
            }
        }

        super.onAttached();
    }

    private void acceptSelection() {
        if (focusedTypeIndex >= 0 && focusedTypeIndex < types.size()) {
            onAcceptedIndex.accept(focusedTypeIndex);
            if (display != null) {
                display.playSound(SoundEffect.CLICK);
            }
        }
        close();
    }

    private class TypeSelectionItem extends MapWidget {
        private final int index;
        private final AttachmentType type;
        private final CachedAttachmentIcon icon;

        TypeSelectionItem(int index, AttachmentType type) {
            this.index = index;
            this.type = type;
            this.icon = new CachedAttachmentIcon(type, attachmentConfig);
            this.icon.getIcon(); // Preload expensive icons once
            this.setFocusable(true);
        }

        @Override
        public void onFocus() {
            focusedTypeIndex = index;
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                acceptSelection();
                return;
            }
            super.onKeyPressed(event);
        }

        @Override
        public void onActivate() {
            acceptSelection();
        }

        @Override
        public void onDraw() {
            MapTexture icon = this.icon.getIcon();
            int iconOffset = icon.getWidth();
            boolean selected = isFocused();
            byte outlineColor = selected ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK;
            MapWidgetButton.fillBackground(view.getView(1, 1, view.getWidth() - 2, view.getHeight() - 2), true, selected);
            view.draw(icon, 0, Math.max(0, (getHeight() - icon.getHeight()) / 2));
            view.drawRectangle(0, 0, view.getWidth(), view.getHeight(), outlineColor);
            view.drawLine(iconOffset - 1, 1, iconOffset - 1, view.getHeight() - 2, outlineColor);

            int textX = Math.max(0, iconOffset + 2);
            view.draw(MapFont.MINECRAFT, textX, Math.max(0, (view.getHeight() - 7) / 2),
                    selected ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_WHITE,
                    type.getName());
        }
    }
}
