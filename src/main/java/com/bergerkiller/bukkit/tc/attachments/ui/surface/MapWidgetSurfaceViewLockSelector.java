package com.bergerkiller.bukkit.tc.attachments.ui.surface;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;

/**
 * Toggles view locking on or off. View locking controls whether the player view rotates when
 * the surface the player is standing on rotates. (only in simulated mode)
 */
public abstract class MapWidgetSurfaceViewLockSelector extends MapWidgetBlinkyButton {
    private static final int WIDTH = 13;
    private static final int HEIGHT = 13;

    private boolean viewLocked = true;
    private final MapTexture viewLockIcons =  MapTexture.loadResource(MapWidgetSurfacePlatformModeSelector.class,
                    "/com/bergerkiller/bukkit/tc/textures/surface/surface_menu_icons.png")
            .getView(WIDTH, 0, WIDTH, 2 * HEIGHT)
            .clone();

    /**
     * Called when {@link #isViewLocked()} changes
     */
    public abstract void onViewLockedChanged();

    public MapWidgetSurfaceViewLockSelector() {
        updateIconAndText();
        this.setDepthOffset(2); // Ensure above preview screen
    }

    @Override
    public void onClick() {
        setViewLocked(!isViewLocked());
    }

    public boolean isViewLocked() {
        return viewLocked;
    }

    public MapWidgetSurfaceViewLockSelector setViewLocked(boolean locked) {
        if (this.viewLocked != locked) {
            this.viewLocked = locked;
            updateIconAndText();
            onViewLockedChanged();
        }
        return this;
    }

    private void updateIconAndText() {
        setIcon(viewLockIcons.getView(0, viewLocked ? HEIGHT : 0, WIDTH, HEIGHT).clone());
        setTooltip(viewLocked ? "Player View Lock:\nEnabled" : "Player View Lock:\nDisabled");
    }
}
