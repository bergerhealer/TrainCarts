package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.resources.SoundEffect;

/**
 * A tab view widget that intercepts keyboard input (up/down) to switch between
 * tabs automatically. Allows for special navigating logic to occur when navigating
 * past the first or last items.
 */
public class MapWidgetVerticalNavigableList extends MapWidgetTabView {
    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (!shouldInterceptInput(event)) {
            super.onKeyPressed(event);
        } else if (event.getKey() == MapPlayerInput.Key.UP && this.getSelectedIndex() > 0) {
            display.playSound(SoundEffect.PISTON_EXTEND);
            this.setSelectedIndex(this.getSelectedIndex()-1);
            this.getSelectedTab().activate();
            this.onNavigated(event, this.getSelectedTab());
        } else if (event.getKey() == MapPlayerInput.Key.DOWN &&
                   this.getSelectedIndex() < (this.getTabCount()-1) &&
                   this.getTab(this.getSelectedIndex() + 1).getWidgetCount() > 0
        ) {
            display.playSound(SoundEffect.PISTON_EXTEND);
            this.setSelectedIndex(this.getSelectedIndex()+1);
            this.getSelectedTab().activate();
            this.onNavigated(event, this.getSelectedTab());
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            onLastItemDown(event);
        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            onFirstItemUp(event);
        } else {
            super.onKeyPressed(event);
        }
    }

    /**
     * Called when the selected tab is successfully navigated by the player
     *
     * @param event Key event
     * @param tab Current tab navigated to
     */
    public void onNavigated(MapKeyEvent event, Tab tab) {
    }

    /**
     * Called when pressing UP while the first item is selected. By default navigates within.
     *
     * @param event Key event
     */
    public void onFirstItemUp(MapKeyEvent event) {
        super.onKeyPressed(event);
    }

    /**
     * Called when pressing DOWN while the last item is selected. By default navigates within.
     *
     * @param event Key event
     */
    public void onLastItemDown(MapKeyEvent event) {
        super.onKeyPressed(event);
    }

    /**
     * Gets whether the input keypress should be intercepted or not
     *
     * @param event
     * @return True if intercepted and it should navigate the list
     */
    public boolean shouldInterceptInput(MapKeyEvent event) {
        return true;
    }
}
