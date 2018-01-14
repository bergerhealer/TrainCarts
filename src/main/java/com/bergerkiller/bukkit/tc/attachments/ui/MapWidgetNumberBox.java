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
    private double _min = Double.NEGATIVE_INFINITY;
    private double _max = Double.POSITIVE_INFINITY;
    private double _incr = 0.01;
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);

    public MapWidgetNumberBox() {
        this.setFocusable(true);
    }

    public void setIncrement(double increment) {
        this._incr = increment;
    }

    public void setRange(double min, double max) {
        this._min = min;
        this._max = max;
    }

    public void setValue(double value) {
        if (value != this._value) {
            this._value = value;
            if (this._value < this._min) {
                this._value = this._min;
            } else if (this._value > this._max) {
                this._value = this._max;
            }
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
            this.addValue(-this._incr, event.getRepeat());
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.sendFocus();
            this.addValue(this._incr, event.getRepeat());
        } else {
            super.onKeyPressed(event);
        }
    }

    // 1 -> 2 -> 5 -> 10 -> 20 -> 50 -> 100 etc.
    private static double getExp(int repeat) {
        int a = (repeat / 3);
        int b = (repeat % 3);
        double f = (b==0) ? 1.0 : (b==1) ? 2.0 : 5.0;
        return f * Math.pow(10.0, a);
    }

    private void addValue(double incr, int repeat) {
        incr *= getExp(repeat / 50);
        double value = this.getValue();

        // Only keep precision of increment
        value = incr * Math.round(value / incr);

        // Increment and set
        this.setValue(value + incr);
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
