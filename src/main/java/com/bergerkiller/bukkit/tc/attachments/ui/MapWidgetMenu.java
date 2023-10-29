package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.common.resources.SoundEffect;

/**
 * Abstract base class for a menu window that is closed when deactivated
 */
public class MapWidgetMenu extends MapWidgetWindow {
    protected MapWidgetAttachmentNode attachment;
    protected byte labelColor = MapColorPalette.COLOR_GREEN;
    protected boolean playSoundWhenBackClosed = false;
    protected boolean exitOnBack = true;

    public MapWidgetMenu() {
        this.setDepthOffset(4);
        this.setFocusable(true);
    }

    public void setAttachment(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
    }

    /**
     * Sets whether this menu dialog is closed when the user presses the back button, and this widget
     * is activated
     *
     * @param exitOnBack True to exit when BACK is pressed
     */
    public void setExitOnBack(boolean exitOnBack) {
        this.exitOnBack = exitOnBack;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.activate();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (exitOnBack && event.getKey() == Key.BACK && this.isActivated()) {
            if (playSoundWhenBackClosed) {
                display.playSound(SoundEffect.CLICK, 1.0f, 0.6f);
            }
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
