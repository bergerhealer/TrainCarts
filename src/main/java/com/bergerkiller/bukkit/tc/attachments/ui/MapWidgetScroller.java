package com.bergerkiller.bukkit.tc.attachments.ui;

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
    private double scrollSpeed = 0.35;
    private int scrollPadding = 0;

    public MapWidgetScroller() {
        this.container.setClipParent(true);
        this.addWidget(this.container);

        // Keep container added, but container might lose its children
        this.setRetainChildWidgets(true);
    }

    /**
     * Sets the scroll speed. This is the ratio of the amount of pixels scrolled
     * compared to the requested scroll position
     *
     * @param speed Speed ratio. 1 is instant.
     * @return this scroller widget
     */
    public MapWidgetScroller setScrollSpeed(double speed) {
        this.scrollSpeed = speed;
        return this;
    }

    /**
     * Sets the amount of extra pixels around the focused widget which should be
     * made visible by scrolling.
     *
     * @param padding Pixel padding
     * @return this scroller widget
     */
    public MapWidgetScroller setScrollPadding(int padding) {
        this.scrollPadding = padding;
        return this;
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

        // Container dimensions
        int cMinX = container.getAbsoluteX();
        int cMinY = container.getAbsoluteY();
        int cMaxX = cMinX + container.getWidth();
        int cMaxY = cMinY + container.getHeight();

        // Compute the rectangle that should stay within the window
        // Include padding, but ignore padding beyond the bounds of the
        // scroll container.
        int fMinX = focused.getAbsoluteX();
        int fMinY = focused.getAbsoluteY();
        int fMaxX = fMinX + focused.getWidth();
        int fMaxY = fMinY + focused.getHeight();
        if (container.getWidth() > this.getWidth()) {
            fMinX -= scrollPadding;
            fMaxX += scrollPadding;
        }
        if (container.getHeight() > this.getHeight()) {
            fMinY -= scrollPadding;
            fMaxY += scrollPadding;
        }
        fMinX = Math.max(fMinX, cMinX);
        fMinY = Math.max(fMinY, cMinY);
        fMaxX = Math.min(fMaxX, cMaxX);
        fMaxY = Math.min(fMaxY, cMaxY);

        // Compute the four edges of the focused widget relative to this widget viewport
        int selfAbsX = this.getAbsoluteX();
        int selfAbsY = this.getAbsoluteY();
        int minEdgeX = selfAbsX - fMinX;
        int minEdgeY = selfAbsY - fMinY;
        int maxEdgeX = (this.getWidth() - (fMaxX - selfAbsX));
        int maxEdgeY = (this.getHeight() - (fMaxY - selfAbsY));

        // Compute how much the widget should be moved horizontally
        int dx = 0;
        if (minEdgeX > 0 && maxEdgeX < 0) {
            dx = minEdgeX + maxEdgeX;
            if (dx == 1 || dx == -1) {
                dx = 0;
            }
        } else if (minEdgeX >= 0) {
            dx = minEdgeX;
        } else if (maxEdgeX < 0) {
            dx = maxEdgeX;
        }

        // Compute how much the widget should be moved vertically
        int dy = 0;
        if (minEdgeY > 0 && maxEdgeY < 0) {
            dy = minEdgeY + maxEdgeY;
            if (dy == 1 || dy == -1) {
                dy = 0;
            }
        } else if (minEdgeY >= 0) {
            dy = minEdgeY;
        } else if (maxEdgeY < 0) {
            dy = maxEdgeY;
        }

        if (dx != 0 || dy != 0) {
            // Make scrolling smoother instead of instant
            dx = smoothenScrollDelta(dx);
            dy = smoothenScrollDelta(dy);

            container.setPosition(container.getX() + dx, container.getY() + dy);
            onScrolled();
        }
    }

    private int smoothenScrollDelta(int delta) {
        if (delta == 0) {
            return 0;
        }
        boolean negative = (delta < 0);
        delta = Math.abs(delta);
        delta = Math.max((int) (delta * scrollSpeed), 1);
        if (negative) {
            delta = -delta;
        }
        return delta;
    }

    @Override
    public void onTick() {
        scrollIntoView();
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
