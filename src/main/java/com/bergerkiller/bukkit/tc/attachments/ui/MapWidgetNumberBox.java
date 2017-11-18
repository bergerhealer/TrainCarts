package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Allows modifying a double number using left/right buttons
 */
public class MapWidgetNumberBox extends MapWidget {
    private double _value = 0.0;
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);

    public MapWidgetNumberBox() {
        this.setFocusable(true);
    }
    
    public void setValue(double value) {
        if (value != this._value) {
            this._value = value;
            this.invalidate();
            this.onValueChanged();
        }
    }

    public double getValue() {
        return this._value;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        nav_left.setVisible(false);
        nav_right.setVisible(false);
        this.addWidget(nav_left);
        this.addWidget(nav_right);
    }

    @Override
    public void onBoundsChanged() {
        nav_left.setPosition(0, 0);
        nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 0);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            nav_left.sendFocus();
            this.setValue(getValue() - 0.1);
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.sendFocus();
            this.setValue(getValue() + 0.1);
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            nav_left.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.stopFocus();
        }
    }

    @Override
    public void onFocus() {
        nav_left.setVisible(true);
        nav_right.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_left.setVisible(false);
        nav_right.setVisible(false);
    }

    @Override
    public void onDraw() {
        int offset = nav_left.getWidth() + 1;

        this.view.drawRectangle(offset, 0, getWidth() - 2 * offset, getHeight(), this.isFocused() ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

        String text = Double.toString(MathUtil.round(getValue(), 4));
        this.view.setAlignment(Alignment.MIDDLE);
        this.view.draw(MapFont.MINECRAFT, getWidth() / 2, 2, MapColorPalette.COLOR_WHITE, text);
    }

    public void onValueChanged() {
    }
}
