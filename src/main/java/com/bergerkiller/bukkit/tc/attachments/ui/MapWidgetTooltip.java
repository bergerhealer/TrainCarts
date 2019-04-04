package com.bergerkiller.bukkit.tc.attachments.ui;

import java.awt.Dimension;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * Displays text on top of other widgets while a widget is hovered over.
 * Tooltips are displayed above or below the parent widget.
 */
public class MapWidgetTooltip extends MapWidget {
    private String _text = null;

    public MapWidgetTooltip() {
        this.setDepthOffset(2);
    }

    public MapWidgetTooltip setText(String text) {
        if (!LogicUtil.bothNullOrEqual(this._text, text)) {
            this._text = text;
            this.invalidate();
            this.calcBounds();
        }
        return this;
    }

    public String getText() {
        return this._text;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.calcBounds();
    }

    @Override
    public void onDraw() {
        // Only draw something when text is set
        if (this._text == null) {
            return;
        }

        // Draw background
        view.fill(MapColorPalette.COLOR_BLACK);

        // Draw text in the middle
        view.setAlignment(MapFont.Alignment.MIDDLE);
        view.draw(MapFont.MINECRAFT, getWidth()/2, (getHeight()-7)/2, MapColorPalette.COLOR_WHITE, this._text);
    }

    private void calcBounds() {
        if (this.parent == null || this._text == null) {
            this.setBounds(0, 0, 0, 0);
            return;
        }

        Dimension textSize = this.view.calcFontSize(MapFont.MINECRAFT, this._text);
        int pos_x, pos_y;

        // Above or below the parent widget?
        int parent_x = parent.getAbsoluteX();
        int parent_y = parent.getAbsoluteY();
        if (textSize.getHeight() > (128 - (parent_y+parent.getHeight()))) {
            // Above
            pos_y = parent_y - (int) textSize.getHeight();
        } else {
            // Below
            pos_y = parent_y+parent.getHeight();
        }

        // Center it
        pos_x = (parent_x + parent.getWidth()/2) - ((int) textSize.getWidth() / 2);

        // Clamp x-coordinate to the left or right side of the window
        if (pos_x < 0) {
            pos_x = 0;
        } else if ((pos_x + (int) textSize.getWidth()) > 128) {
            pos_x = 128 - (int) textSize.getWidth();
        }

        // Make absolute position of tooltip relative to parent
        pos_x -= parent_x;
        pos_y -= parent_y;

        // Apply
        this.setBounds(pos_x, pos_y, (int) textSize.getWidth(), (int) textSize.getHeight());
    }
}
