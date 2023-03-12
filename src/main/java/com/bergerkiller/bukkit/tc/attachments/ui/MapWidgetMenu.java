package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;

/**
 * Abstract base class for a menu window that is closed when deactivated
 */
public class MapWidgetMenu extends MapWidgetWindow {
    protected MapWidgetAttachmentNode attachment;
    protected byte labelColor = MapColorPalette.COLOR_GREEN;

    public MapWidgetMenu() {
        this.setDepthOffset(4);
        this.setFocusable(true);
    }

    public void setAttachment(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.activate();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.BACK && this.isActivated()) {
            this.close();
            return;
        }
        super.onKeyPressed(event);
    }

    @Override
    public void onTick() {
        super.onTick();
        if (attachment != null && attachment.getAttachmentConfig().isRemoved()) {
            close();
        }
    }

    /**
     * Closes this menu, removing this window
     */
    public void close() {
        this.removeWidget();
    }

    /**
     * Adds a small text label in a place
     * 
     * @param x
     * @param y
     * @param text
     */
    public void addLabel(int x, int y, String text) {
        MapWidgetText label = new MapWidgetText();
        label.setFont(MapFont.TINY);
        label.setText(text);
        label.setPosition(x, y);
        label.setColor(MapColorPalette.getSpecular(this.labelColor, 0.5f));
        this.addWidget(label);
    }
}
