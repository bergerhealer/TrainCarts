package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;

/**
 * Abstract base class for a menu window that is closed when deactivated
 */
public class MapWidgetMenu extends MapWidgetWindow {

    public MapWidgetMenu() {
        this.setDepthOffset(4);
        this.setFocusable(true);
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

    /**
     * Closes this menu, removing this window
     */
    public void close() {
        this.removeWidget();
    }
}
