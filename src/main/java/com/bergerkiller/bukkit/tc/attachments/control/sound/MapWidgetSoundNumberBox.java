package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import org.bukkit.block.BlockFace;

import java.text.NumberFormat;

/**
 * Displays a number box, like the {@link com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox},
 * but themed for the sound appearance page. Also requires activating the element before
 * the value can be changed, instead of showing arrows when focused.<br>
 * <br>
 * Used for pitch and volume sliders
 */
public abstract class MapWidgetSoundNumberBox extends MapWidgetSoundElement implements SetValueTarget {
    private static final NumberFormat numberFormat = Util.createNumberFormat(1, 2);
    private final MapWidgetArrow leftArrow = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow rightArrow = new MapWidgetArrow(BlockFace.EAST);
    private double value = 0.0;
    private double defaultValue = 0.0;
    private double _incr = 0.01;
    private double _min = 0.0;
    private double _max = 5.0;
    private int _holdEnterProgress = 0;
    private int _holdEnterMaximum = 15;

    public abstract void onValueChanged(double newValue);

    public MapWidgetSoundNumberBox setInitialValue(double value) {
        this.value = value;
        return this;
    }

    public MapWidgetSoundNumberBox setDefaultValue(double value) {
        this.defaultValue = value;
        return this;
    }

    public MapWidgetSoundNumberBox setIncrement(double increment) {
        this._incr = increment;
        return this;
    }

    public MapWidgetSoundNumberBox setRange(double min, double max) {
        this._min = min;
        this._max = max;
        return this;
    }

    public double getValue() {
        return value;
    }

    public MapWidgetSoundNumberBox setValue(double value) {
        value = MathUtil.clamp(value, _min, _max);
        if (this.value != value) {
            this.value = value;
            onValueChanged(value);
            invalidate();
        }
        return this;
    }

    private void addValue(double incr, int repeat) {
        setValue(MapWidgetNumberBox.scaledIncrease(getValue(), incr, repeat));
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
    public void onActivate() {
        super.onActivate();

        // Don't want the reset button to become active right away
        _holdEnterProgress = _holdEnterMaximum + 1;

        // When activated show a left/right arrow that allows the player to change the value
        removeWidget(leftArrow);
        removeWidget(rightArrow);
        leftArrow.stopFocus();
        rightArrow.stopFocus();
        addWidget(leftArrow.setPosition(-leftArrow.getWidth() - 1, 1));
        addWidget(rightArrow.setPosition(getWidth() + 1, 1));
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();

        this._holdEnterProgress = 0;
        removeWidget(leftArrow);
        removeWidget(rightArrow);
    }

    @Override
    public void onDraw() {
        super.onDraw();

        // Animate two bars moving from outside to the middle to reset the number
        {
            int holdEnterProgress = this._holdEnterProgress;
            if (holdEnterProgress > this._holdEnterMaximum) {
                holdEnterProgress = 0; // Completed
            }

            if (holdEnterProgress > 0) {
                int barWidth = ((getWidth() * holdEnterProgress) / 2) / this._holdEnterMaximum;
                this.view.fillRectangle(2, 2, barWidth, getHeight() - 4, MapColorPalette.COLOR_RED);
                this.view.fillRectangle(getWidth() - barWidth - 2, 2, barWidth, getHeight() - 4, MapColorPalette.COLOR_RED);
            }
        }

        // Draw using x.xx format
        view.draw(MapFont.MINECRAFT, 3, 3, MapColorPalette.COLOR_WHITE, numberFormat.format(value));
    }

    @Override
    public void onKey(MapKeyEvent event) {
        if (isActivated() && event.getKey() == MapPlayerInput.Key.ENTER) {
            // Track amount of ticks ENTER was held
            if (this._holdEnterProgress <= this._holdEnterMaximum) {
                ++this._holdEnterProgress;
                if (this.isHoldEnterResetComplete()) {
                    setValue(defaultValue);
                }
                this.invalidate();
            }
        } else {
            super.onKey(event);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                addValue(-_incr, event.getRepeat());
                leftArrow.sendFocus();
                return;
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                addValue(_incr, event.getRepeat());
                rightArrow.sendFocus();
                return;
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                // Already handled in onKey. However, when just activating, by holding a little longer
                // this activates the hold button. handle that here.
                if (event.getRepeat() == 15 && _holdEnterProgress > _holdEnterMaximum) {
                    _holdEnterProgress = 1;
                }
                return;
            } else if (event.getKey() == MapPlayerInput.Key.UP || event.getKey() == MapPlayerInput.Key.DOWN) {
                // De-activate and let normal navigation handle this one
                this.focus();
            }
        }

        super.onKeyPressed(event);
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                leftArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                rightArrow.stopFocus();
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                this._holdEnterProgress = 0;
                this.invalidate();
            }
        }
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
            return true;
        } else {
            return false;
        }
    }
}
