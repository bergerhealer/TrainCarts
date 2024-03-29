package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.resources.SoundEffect;
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
    private int _holdEnterProgress = 0;
    private int _holdEnterMaximum = 15;
    private String _textOverride = null;
    private String _textPrefix = "";
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

    public void setTextPrefix(String textPrefix) {
        if (!LogicUtil.bothNullOrEqual(this._textPrefix, textPrefix)) {
            this._textPrefix = textPrefix;
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

    /**
     * Sets the initial value, without firing {@link #onValueChanged()} if it
     * differs from the current value (default of 0)
     *
     * @param value The initial value
     */
    public void setInitialValue(double value) {
        if (value != this._value) {
            updateValue(value);
        }
    }

    /**
     * Sets a new value. Will fire {@link #onValueChanged()} if the value changes.
     *
     * @param value The new value
     */
    public void setValue(double value) {
        if (value != this._value) {
            updateValue(value);
            this.onValueChanged();
        }
    }

    private void updateValue(double value) {
        this._value = value;
        if (this._value < this._min) {
            this._value = this._min;
        } else if (this._value > this._max) {
            this._value = this._max;
        }
        this.invalidate();
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

    /**
     * Called when the player holds down spacebar to reset the value.
     * Can be overrided to set a different value than 0.0.
     */
    public void onResetValue() {
        setValue(0.0);
    }

    /**
     * Called when the player holds down spacebar and then presses
     * one of the arrow keys. Can be used to perform a special transformation.
     * By default inverts the number (if possible according to range)
     *
     * @param key Key pressed while holding spacebar
     */
    public void onResetSpecial(MapPlayerInput.Key key) {
        double newValue = -this.getValue();
        if (newValue >= this._min && newValue <= this._max) {
            this.setValue(newValue);
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

    /**
     * Gets whether the user held the ENTER key for long enough for the value
     * to be reset (this widget is activated)
     *
     * @return True if reset
     */
    public boolean isHoldEnterResetComplete() {
        return this._holdEnterProgress == this._holdEnterMaximum;
    }

    @Override
    public void onKey(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            // Track amount of ticks ENTER was held
            if (this._holdEnterProgress <= this._holdEnterMaximum) {
                ++this._holdEnterProgress;
                if (this.isHoldEnterResetComplete()) {
                    this.activate();
                }
                this.invalidate();
            }
        } else {
            super.onKey(event);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        this._changeRepeat = event.getRepeat();
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            // Already handled in onKey
        } else if (this._holdEnterProgress > 0 && this._holdEnterProgress < this._holdEnterMaximum) {
            switch (event.getKey()) {
                case UP:
                case DOWN:
                case LEFT:
                case RIGHT:
                    this.onResetSpecial(event.getKey());
                    this._holdEnterProgress = this._holdEnterMaximum + 1;
                    this.invalidate();
                    this.onResetClickSound();
                    break;
                default:
                    super.onKeyPressed(event);
                    break;
            }
        } else if (this._vertical) {
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
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            this._holdEnterProgress = 0;
            this.invalidate();
        } else if (this._vertical) {
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
                onValueChangeEnd();
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
        String s;
        if (_incr == 1.0) {
            s = Integer.toString((int) getValue());
        } else {
            s = Util.stringifyNumberBoxValue(getValue());
        }
        if (!_textPrefix.isEmpty()) {
            s = _textPrefix + s;
        }
        return s;
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

        int holdEnterProgress = this._holdEnterProgress;
        if (holdEnterProgress > this._holdEnterMaximum) {
            holdEnterProgress = 0; // Completed
        }

        int offset;
        if (this._vertical) {
            offset = nav_decr.getHeight() + 1;

            MapWidgetButton.fillBackground(this.view.getView(1, offset + 1, getWidth() - 2, getHeight() - 2 * offset - 2), this.isEnabled(), focused);
            this.view.drawRectangle(0, offset, getWidth(), getHeight() - 2 * offset, focused ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

            // Animate two bars moving from outside to the middle to reset the number
            if (holdEnterProgress > 0) {
                int barHeight = (((getHeight() - 2 * offset - 4) * holdEnterProgress) / 2) / this._holdEnterMaximum;
                this.view.fillRectangle(2, 2 + offset, getWidth() - 4, barHeight, MapColorPalette.COLOR_RED);
                this.view.fillRectangle(2, getHeight() - offset - barHeight - 2, getWidth() - 4, barHeight, MapColorPalette.COLOR_RED);
            }
        } else {
            offset = nav_decr.getWidth() + 1;

            MapWidgetButton.fillBackground(this.view.getView(offset + 1, 1, getWidth() - 2 * offset - 2, getHeight() - 2), this.isEnabled(), focused);
            this.view.drawRectangle(offset, 0, getWidth() - 2 * offset, getHeight(), focused ? MapColorPalette.COLOR_RED : MapColorPalette.COLOR_BLACK);

            // Animate two bars moving from outside to the middle to reset the number
            if (holdEnterProgress > 0) {
                int barWidth = (((getWidth() - 2 * offset - 4) * holdEnterProgress) / 2) / this._holdEnterMaximum;
                this.view.fillRectangle(2 + offset, 2, barWidth, getHeight() - 4, MapColorPalette.COLOR_RED);
                this.view.fillRectangle(getWidth() - offset - barWidth - 2, 2, barWidth, getHeight() - 4, MapColorPalette.COLOR_RED);
            }
        }

        // Number information or text
        this.view.setAlignment(Alignment.MIDDLE);
        this.view.draw(MapFont.MINECRAFT, getWidth() / 2, (getHeight()-7) / 2, MapColorPalette.COLOR_WHITE, text);
    }

    @Override
    public void onActivate() {
        this.onResetValue();
        this.onValueChangeEnd();
        this.onResetClickSound();
    }

    // So that others can cancel the click sound (size box...)
    protected void onResetClickSound() {
        this.display.playSound(SoundEffect.CLICK);
    }

    /**
     * Called when the value is changed by the user. Is <b>not</b> called when
     * {@link #setInitialValue(double)} is used,
     */
    public void onValueChanged() {
    }

    /**
     * Fired when the player stops changing the value.
     * Does not fire when changing value directly, or when loading.
     */
    public void onValueChangeEnd() {
    }
}
