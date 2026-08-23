package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.animation.Easing;

/**
 * Displays a cubic bezier graph editor.
 * Height and width must be the same value.
 */
public class MapWidgetCubicBezier extends MapWidget {

    private final MapWidgetControlPoint controlPoint1;
    private final MapWidgetControlPoint controlPoint2;
    private final MapWidget focusEditTooltip = new MapWidget() {
        @Override
        public void onDraw() {
            // Draw background
            view.fill(MapColorPalette.COLOR_BLACK);

            // Draw text in the middle
            view.setAlignment(MapFont.Alignment.MIDDLE);
            view.draw(MapFont.MINECRAFT, getWidth()/2, 1, MapColorPalette.COLOR_WHITE,
                    "Enter [space] to edit");
        }
    };

    /**
     * Create a new cubic bezier editor widget.
     * @param controlPoint1 First control point
     * @param controlPoint2 Second control point
     */
    public MapWidgetCubicBezier(MapWidgetControlPoint controlPoint1, MapWidgetControlPoint controlPoint2) {
        this.controlPoint1 = this.addWidget(controlPoint1);
        this.controlPoint2 = this.addWidget(controlPoint2);

        this.focusEditTooltip.setDepthOffset(2);
        this.focusEditTooltip.setBounds(1, 28, 106, 10);
    }

    /**
     * Sets the bounds for this widget. Height should always be the same as the width.
     * @param x - position relative to the parent
     * @param y - position relative to the parent
     * @param widthHeight width and height
     */
    public void setBounds(int x, int y, int widthHeight) {
        setBounds(x, y, widthHeight, widthHeight);
    }

    public MapWidgetControlPoint getControlPoint1() {
        return controlPoint1;
    }

    public MapWidgetControlPoint getControlPoint2() {
        return controlPoint2;
    }

    public int getInnerWidth() {
        return getWidth() - 2;
    }

    public int getInnerHeight() {
        return getHeight() - 2;
    }

    @Override
    public void onAttached() {
        setFocusable(true);
    }

    @Override
    public void onDraw() {
        if (getHeight() != getWidth()) { // Width and height must be identical
            setBounds(getX(), getY(), getWidth());
        }

        byte frameColor = this.isFocused() ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_BLACK;

        this.view.fill(MapColorPalette.getColor(96, 96, 96));

        this.view.drawLine(1, getHeight() - 2, getControlPoint1().getX() + 1, getControlPoint1().getY() + 1, MapColorPalette.getColor(72, 108, 152));
        this.view.drawLine(getWidth() - 2, 1, getControlPoint2().getX() + 1, getControlPoint2().getY() + 1, MapColorPalette.getColor(108, 36, 36));

        int prevX = 1;
        int prevY = getInnerHeight();
        int samples = getInnerWidth();

        for (int i = 1; i < samples; i++) {
            float t = i / (float) samples;

            int x = 1 + Math.round(
                    Easing.EasingType.cubicBezier(
                            0.0f,
                            getControlPoint1().x(),
                            getControlPoint2().x(),
                            1.0f,
                            t
                    ) * (getInnerWidth() - 1));

            int y = 1 + Math.round(
                    (1.0f - // invert y so it displays correctly
                            Easing.EasingType.cubicBezier(
                            0.0f,
                            getControlPoint1().y(),
                            getControlPoint2().y(),
                            1.0f,
                            t)
                    ) * (getInnerHeight() - 1));

            x = Math.max(1, Math.min(getWidth() - 2, x));
            y = Math.max(1, Math.min(getWidth() - 2, y));

            view.drawLine(prevX, prevY, x, y, MapColorPalette.COLOR_WHITE);

            prevX = x;
            prevY = y;
        }

        this.view.drawPixel(1, getHeight() - 2, MapColorPalette.COLOR_WHITE);
        this.view.drawPixel(getWidth() - 2, 1, MapColorPalette.COLOR_WHITE);
        this.view.drawRectangle(0, 0, getWidth(), getHeight(), frameColor);
    }

    @Override
    public void onFocus() {
        this.focusEditTooltip.removeWidget();
        this.addWidget(this.focusEditTooltip);
    }

    @Override
    public void onBlur() {
        this.focusEditTooltip.removeWidget();
    }

    public static class MapWidgetControlPoint extends MapWidget {

        private byte color = MapColorPalette.COLOR_RED;
        private int blinkCtr = 0;
        private boolean blinkMode = false;

        /**
         * Called when the position changed, isn't called on {@link #setInitialPoint(float, float)}.
         */
        public void onValueChanged() {}

        /**
         * @return x-coordinate of this control point, normalized to the range [0.0, 1.0]
         */
        public float x() {
            return (getX() + 1.0f) / getBezierParent().getInnerWidth();
        }

        /**
         * @return y-coordinate of this control point, normalized to the range [0.0, 1.0]
         */
        public float y() {
            return 1.0f - (getY() + 1.0f) / getBezierParent().getInnerHeight();
        }

        public void setColor(byte color) {
            this.color = color;
            invalidate();
        }

        /**
         * Sets new coordinates. The coordinates are normalized to the range [0.0, 1.0] and start from the left-bottom corner.
         * @param x X coordinate
         * @param y Y coordinate
         */
        public void setPoint(float x, float y) {
            setPosition(
                    Math.round(x * getBezierParent().getInnerWidth()) - 1,
                    Math.round((1.0f - y) * getBezierParent().getInnerHeight()) - 1
            );
            getBezierParent().invalidate();
            onValueChanged();
        }

        /**
         * The same as {@link #setPoint(float, float)}, but doesn't call {@link #onValueChanged()}.
         * @param x X coordinate
         * @param y Y coordinate
         */
        public void setInitialPoint(float x, float y) {
            setPosition(
                    Math.round(x * getBezierParent().getInnerWidth()) - 1,
                    Math.round((1.0f - y) * getBezierParent().getInnerHeight()) - 1
            );
            getBezierParent().invalidate();
        }

        public MapWidgetCubicBezier getBezierParent() {
            return (MapWidgetCubicBezier) getParent();
        }

        @Override
        public void onAttached() {
            if (!(getParent() instanceof MapWidgetCubicBezier)) {
                throw new IllegalStateException("MapWidgetControlPoint must only be a child of MapWidgetCubicBezier");
            }

            setFocusable(true);
            setBounds(getX(), getY(), 3, 3);
        }

        @Override
        public void onDraw() {
            byte drawColor = color;

            if (isActivated()) {
                if (color == MapColorPalette.COLOR_RED) {
                    drawColor = MapColorPalette.getColor(255, 128, 128);
                } else if (color == MapColorPalette.COLOR_BLUE) {
                    drawColor = MapColorPalette.getColor(128, 192, 255);
                } else {
                    drawColor = MapColorPalette.getColor(255, 255, 255);
                }
            }

            if (blinkMode) {
                this.view.drawPixel(1, 1, drawColor);
                return;
            }

            this.view.drawLine(1, 0, 1, 2, drawColor);
            this.view.drawLine(0, 1, 2, 1, drawColor);
        }

        @Override
        public void onKey(MapKeyEvent event) {
            if (!isActivated()) {
                return;
            }

            int x = Math.max(1, Math.min(getBezierParent().getWidth() - 1 - getWidth(), getX() + event.getKey().dx()));
            int y = Math.max(1, Math.min(getBezierParent().getHeight() - 1 - getHeight(), getY() + event.getKey().dy()));

            if (getX() == x && getY() == y) {
                return;
            }

            setPosition(x, y);
            getBezierParent().invalidate();
            onValueChanged();
        }

        @Override
        public void onTick() {
            if (this.isFocused() && !this.isActivated()) {
                if (blinkCtr-- == 0) {
                    blinkCtr = 5;
                    setBlink(!this.blinkMode);
                }
            } else {
                setBlink(false);
                blinkCtr = 0;
            }
        }

        private void setBlink(boolean mode) {
            if (this.blinkMode != mode) {
                this.blinkMode = mode;
                this.invalidate();
            }
        }
    }

}
