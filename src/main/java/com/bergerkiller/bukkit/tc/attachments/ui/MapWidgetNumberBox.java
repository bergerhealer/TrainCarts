package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Allows modifying a double number using left/right OR up/down (vertical) buttons
 */
public class MapWidgetNumberBox extends MapWidget implements SetValueTarget {
    private double _value = 0.0;
    private double _min = Double.NEGATIVE_INFINITY;
    private double _max = Double.POSITIVE_INFINITY;
    private double _incr = 0.01;
    private int _changeRepeat = 0;
    private boolean _vertical = false;
    private boolean _alwaysFocused = false;
    private String _textOverride = null;
    private final MapWidgetArrow nav_decr = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_incr = new MapWidgetArrow(BlockFace.EAST);

    public MapWidgetNumberBox() {
        this.setFocusable(true);
    }

    public MapWidgetNumberBox setVertical(boolean vertical) {
        if (this._vertical != vertical) {
            this._vertical = vertical;
            this.nav_decr.setDirection(vertical ? BlockFace.NORTH : BlockFace.WEST);
            this.nav_incr.setDirection(vertical ? BlockFace.SOUTH : BlockFace.EAST);
            this.onBoundsChanged();
        }
        return this;
    }

    public void setTextOverride(String text) {
        if (!LogicUtil.bothNullOrEqual(this._textOverride, text)) {
            this._textOverride = text;
            this.invalidate();
        }
    }

    public void setAlwaysFocused(boolean always) {
        if (this._alwaysFocused != always) {
            this._alwaysFocused = always;
            this.invalidate();
            if (always) {
                nav_incr.setVisible(true);
                nav_decr.setVisible(true);
            } else {
                nav_incr.setVisible(this.isFocused());
                nav_decr.setVisible(this.isFocused());
            }
        }
    }

    public void setIncrement(double increment) {
        this._incr = increment;
    }

    public void setRange(double min, double max) {
        this._min = min;
        this._max = max;
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Numeric Value";
    }

    @Override
    public boolean acceptTextValue(String value) {
        return acceptTextValue(Operation.SET, value);
    }

    @Override
    public boolean acceptTextValue(Operation operation, String value) {
        if (operation.perform(this::getValue, this::setValue, value)) {
            this.onValueChangeEnd();
            return true;
        } else {
            return false;
        }
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

    public int getChangeRepeat() {
        return this._changeRepeat;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        nav_decr.setVisible(this._alwaysFocused);
        nav_incr.setVisible(this._alwaysFocused);
        nav_decr.setClipParent(this.isClipParent());
        nav_incr.setClipParent(this.isClipParent());
        this.addWidget(nav_decr);
        this.addWidget(nav_incr);
    }

    @Override
    public void onBoundsChanged() {
        if (this._vertical) {
            int x_offset = (this.getWidth()-nav_decr.getWidth())>>1;
            nav_decr.setPosition(x_offset, this.getHeight() - nav_incr.getHeight());
            nav_incr.setPosition(x_offset, 0);
        } else {
            int y_offset = (this.getHeight()-nav_decr.getHeight())>>1;
            nav_decr.setPosition(0, y_offset);
            nav_incr.setPosition(this.getWidth() - nav_incr.getWidth(), y_offset);
        }
    }

    // 1 -> 2 -> 5 -> 10 -> 20 -> 50 -> 100 etc.
    private static double getExp(int repeat) {
        int a = (repeat / 3);
        int b = (repeat % 3);
        double f = (b==0) ? 1.0 : (b==1) ? 2.0 : 5.0;
        return f * Math.pow(10.0, a);
    }

    /**
     * Scales the increase of a value by a repeat counter. This makes the adjustment speed
     * up the longer a button is pressed.
     *
     * @param value Input value
     * @param incr Amount to increase the value by
     * @param repeat Amount of ticks this increase has been ongoing
     * @return Updated value
     */
    public static double scaledIncrease(double value, double incr, int repeat) {
        incr *= getExp(repeat / 50);

        // Only keep precision of increment
        value = incr * Math.round(value / incr);

        return value + incr;
    }

    private void addValue(double incr, int repeat) {
        setValue(scaledIncrease(getValue(), incr, repeat));
    }

    public void updateArrowFocus(boolean decreasing, boolean increasing) {
        if (decreasing) {
            nav_decr.sendFocus();
        } else {
            nav_decr.stopFocus();
        }
        if (increasing) {
            nav_incr.sendFocus();
        } else {
            nav_incr.stopFocus();
        }
    }

    public void stopArrowFocus(boolean increasing) {
        if (increasing) {
            nav_incr.stopFocus();
        } else {
            nav_decr.stopFocus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        this._changeRepeat = event.getRepeat();
        if (this._vertical) {
            // Up / Down
            if (event.getKey() == MapPlayerInput.Key.DOWN) {
                updateArrowFocus(true, false);
                this.addValue(-this._incr, event.getRepeat());
            } else if (event.getKey() == MapPlayerInput.Key.UP) {
                updateArrowFocus(false, true);
                this.addValue(this._incr, event.getRepeat());
            } else {
                super.onKeyPressed(event);
            }
        } else {
            // Left / Right
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                updateArrowFocus(true, false);
                this.addValue(-this._incr, event.getRepeat());
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                updateArrowFocus(false, true);
                this.addValue(this._incr, event.getRepeat());
            } else {
                super.onKeyPressed(event);
            }
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (this._vertical) {
            // Up / Down
            if (event.getKey() == MapPlayerInput.Key.DOWN) {
                stopArrowFocus(false);
                onValueChangeEnd();
            } else if (event.getKey() == MapPlayerInput.Key.UP) {
                stopArrowFocus(true);
                onValueChangeEnd();
            }
        } else {
            // Left / Right
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                stopArrowFocus(false);
                onValueChangeEnd();
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                stopArrowFocus(true);
                nav_incr.stopFocus();
            }
        }
    }

    @Override
    public void onFocus() {
        nav_decr.setVisible(true);
        nav_incr.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_decr.setVisible(this._alwaysFocused);
        nav_incr.setVisible(this._alwaysFocused);
    }

    /**
     * Gets the text displayed in this number box, if no override is set
     * 
     * @return value text
     */
    public String getValueText() {
        return Util.stringifyNumberBoxValue(getValue());
    }

    @Override
    public void onDraw() {
        onDraw(this.isFocused());
    }

    protected void onDraw(boolean focused) {
        String text;
        if (this._textOverride != null) {
            text = this._textOverride;
        } else {
            text = getValueText();
        }

        if (this._vertical) {
            int offset = nav_decr.getHeight() + 1;

            MapWidgetButton.fillBackground(this.view.getView(1, offset + 1, getWidth() - 2, getHeight() - 2 * offset - 2), this.isEnabled(), focused);
            this.view.drawRectangle(0, offset, getWidth(), getHeight() - 2 * offset, focused ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

            this.view.setAlignment(Alignment.MIDDLE);
            this.view.draw(MapFont.MINECRAFT, getWidth() / 2, (getHeight()-7) / 2, MapColorPalette.COLOR_WHITE, text);
        } else {
            int offset = nav_decr.getWidth() + 1;

            MapWidgetButton.fillBackground(this.view.getView(offset + 1, 1, getWidth() - 2 * offset - 2, getHeight() - 2), this.isEnabled(), focused);
            this.view.drawRectangle(offset, 0, getWidth() - 2 * offset, getHeight(), focused ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

            this.view.setAlignment(Alignment.MIDDLE);
            this.view.draw(MapFont.MINECRAFT, getWidth() / 2, (getHeight()-7) / 2, MapColorPalette.COLOR_WHITE, text);
        }
    }

    @Override
    public void onActivate() {
        this.setValue(0.0);
        this.onValueChangeEnd();
    }

    public void onValueChanged() {
    }

    /**
     * Fired when the player stops changing the value.
     * Does not fire when changing value directly, or when loading.
     */
    public void onValueChangeEnd() {
    }
}
