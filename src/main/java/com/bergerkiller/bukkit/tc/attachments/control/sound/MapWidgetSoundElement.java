package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * An element on the sound appearance menu page. Doesn't do anything
 * except drawing a button-like background and making the element
 * focusable.
 */
public class MapWidgetSoundElement extends MapWidget {

    public MapWidgetSoundElement() {
        setFocusable(true);
    }

    @Override
    public void onDraw() {
        // Draw the background of this widget, with different colors if selected
        {
            byte edgeColor, innerColorTop, innerColorBottom;
            if (isActivated()) {
                edgeColor = MapColorPalette.COLOR_BLACK;
                innerColorTop = MapColorPalette.getColor(36, 89, 152);
                innerColorBottom = MapColorPalette.getColor(44, 109, 186);
            } else if (isFocused()) {
                edgeColor = MapColorPalette.getColor(25, 25, 25);
                innerColorTop = MapColorPalette.getColor(78, 185, 180);
                innerColorBottom = MapColorPalette.getColor(100, 151, 213);
            } else {
                edgeColor = MapColorPalette.COLOR_BLACK;
                innerColorTop = MapColorPalette.getColor(44, 109, 186);
                innerColorBottom = MapColorPalette.getColor(36, 89, 152);
            }

            drawBackground(edgeColor, innerColorTop, innerColorBottom);
        }
    }

    protected void drawBackground(byte edgeColor, byte innerColorTop, byte innerColorBottom) {
        view.fillRectangle(2, 2, getWidth() - 3, getHeight() - 3, innerColorBottom);
        view.drawPixel(2, 2, innerColorTop);
        view.drawLine(2, 1, getWidth() - 3, 1, innerColorTop);
        view.drawLine(1, 2, 1, getHeight() - 3, innerColorTop);
        view.drawLine(1, 0, getWidth()-2, 0, edgeColor);
        view.drawLine(1, getHeight()-1, getWidth()-2, getHeight()-1, edgeColor);
        view.drawLine(0, 1, 0, getHeight() - 2, edgeColor);
        view.drawLine(getWidth() - 1, 1, getWidth() - 1, getHeight() - 2, edgeColor);
        view.drawPixel(1, 1, edgeColor);
        view.drawPixel(1, getHeight() - 2, edgeColor);
        view.drawPixel(getWidth() - 2, getHeight() - 2, edgeColor);
        view.drawPixel(getWidth() - 2, 1, edgeColor);
    }
}
