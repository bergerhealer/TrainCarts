package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * A widget that can contain child widgets, and can be scrolled up/down/left/right.
 * Scrolling is done by giving focus to the child widgets.<br>
 * <br>
 * Widgets should be added with positive x/y positions. Negative positions are
 * not supported. Widget size should be set up-front before calling
 * {@link #addContainerWidget(MapWidget)}<br>
 * <br>
 * The bounds of this scroller widget define the view port dimensions, where the
 * child widgets will be displayed inside.
 */
public class MapWidgetScroller extends MapWidget {
    private final MapWidget container = new MapWidget();
    private static final boolean isBoundsChangeViewBugged = !Common.hasCapability("Common:MapDisplay:BoundsChangeViewFix");

    public MapWidgetScroller() {
        this.container.setClipParent(true);
        this.addWidget(this.container);

        // Keep container added, but container might lose its children
        this.setRetainChildWidgets(true);
    }

    /**
     * Gets the container widget to which other widgets can be added. The container
     * grows automatically based on the dimensions of widgets added to it.
     *
     * @return container widget
     */
    public MapWidget getContainer() {
        return container;
    }

    /**
     * Adds a new widget to the {@link #getContainer() container} of this widget.
     * Automatically sets the widgets to clip it's view area and resizes the container
     * widget to fit the new widget.
     *
     * @param widget Widget to add to the container
     * @return input widget
     */
    public <T extends MapWidget> T addContainerWidget(T widget) {
        if (this.getDisplay() != null) {
            adjustContainerSize(widget);
        }
        setClipParentRecursive(widget);
        container.addWidget(widget);
        return widget;
    }

    /**
     * Gets the maximum horizontal scroll position.
     * Returns 0 if there is no horizontal scrolling.
     *
     * @return Maximum horizontal scroll position
     */
    public int getHScrollMaximum() {
        return Math.min(0, container.getWidth() - this.getWidth());
    }

    /**
     * Gets the current horizontal scroll position
     *
     * @return Horizontal scroll position
     */
    public int getHScroll() {
        return -container.getX();
    }

    /**
     * Gets the maximum vertical scroll position.
     * Returns 0 if there is no vertical scrolling.
     *
     * @return Maximum vertical scroll position
     */
    public int getVScrollMaximum() {
        return Math.min(0, container.getHeight() - this.getHeight());
    }

    /**
     * Gets the current vertical scroll position
     *
     * @return Vertical scroll position
     */
    public int getVScroll() {
        return -container.getY();
    }

    /**
     * If a child widget added to this widgets {@link #getContainer() container} is
     * focused, ensures that this widget is maximally visible. Scrolls the container
     * horizontally and vertically if needed.
     */
    public void scrollIntoView() {
        MapWidget focused = display.getFocusedWidget();
        if (focused == null || !isContained(focused)) {
            return;
        }

        int minX = focused.getAbsoluteX() - this.getAbsoluteX();
        int minY = focused.getAbsoluteY() - this.getAbsoluteY();
        int maxX = (minX + focused.getWidth()) - this.getWidth();
        int maxY = (minY + focused.getHeight()) - this.getHeight();

        int dx = 0;
        if (minX <= 0) {
            dx = -minX;
        } else if (maxX > 0) {
            dx = -maxX;
        }

        int dy = 0;
        if (minY <= 0) {
            dy = -minY;
        } else if (maxY > 0) {
            dy = -maxY;
        }

        if (dx != 0 || dy != 0) {
            // Required for older BKCL, where drawn contents get all streaked because the
            // view buffer isn't cleared before being moved.
            if (isBoundsChangeViewBugged) {
                clearViewRecursive(container);
            }

            container.setPosition(container.getX() + dx, container.getY() + dy);
            onScrolled();
        }
    }

    /**
     * Called when the scroll position changes horizontally or vertically
     */
    public void onScrolled() {
    }

    private boolean isContained(MapWidget w) {
        while (w != null && w != root) {
            if (w == container) {
                return true;
            }
            w = w.getParent();
        }
        return false;
    }

    private static void clearViewRecursive(MapWidget w) {
        w.clear();
        for (MapWidget c : w.getWidgets()) {
            clearViewRecursive(c);
        }
    }

    @Override
    public void onAttached() {
        container.getWidgets().forEach(this::adjustContainerSize);
    }

    @Override
    public void onDetached() {
        // Reset
        container.setBounds(0, 0, 0, 0);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        // Handle key like normal. Might change navigation.
        super.onKeyPressed(event);
        // Make sure all is visible
        this.scrollIntoView();
    }

    private void setClipParentRecursive(MapWidget w) {
        w.setClipParent(true);
        for (MapWidget c : w.getWidgets()) {
            setClipParentRecursive(c);
        }
    }

    private void adjustContainerSize(MapWidget child) {
        adjustContainerSize(child.getX() + child.getWidth(),
                            child.getY() + child.getHeight());
    }

    private void adjustContainerSize(int width, int height) {
        if (width > container.getWidth() || height > container.getHeight()) {
            container.setSize(Math.max(container.getWidth(), width),
                              Math.max(container.getHeight(), height));
        }
    }
}
